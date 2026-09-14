# HU-20 · Aprovisionamiento inicial de la organización

Resumen de los cambios de esta rama, para acompañar el pull request.

---

## Qué resuelve

La plataforma atiende a **una sola organización**. Este endpoint la aprovisiona por primera y única
vez: crea la organización y su usuario administrador **en una sola transacción**, y a partir de ahí
rechaza cualquier intento posterior.

> **Nota de alcance:** la historia se planteó originalmente como auto-registro multi-tenant. Al
> decidirse que la plataforma no será multi-tenant, desapareció `tenant_id`, la unicidad de correo
> volvió a ser global y `/api/v1/auth/register` recuperó su contrato original. La HU-21 (aislamiento
> inter-tenant) queda cancelada.

---

## Contrato del endpoint

### `POST /api/v1/organizations` — público, de un solo uso

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
  "organizationId": "53573afe-...",
  "name": "Clínica Odontológica del Norte",
  "adminUserId": "acd6e775-..."
}
```

La respuesta no incluye la contraseña ni su hash.

| Código | `errorCode` | Cuándo |
|---|---|---|
| 201 | — | Aprovisionamiento exitoso |
| 400 | `VALIDATION_ERROR` | Falta un campo obligatorio o no cumple el formato. `details` trae campo → motivo |
| 409 | `ORGANIZATION_ALREADY_EXISTS` | Ya hay una organización registrada: el aprovisionamiento es irrepetible |

Todos los errores usan el envoltorio uniforme ya existente: `errorCode`, `message`, `details`,
`traceId`, `timestamp`.

> ⚠️ **El endpoint queda público y sin autenticar**, porque es la puerta de entrada inicial. Una vez
> aprovisionada la organización responde 409 a todo, pero conviene cerrarlo por red o configuración
> en el despliegue.

---

## Migraciones (Flyway, nuevo en el proyecto)

Antes el esquema vivía en `docker/postgres/init/`, que Postgres solo ejecuta al crear el volumen por
primera vez y que no aplica en un entorno administrado como Render. **Esos scripts se eliminaron**
junto con su montaje en `docker-compose.yml`: ahora el esquema lo gobierna Flyway.

> **Trampa de Spring Boot 4:** añadir `flyway-core` **no basta**. Boot 4 sacó las autoconfiguraciones
> a módulos propios, así que `FlywayAutoConfiguration` vive en
> `org.springframework.boot:spring-boot-flyway`. Sin esa dependencia Flyway queda en el classpath
> pero nunca se ejecuta, y el arranque falla con `Schema validation: missing table [app_user]` sin
> mencionar a Flyway por ningún lado.

| Migración | Qué hace |
|---|---|
| `V1__baseline_app_user.sql` | Línea base: `app_user` tal como estaba antes de Flyway. En bases ya existentes no se reaplica, gracias a `spring.flyway.baseline-on-migrate=true` |
| `V2__add_login_security.sql` | Columnas de bloqueo de cuenta de HU-03 y CHECK de roles. Es el antiguo `002-add-login-security.sql`, convertido en migración |
| `V3__create_organization.sql` | Tabla `organization`, con `tax_id` único, índice único sobre `lower(name)` y un índice único sobre una expresión constante que permite **exactamente una fila** |

Ninguna migración borra datos: HU01 y HU-03 quedan intactas.

Las pruebas corren sobre H2 y las migraciones son de PostgreSQL, así que Flyway queda desactivado en
`src/test/resources/application.properties`; ahí el esquema lo sigue generando Hibernate.

---

## Decisiones tomadas

- **La unicidad la sostiene la base, no solo el servicio.** El servicio comprueba
  `organizationRepository.count() > 0`, pero entre esa lectura y la escritura caben dos peticiones
  simultáneas. El índice `uk_organization_singleton ON organization ((true))` hace que la segunda
  falle en la base pase lo que pase; el handler de `DataIntegrityViolationException` ya devuelve 409.
- **Se conservan los índices únicos de razón social y NIT** aunque con una sola fila sean
  redundantes: documentan la intención y no cuestan nada.
- **Correo de bienvenida como puerto con adaptador de log.** `WelcomeNotificationPort` es la
  interfaz; `LoggingWelcomeNotificationAdapter` deja constancia en el log. Sin
  `spring-boot-starter-mail` ni SMTP, así que no hay secretos que gestionar.
  *Pendiente al cambiar a SMTP real:* moverlo a un `@TransactionalEventListener(AFTER_COMMIT)` para
  no anunciar un registro que terminó en rollback. Con un log es inocuo.
- **Rol del administrador: `ADMIN`.** Es el valor que HU-03 dejó en el enum y en el CHECK de la
  tabla. El documento de contexto pedía `ORG_ADMIN`; se adoptó `ADMIN` para no romper trabajo ya
  integrado.
- **`details` sigue siendo un objeto** campo → mensaje, y no un arreglo como lo dibuja el ADR-004.
  Cambiarlo rompería el contrato que ya exponen HU01 y HU-03 sin ganancia.
- **La contraseña se guarda con BCrypt**, reutilizando el `PasswordEncoder` ya configurado. Nunca se
  registra en logs ni se devuelve en la respuesta.

---

## Estructura añadida

```
com.bookly.backendcf.organization
├── domain/model/          Organization, OrganizationStatus
├── application/           RegisterOrganizationService, WelcomeNotificationPort,
│                          OrganizationAlreadyExistsException
├── infrastructure/        persistence/OrganizationRepository,
│                          notification/LoggingWelcomeNotificationAdapter
└── presentation/          OrganizationController + DTOs
```

Fuera de este módulo solo se tocaron tres archivos: `SecurityConfiguration` (la ruta pública),
`GlobalExceptionHandler` (un handler nuevo) y `pom.xml` (Flyway). HU01 y HU-03 quedan sin cambios.

---

## Verificación ejecutada

Contra un PostgreSQL 16.10 real con colación ICU, no contra H2:

| Comprobación | Resultado |
|---|---|
| `mvnw clean test` | BUILD SUCCESS, 4 pruebas, 0 fallos |
| Flyway V1→V3 desde base vacía | `Successfully applied 3 migrations, now at version v3` |
| Arranque con `ddl-auto=validate` | Correcto: el esquema cuadra con las entidades |
| Primer aprovisionamiento | `201` con `organizationId`, `name` y `adminUserId` |
| Segundo aprovisionamiento, datos distintos | `409 ORGANIZATION_ALREADY_EXISTS` |
| Petición sin `taxId` | `400 VALIDATION_ERROR` con `errorCode`, `message`, `details` y `traceId` |
| `/auth/register` sin `organizationId` | `201`: HU01 conserva su contrato original |
| Contraseña en base | Hash BCrypt `$2a$10$`, nunca en claro, ausente de la respuesta |

Reproducirlo:

```bash
docker compose down -v && docker compose up --build
curl -X POST localhost:8080/api/v1/organizations -H 'Content-Type: application/json' \
  -d '{"name":"Clínica Odontológica del Norte","taxId":"900123456",
       "adminEmail":"admin@clinicanorte.co","adminPassword":"Secreta123*",
       "adminFullName":"María Restrepo"}'
# repetir con datos distintos -> 409
# repetir omitiendo "taxId" -> 400
```

---

## Pendientes

- **OpenAPI/Swagger.** springdoc 2.x apunta a Spring Boot 3 y el proyecto corre **Boot 4.0.8**. Queda
  bloqueado por la versión del framework hasta confirmar si hay una versión compatible.
- **Sin pipeline de CI.** `.github/` solo tiene `CODEOWNERS`, así que las comprobaciones automáticas
  no corren sobre este PR.
- **Cerrar el endpoint tras el aprovisionamiento**, por red o configuración.
- **Colación de la base de despliegue.** El índice sobre `lower(name)` solo pliega ASCII si el
  clúster se creó con `LC_CTYPE=C`; debe usarse una colación UTF-8 o ICU.
