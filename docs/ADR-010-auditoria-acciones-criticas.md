# ADR-010: Arquitectura del módulo de auditoría de acciones críticas

- **Estado:** Propuesta
- **Fecha:** 2026-10-08
- **Responsables:** Arquitectura y Base de Datos
- **Ámbito:** Sprint 2, HU-19

## Contexto

HU-19 requiere una bitácora consultable por el administrador para mantener la trazabilidad de
acciones críticas ejecutadas en la plataforma. Como mínimo deben auditarse:

- creación de una reserva;
- cancelación de una reserva;
- cambio de rol de un usuario.

La bitácora debe indicar quién ejecutó la acción, qué acción se ejecutó y cuándo ocurrió. La consulta
debe estar restringida a usuarios con rol `ADMIN`.

El backend es un monolito modular Spring Boot con separación por módulos y capas. La solución debe
mantener esa organización, usar PostgreSQL como fuente de verdad y evitar que la auditoría quede
acoplada a un controlador o a una implementación concreta de reservas.

Existen dos dependencias del sprint:

1. HU-17 publica la matriz de roles y permisos. La consulta de auditoría utilizará la misma decisión
   de autorización y el rol `ADMIN` existente.
2. HU-08 y HU-09 publican los eventos o contratos de reserva que permiten identificar la creación y
   cancelación de una reserva. HU-19 no debe inventar ni duplicar el modelo de reservas.

## Decisión

### 1. Módulo y capas

Se crea el módulo `audit` con la misma estructura utilizada por los módulos actuales:

```text
audit/
├── application/
├── domain/model/
├── infrastructure/persistence/
└── presentation/
```

Sus responsabilidades serán:

- `application`: registrar acciones y consultar la bitácora;
- `domain/model`: representar una entrada de auditoría y los tipos de acción permitidos;
- `infrastructure`: persistir y consultar `audit_log` mediante PostgreSQL/JPA;
- `presentation`: exponer exclusivamente la consulta administrativa definida por HU-19.

El módulo `audit` no contendrá reglas de reservas ni de asignación de roles. Recibirá la información
de la acción mediante un contrato de aplicación y conservará únicamente la evidencia necesaria para
la trazabilidad.

### 2. Contrato de registro

El registro se realizará mediante un puerto de aplicación, conceptualmente equivalente a:

```text
AuditRecorder.record(AuditAction action)
```

La primera definición ejecutable del contrato queda en:

- `audit/application/AuditRecorder.java`;
- `audit/domain/model/AuditAction.java`;
- `audit/domain/model/AuditActionType.java`;
- `audit/domain/model/AuditResourceType.java`.

Estos tipos no dependen de Spring, JPA, PostgreSQL ni de los módulos consumidores. La persistencia y
la implementación concreta del puerto se agregarán en las siguientes tasks.

`AuditAction` deberá contener como mínimo:

- `actorUserId`: identificador del usuario autenticado que ejecutó la acción;
- `actionType`: tipo de acción (`BOOKING_CREATED`, `BOOKING_CANCELLED` o `ROLE_CHANGED`);
- `resourceType`: tipo de recurso afectado (`BOOKING` o `USER`);
- `resourceId`: identificador del recurso, cuando exista;
- `occurredAt`: fecha y hora con zona horaria;
- `sourceIp`: IP de origen, cuando esté disponible en el contexto de la solicitud;
- `metadata`: datos técnicos mínimos y no sensibles, cuando sean necesarios.

Los tipos de acción deben estar centralizados como un tipo controlado y no como cadenas arbitrarias
repetidas por los módulos consumidores.

### 3. Integración con operaciones críticas

Los casos de uso que ejecutan una operación crítica serán responsables de solicitar el registro al
puerto de auditoría después de completar correctamente la operación principal y dentro de la misma
transacción cuando la operación sea transaccional.

La integración seguirá estas reglas:

- HU-08 solicitará `BOOKING_CREATED` después de confirmar la creación de la reserva.
- HU-09 solicitará `BOOKING_CANCELLED` después de confirmar la cancelación.
- HU-17 solicitará `ROLE_CHANGED` después de persistir el cambio de rol.
- Una operación que termina con error no genera una entrada de acción exitosa.
- El módulo de auditoría no leerá directamente el JWT para determinar el actor; utilizará la identidad
  autenticada validada por el backend mediante un puerto o contexto de actor.
- La publicación de eventos de reserva de Miguel podrá adaptarse al puerto de auditoría, sin duplicar
  la lógica ni crear una segunda fuente de verdad.

Si una integración asíncrona se adopta posteriormente, deberá utilizar un evento transaccional u otra
garantía equivalente para no perder la auditoría después de confirmar la operación de negocio. Esa
decisión queda fuera de ARQ-01 y no se introduce un broker en este sprint.

### 4. Persistencia e inmutabilidad

La fuente de verdad será una tabla `audit_log`, creada mediante la migración `V40` asignada a Elena.
La tabla será append-only:

- la aplicación solo tendrá operación de inserción y consulta;
- no se expondrán endpoints de actualización o eliminación;
- el repositorio no ofrecerá operaciones de modificación o borrado;
- PostgreSQL rechazará `UPDATE` y `DELETE` sobre la tabla mediante una protección declarativa o
  procedural documentada en la migración, para que la inmutabilidad no dependa únicamente del código
  Java.

