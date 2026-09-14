# HU-20 · Auto-registro y aprovisionamiento de Organización (Tenant)

Resumen de los cambios de esta rama, para acompañar el pull request.

---

## Qué resuelve

Un visitante con perfil comercial puede registrar su organización sin intervención de nadie. El
endpoint es público y crea, **en una sola transacción**, la organización y su usuario administrador
con rol `ADMIN`. Con esto empieza a existir el concepto de *tenant* en la plataforma, que hasta
ahora no existía en el código.

Criterios de aceptación: `CONTEXTO-HU20-HU21.md` §5.

---

## Contrato del endpoint

### `POST /api/v1/organizations` — público, sin autenticación

```json
{
  "name": "Clínica Odontológica del Norte",
  "taxId": "900123456",
  "adminEmail": "admin@clinicanorte.co",
  "adminPassword": "Secreta123*",
  "adminFullName": "María Restrepo"
}
```

**201 Created**

```json
{
  "organizationId": "3f2a1c4e-...",
  "name": "Clínica Odontológica del Norte",
  "adminUserId": "9b71f0d2-..."
}
```

La respuesta no incluye la contraseña ni su hash.

| Código | `errorCode` | Cuándo |
|---|---|---|
| 201 | — | Registro exitoso |
| 400 | `VALIDATION_ERROR` | Falta un campo obligatorio o no cumple el formato. `details` trae campo → motivo |
| 409 | `ORGANIZATION_ALREADY_EXISTS` | La razón social o el NIT ya están registrados. `details` indica cuál de los dos |
| 404 | `ORGANIZATION_NOT_FOUND` | Solo en `/api/v1/auth/register`: el `organizationId` enviado no existe |

Todos los errores usan el envoltorio uniforme ya existente: `errorCode`, `message`, `details`,
`traceId`, `timestamp`.

---

## Migraciones (Flyway, nuevo en el proyecto)

Antes el esquema vivía en `docker/postgres/init/001-create-app-user.sql`, que Postgres solo ejecuta
al crear el volumen por primera vez y que no aplica en un entorno administrado como Render. **Ese
script se eliminó** junto con su montaje en `docker-compose.yml`: ahora el esquema lo gobierna
Flyway y es una sola fuente de verdad.

| Migración | Qué hace |
|---|---|
| `V1__baseline_app_user.sql` | Línea base: `app_user` tal como estaba antes de Flyway. En bases ya existentes no se reaplica, gracias a `spring.flyway.baseline-on-migrate=true` |
| `V2__add_login_security.sql` | Las columnas de bloqueo de cuenta de HU-03 (`failed_login_attempts`, `locked_until`) y el CHECK de roles con `PATIENT`/`PROFESSIONAL`/`ADMIN`. Es el antiguo `002-add-login-security.sql`, convertido en migración |
| `V3__create_organization.sql` | Tabla `organization` con `tax_id` único y un índice único sobre `lower(name)` para que la razón social sea única sin importar mayúsculas |
| `V4__add_tenant_to_app_user.sql` | Agrega `tenant_id` NOT NULL con FK a `organization` y cambia la unicidad de `email` a `(tenant_id, email)` |

> ⚠️ **`V4` elimina los usuarios preexistentes.** No hay forma de inferir a qué organización
> pertenecen, porque las organizaciones no existían. Es seguro porque ningún entorno tiene datos
> reales todavía. Quien tenga una base local con datos que quiera conservar, que avise antes de
> aplicarla.

Las pruebas corren sobre H2 y las migraciones son de PostgreSQL (`TIMESTAMPTZ`, índice funcional),
así que Flyway queda desactivado en `src/test/resources/application.properties`; ahí el esquema lo
sigue generando Hibernate.

---

## Cambio incompatible en HU01

`POST /api/v1/auth/register` **ahora exige el campo `organizationId`**. Un paciente no puede existir
fuera de una organización: es la consecuencia directa de volver la plataforma multi-tenant.

- Si el `organizationId` no existe → `404 ORGANIZATION_NOT_FOUND`.
- La unicidad del correo pasó de **global** a **por organización**. El mismo correo puede repetirse
  en dos organizaciones distintas, porque son negocios sin relación entre sí.
- La respuesta incluye ahora `organizationId`.

---

## Decisiones tomadas

- **Unicidad por razón social y por NIT.** El Gherkin de la historia rechaza por nombre repetido y el
  documento de contexto rechaza por NIT repetido; se implementan los dos, bajo un único
  `errorCode: ORGANIZATION_ALREADY_EXISTS`, y `details` dice cuál de los dos campos chocó. El nombre
  se normaliza (espacios colapsados) antes de comparar, y la comparación ignora mayúsculas.
- **Correo de bienvenida como puerto con adaptador de log.** `WelcomeNotificationPort` es la
  interfaz; `LoggingWelcomeNotificationAdapter` deja constancia en el log. No se añadió
  `spring-boot-starter-mail` ni configuración SMTP, así que no hay secretos que gestionar. Cuando
  exista proveedor de correo, se sustituye el adaptador sin tocar el caso de uso.
  *Pendiente al cambiar a SMTP real:* moverlo a un `@TransactionalEventListener(AFTER_COMMIT)` para
  no anunciar un registro que terminó en rollback. Con un log es inocuo.
