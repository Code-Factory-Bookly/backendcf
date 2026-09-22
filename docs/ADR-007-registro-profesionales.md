# ADR-007: Registro de profesionales por el administrador

- **Estado:** Propuesta
- **Fecha:** 2026-09-19
- **Responsables:** Arquitectura y Base de Datos
- **Ámbito:** Sprint 1, Caso 14

## Contexto

HU-22 pide que el administrador registre a los profesionales o especialistas que prestan servicios, para
poder configurar después sus agendas. El profesional debe poder iniciar sesión, tener el rol `PROFESSIONAL`
y, en HUs posteriores, acceder únicamente a su propia agenda.

El rol `PROFESSIONAL` ya existe en `UserRole` y en la restricción `ck_app_user_role` (migración V4), y el
login de HU-03 ya emite tokens con el rol. Falta decidir cómo se representa el profesional, quién define su
contraseña inicial, cómo se protege el alta y qué garantiza la base de datos.

## Decisión

1. **Representación.** Un profesional es una cuenta de `app_user` con rol `PROFESSIONAL` más un perfil en la
   tabla `profesionales`. La relación es 1 a 1 y comparte la clave primaria: `profesionales.id` es a la vez
   PK y parte de la FK hacia `app_user`. Así el identificador del profesional es el mismo UUID que el token
   JWT lleva en `sub`, el valor que `OwnershipGuard` compara para proteger recursos propios.
2. **Especialidad.** Es un texto obligatorio (`especialidad`, máximo 100 caracteres, no vacío) del perfil.
   No se crea una tabla de especialidades, en coherencia con el [ADR-005](ADR-005-alcance-HU-02.md).
3. **Alta.** `POST /api/v1/profesionales` recibe `email`, `password`, `fullName` y `specialty`, crea cuenta
   y perfil en una sola transacción y responde `201`. La contraseña inicial la define el administrador y
   cumple la misma política que el registro de clientes.
4. **Autorización.** El endpoint es exclusivo de `ADMIN`. Se agrega una regla explícita
   `hasRole("ADMIN")` sobre `/api/v1/profesionales/**` en `SecurityConfiguration`, antes de
   `anyRequest().authenticated()`; sin ella, cualquier usuario autenticado (incluido un cliente) podría
   crear profesionales. Sin token responde `401 UNAUTHORIZED` y con un rol distinto de `ADMIN`,
   `403 ACCESS_DENIED`.
5. **Alcance.** Esta HU solo registra al profesional y le asigna el rol. La restricción "permisos limitados
   a su propia agenda" se aplicará en la HU de agenda con `OwnershipGuard`, usando el id del profesional
   como dueño del recurso. La asignación de profesionales a servicios queda fuera: ninguna historia define
   ese escenario y HU-07 lo presupone.
6. **Integridad.** PostgreSQL conserva la autoridad final, como en el [ADR-006](ADR-006-concurrencia-integridad.md):
    - La unicidad del correo la sostiene `uk_app_user_email`.
    - El perfil exige una cuenta con rol `PROFESSIONAL`. `profesionales.rol` vale siempre `PROFESSIONAL`
      (`DEFAULT` y `CHECK`) y una FK compuesta `(id, rol)` apunta a `app_user (id, role)`, para lo cual la
      migración agrega `uk_app_user_id_role`. Una FK simple solo comprobaría que la cuenta exista y
      aceptaría el perfil de un cliente o de un administrador.
    - Una cuenta tiene como máximo un perfil (PK). La FK no tiene `ON DELETE CASCADE`: una cuenta con perfil
      se deshabilita (`enabled = FALSE`) y no se elimina.
    - El correo duplicado se traduce a `409 EMAIL_ALREADY_REGISTERED`.

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| Agregar `especialidad` como columna de `app_user` | Mezcla atributos de un solo rol en la tabla de cuentas y deja la columna vacía para clientes y administradores |
| PK propia en `profesionales` con `usuario_id` único | Introduce dos identificadores por profesional y obliga a traducir entre el `sub` del token y el id del perfil |
| Tabla independiente de especialidades | Sin reglas de negocio que la justifiquen en este sprint (ADR-005) |
| FK simple hacia `app_user` y validar el rol solo en la aplicación | No protege ante datos insertados fuera de la aplicación: la base aceptaría el perfil de una cuenta que no es `PROFESSIONAL` |
| Trigger que verifique el rol al insertar | Lógica procedural fuera de las restricciones declarativas; la FK compuesta la resuelve el motor sin código adicional |
| Contraseña temporal o invitación por correo | No existe proveedor de correo (`WelcomeNotificationPort` solo escribe en el log) ni flujo de cambio de contraseña |

## Consecuencias

- El modelo agrega una tabla, una relación FK compuesta y una restricción única auxiliar sobre `app_user`;
  `servicios` y `platform` no cambian.
- `profesionales.rol` es una columna redundante y deliberada. La aplicación no la mapea: la base la completa
  con `PROFESSIONAL`.
- Mientras una cuenta tenga perfil no se puede cambiar su rol. Cuando exista el flujo de cambio de roles
  (HU-17), quitar a alguien de `PROFESSIONAL` exigirá eliminar antes su perfil.
- La base no exige que toda cuenta `PROFESSIONAL` tenga perfil; la consulta 12 de `README_BASE_DE_DATOS.md`
  permite detectarlo.
- El login y el JWT no requieren cambios: el profesional inicia sesión como cualquier cuenta.
- El administrador conoce la contraseña inicial del profesional. Mientras no exista un flujo de invitación
  o de cambio de contraseña, debe entregarse por un canal seguro. Es una deuda de seguridad registrada
  para una HU posterior.

## Trazabilidad

- Requisitos funcionales: `Historias_de_usuario_azure.md`, sección HU-22.
- Contrato: `docs/openapi.yaml`, ruta `/profesionales`.
- Persistencia: `V6__create_professionals.sql`, tabla `profesionales`.
- Evidencia de integridad: `docs/evidencia/HU-22-integridad.sql`.