La eliminación de usuarios no debe eliminar sus registros de auditoría. La relación con `app_user`
no utilizará `ON DELETE CASCADE`; si el modelo de usuarios cambia, debe conservarse la evidencia del
actor.

La información de auditoría no debe contener contraseñas, tokens, secretos ni datos personales
innecesarios. El detalle adicional se almacenará solamente cuando tenga valor para la trazabilidad.

### 5. Consulta administrativa

HU-19 expondrá una consulta REST versionada bajo `/api/v1`, con rango de fechas como filtro mínimo.
La ruta exacta y sus DTO se definirán en la implementación y se reflejarán en `docs/openapi.yaml`.

La autorización seguirá el contrato uniforme existente:

- usuario no autenticado: `401 UNAUTHORIZED`;
- usuario autenticado sin rol `ADMIN`: `403 ACCESS_DENIED`;
- usuario con rol `ADMIN`: puede consultar.

La consulta debe tener orden determinista por fecha de ocurrencia y contar con límite o paginación para
evitar respuestas sin límite. Las fechas se validarán antes de consultar: la fecha inicial no puede ser
posterior a la fecha final.

## Modelo de componentes

```text
Reserva / Gestión de roles
          │
          │ AuditAction / evento adaptado
          ▼
   AuditRecorder (puerto)
          │
          ▼
 RegisterAuditActionService
          │
          ▼
 AuditLogRepository (puerto)
          │
          ▼
 PostgreSQL: audit_log

 ADMIN ──► AuditQueryController ──► AuditQueryService ──► AuditLogRepository
```

La dependencia apunta hacia el módulo de auditoría mediante contratos. `audit` no debe depender de
`booking` ni de `auth` mediante clases concretas de infraestructura.

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| Escribir únicamente en el log de la aplicación | No garantiza persistencia, consulta por fechas ni trazabilidad transaccional. |
| Crear una tabla diferente para reservas, cancelaciones y roles | Duplica el modelo de auditoría y dificulta la consulta unificada exigida por HU-19. |
| Registrar desde cada controlador REST | Acopla auditoría al transporte y puede omitir acciones ejecutadas por otros flujos de aplicación. |
| Confiar únicamente en el rol recibido dentro del JWT | El actor debe provenir del contexto autenticado validado por el backend; no se debe confiar en datos no verificados. |
| Permitir editar o borrar la bitácora | Contradice la inmutabilidad y debilita la trazabilidad ante incidentes. |
| Incorporar un broker de mensajes en este sprint | No existe una necesidad operativa que justifique esa complejidad; se utilizarán contratos y transacciones del monolito modular. |

## Consecuencias

### Positivas

- La auditoría queda separada de las reglas de reservas y autenticación.
- Las acciones críticas comparten un formato consultable y trazable.
- PostgreSQL conserva la autoridad sobre la persistencia y la inmutabilidad.
- La consulta reutiliza el esquema de autorización existente (`401`/`403`).
- La solución puede recibir eventos de reservas sin duplicar el modelo de `booking`.

### Costos y riesgos

- HU-08, HU-09 y HU-17 deben integrar explícitamente el contrato de auditoría.
- Una operación asíncrona futura requerirá garantía transaccional para no perder registros.
- Las pruebas automáticas no ejecutan Flyway; la migración debe validarse manualmente contra PostgreSQL.
- La protección de `UPDATE` y `DELETE` debe probarse contra PostgreSQL, no solamente mediante mocks.

## Criterios de aceptación de ARQ-01

- Existe este ADR en `docs/ADR-010-auditoria-acciones-criticas.md`.
- El módulo `audit` está definido con las capas del proyecto.
- El contrato de registro incluye actor, acción, recurso y fecha de ocurrencia.
- Se documentan las dependencias con HU-17, HU-08 y HU-09.
- Se define que una acción fallida no genera un registro exitoso.
- Se define la consulta exclusiva para `ADMIN` con respuestas `401` y `403`.
- Se define una estrategia append-only y la protección de la tabla contra actualización y eliminación.
- Se establece que no se almacenarán contraseñas, tokens, secretos ni datos personales innecesarios.
- La decisión no introduce un broker ni modifica todavía el código de los módulos consumidores.

## Implementación de ARQ-02

El contrato se implementa como un puerto de aplicación y un comando de dominio inmutable. El comando
valida que existan el actor, la acción, el recurso y la fecha; permite que el identificador del recurso
y la IP sean opcionales según el contexto; y copia el mapa de metadatos para impedir que un consumidor
modifique la evidencia después de construir la acción.

Las pruebas del contrato se encuentran en
`src/test/java/com/bookly/backendcf/audit/domain/model/AuditActionTest.java` y cubren la inmutabilidad
de los metadatos, el mapa vacío por defecto y los campos obligatorios.

## Trazabilidad

- Requisito funcional: HU-19, `documentos_cursos/Historias_de_usuario_azure.md`.
- Plan del sprint: `docs/PLAN-SPRINT-2.md`.
- Autorización: HU-17 y `SecurityConfiguration`.
- Integridad y migraciones: ADR-006 y sección de Flyway del plan del Sprint 2.
- Próxima migración: `V40__create_audit_log.sql`.