- **`details` sigue siendo un objeto** campo → mensaje, y no un arreglo como lo dibuja el ADR-004.
  Cambiarlo rompería el contrato que ya expone HU01 sin ganancia; el criterio de aceptación solo
  exige que el campo exista.
- **La contraseña se guarda con BCrypt**, reutilizando el `PasswordEncoder` que ya estaba
  configurado. Nunca se registra en logs ni se devuelve en la respuesta.

---

## Estructura añadida

Módulo por dominio, siguiendo el ADR-001 y la convención de paquetes que ya usaba `auth`:

```
com.bookly.backendcf.organization
├── domain/model/          Organization, OrganizationStatus
├── application/           RegisterOrganizationService, WelcomeNotificationPort,
│                          OrganizationAlreadyExistsException, OrganizationNotFoundException
├── infrastructure/        persistence/OrganizationRepository,
│                          notification/LoggingWelcomeNotificationAdapter
└── presentation/          OrganizationController + DTOs
```

---

## Cómo probarlo

```bash
docker compose down -v && docker compose up --build
```

En el arranque debe aparecer `Successfully applied 4 migrations`. Que la aplicación levante ya es
evidencia de que el esquema cuadra con las entidades, porque `spring.jpa.hibernate.ddl-auto=validate`
aborta el arranque si no coinciden.

```bash
# 201 Created
curl -X POST localhost:8080/api/v1/organizations -H 'Content-Type: application/json' \
  -d '{"name":"Clínica Odontológica del Norte","taxId":"900123456",
       "adminEmail":"admin@clinicanorte.co","adminPassword":"Secreta123*",
       "adminFullName":"María Restrepo"}'

# 409 ORGANIZATION_ALREADY_EXISTS (razón social) — repetir el mismo comando
# 409 ORGANIZATION_ALREADY_EXISTS (NIT) — repetir cambiando solo el "name"
# 400 VALIDATION_ERROR — repetir omitiendo "taxId"
```

Verificar en la base que la contraseña no quedó en texto plano:

```sql
SELECT email, role, tenant_id, password_hash FROM app_user;
-- password_hash debe empezar por $2a$ y el rol del administrador ser ADMIN
```

Y que el log muestra la línea de bienvenida **sin** la contraseña.

---

## Integración con HU-03 (login con JWT)

HU-03 entró a `main` mientras esta rama estaba en curso y toca varios de los mismos archivos. Así
quedó resuelto:

- **Rol administrador: `ADMIN`, no `ORG_ADMIN`.** El documento de contexto pedía `ORG_ADMIN`, pero
  HU-03 ya definió `ADMIN` en el enum y en el CHECK de la tabla, y está mergeado. Se adopta `ADMIN`
  para no tocar trabajo ajeno ya integrado. Queda anotada la divergencia frente al contexto.
- **`UserAccount` combina ambas historias**: las columnas de bloqueo de HU-03 (`failedLoginAttempts`,
  `lockedUntil`) conviven con `tenantId` y el constructor pasa a recibir organización y rol.
- **`LoginServiceTest` tuvo que ajustarse** (una línea) porque construía `UserAccount` con el
  constructor de tres argumentos, que ya no existe.
- **`SecurityConfiguration` y `GlobalExceptionHandler`** suman las reglas y los handlers de las dos
  historias, sin quitar nada de HU-03.
- **Las migraciones se renumeraron** para que el bloqueo de cuenta de HU-03 entre como `V2`, antes de
  las de organización.

### Dos asuntos que necesitan decisión del equipo

**1. El JWT no lleva el identificador de organización.** `JwtTokenService.createToken` emite `sub`,
`email`, `role` y `exp`; `TokenClaims` es `(subject, role)`. El ADR-003 exige el claim de
organización y el `TenantFilter` de la HU-21 no tiene de dónde leerlo. Hay que añadirlo al token
antes de empezar la HU-21.

**2. `findByEmail` deja de ser unívoco.** El login busca la cuenta solo por correo, pero al pasar la
unicidad a `(tenant_id, email)` el mismo correo puede existir en dos organizaciones, y esa consulta
puede devolver más de un resultado. El login necesita saber a qué organización se entra —
subdominio, campo en el request, o lo que el equipo prefiera. Se dejó una nota en el repositorio;
**no se modificó el comportamiento del login**, porque es una historia ajena ya integrada.

---

## Pendientes

- **OpenAPI/Swagger.** springdoc 2.x apunta a Spring Boot 3 y el proyecto corre **Boot 4.0.8**. Queda
  bloqueado por la versión del framework hasta confirmar si hay una versión compatible.
- **Sin pipeline de CI.** `.github/` solo tiene `CODEOWNERS`, así que las comprobaciones automáticas
  no corren sobre este PR.
- **Ganchos que quedan listos para HU-21:** `app_user.tenant_id` ya existe, que es la columna sobre la
  que operarán las políticas de Row-Level Security; y `POST /api/v1/organizations` deberá añadirse a
  las rutas exentas del `TenantFilter`, porque es el único endpoint que corre sin tenant en contexto.
- **El usuario de base de datos es dueño de las tablas.** Para que RLS no sea decorativo, HU-21
  necesita un rol sin privilegios y sin `BYPASSRLS`.
