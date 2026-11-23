# EAV-10: Caso 14 - Plataforma de Reservas de Servicios

Backend de la plataforma de reservas de servicios del caso 14 de CodeF@ctory. Clientes reservan
servicios con un profesional en una franja libre; los profesionales definen su horario y gestionan
sus turnos; los administradores configuran el catálogo, el equipo, los roles y auditan la actividad
crítica de la plataforma.

## Estado del proyecto

Sprint 1, Sprint 2 y la implementación funcional de la Entrega 3 están integrados en `main`. El detalle
del estado de Azure DevOps y de la validación de Calidad se mantiene en [README_QA.md](README_QA.md).

| HU | Historia |
|---|---|
| HU-01 | Registro de cliente |
| HU-02 | Catálogo de servicios |
| HU-03 | Inicio de sesión seguro |
| HU-04 | Horario de atención semanal del profesional |
| HU-06 | Duración estándar de servicios |
| HU-07 | Consulta de disponibilidad de agenda |
| HU-08 | Creación y agendamiento de reserva |
| HU-09 | Cancelación autónoma de reservas |
| HU-10 | Confirmación de reserva por correo electrónico |
| HU-11 | Registro de recursos físicos |
| HU-12 | Validación de capacidad de recursos |
| HU-13 | Historial de reservas del cliente |
| HU-14 | Agenda profesional |
| HU-15 | Reporte de ocupación |
| HU-16 | Reporte general de uso |
| HU-17 | Asignación de roles y permisos (RBAC) |
| HU-18 | Autenticación multifactor (MFA) para accesos administrativos |
| HU-19 | Registro de auditoría de acciones críticas |
| HU-20 | Configuración inicial de la plataforma |
| HU-21 | Control de acceso a recursos ajenos (`OwnershipGuard`) |
| HU-22 | Creación de especialistas y profesionales |

El estado detallado de cobertura de pruebas, Quality Gate de SonarCloud y riesgos por HU está en
[README_QA.md](README_QA.md). El plan de reparto y cronograma del Sprint 2 está en
[docs/PLAN-SPRINT-2.md](docs/PLAN-SPRINT-2.md).

Además:

- API REST versionada bajo `/api/v1`, documentada en [docs/openapi.yaml](docs/openapi.yaml).
- Persistencia en PostgreSQL con JPA/Hibernate; esquema administrado por Flyway (`V1` a `V70`).
- Roles unificados: `CUSTOMER`, `PROFESSIONAL` y `ADMIN`, con cambio de rol auditado (HU-17).
- Contraseñas almacenadas mediante BCrypt; tokens Bearer firmados con `io.jsonwebtoken` (JJWT).
- Segundo factor (TOTP) obligatorio para `ADMIN`, con códigos de recuperación (HU-18).
- Bloqueo temporal después de 5 intentos fallidos durante 15 minutos.
- Reservas atómicas: la base garantiza que una franja no se reserve dos veces (`ADR-006`).
- Correo de confirmación/cancelación de reserva, procesado por eventos después del commit (HU-10).
- Bitácora de auditoría inmutable para acciones críticas, reportes e historial (HU-19, HU-16 y HU-13).
- Manejo uniforme de errores con `errorCode`, `message`, `details`, `traceId` y `timestamp`.
- Ejecución local y contenerizada con Docker Compose; desplegado en Render.

## Tecnologías

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Backend | Spring Boot 4.0.8 |
| API | Spring MVC REST |
| Persistencia | Spring Data JPA / Hibernate, Flyway |
| Base de datos | PostgreSQL 16 |
| Seguridad | Spring Security, BCrypt, JJWT, `dev.samstevens.totp` (MFA) |
| Correo | Spring Mail |
| Pruebas | JUnit 5, Mockito |
| Contenedores | Docker Compose |
| Construcción | Maven Wrapper |
| Calidad | SonarCloud (Quality Gate en cada PR/push) |

## Requisitos previos

Para ejecutar localmente se necesita Java 17 y Maven Wrapper (incluido en el repositorio). Para la ejecución recomendada se necesita Docker Desktop con Docker Compose.

## Ejecución recomendada con Docker Compose

### 1. Clonar y entrar al proyecto

```bash
git clone <URL_DEL_REPOSITORIO>
cd backendcf
```

### 2. Configurar variables de entorno

Crear un archivo `.env` a partir de `.env.example` y cambiar las credenciales de desarrollo:

```bash
cp .env.example .env
```

El archivo `.env` no debe subirse al repositorio.

### 3. Levantar la base de datos y la aplicación

```bash
docker compose up -d --build
```

La API quedará disponible en `http://localhost:8080` y PostgreSQL en `localhost:5432`.

### 4. Verificar el estado

```bash
docker compose ps
docker compose logs -f app
```

La aplicación inicia después de que el healthcheck de PostgreSQL esté listo.

### 5. Detener la aplicación

```bash
docker compose down
```

Este comando conserva el volumen `postgres_data`. Para eliminar también los datos de desarrollo, usar `docker compose down -v` únicamente cuando sea aceptable perderlos.

### 6. Aplicar cambios posteriores

Después de modificar código Java o el `Dockerfile`:

```bash
docker compose up -d --build app
```

El esquema lo gestiona **Flyway**, que aplica las migraciones de `src/main/resources/db/migration`
al arrancar la aplicación. No hay que ejecutar SQL a mano ni recrear el volumen: sobre una base ya
existente, Flyway la marca en la línea base y aplica solo lo pendiente. Como el equipo trabaja en
paralelo con rangos de versión por persona (ver [docs/PLAN-SPRINT-2.md](docs/PLAN-SPRINT-2.md),
sección 5), `spring.flyway.out-of-order=true` permite que una migración con número menor se aplique
después de una con número mayor ya aplicada, sin romper el arranque.

Para partir de cero:

```bash
docker compose down -v && docker compose up --build
```

## Ejecución local sin Docker

Se requiere una instancia PostgreSQL disponible y las variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` configuradas. Luego:

```bash
./mvnw test
./mvnw spring-boot:run
```

En Windows se puede usar `mvnw.cmd`.

## Configuración de seguridad

Las siguientes propiedades pueden definirse como variables de entorno:

| Variable | Valor por defecto | Propósito |
|---|---:|---|
| `POSTGRES_DB` | `backendcf` | Nombre de la base en Compose |
| `POSTGRES_USER` | `backendcf` | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | valor de desarrollo | Contraseña de PostgreSQL |
| `JWT_SECRET` | obligatorio | Clave de firma persistente de al menos 64 bytes; nunca debe cambiar entre reinicios |
| `JWT_EXPIRATION_SECONDS` | `3600` | Duración del token |
| `LOGIN_MAX_ATTEMPTS` | `5` | Intentos antes del bloqueo |
| `LOGIN_LOCK_MINUTES` | `15` | Duración del bloqueo |
| `MFA_ENCRYPTION_KEY` | obligatorio | Clave AES-256 (32 bytes en base64) para cifrar el secreto TOTP de cada cuenta `ADMIN` con MFA activo |
| `BOOKING_CANCELLATION_MIN_ADVANCE_MINUTES` | `120` | Minutos mínimos de anticipación para que un cliente cancele sin penalización (HU-09) |
| `FRONTEND_URL` | `http://127.0.0.1:5173,http://localhost:5173` | Orígenes permitidos por CORS |

`JWT_SECRET` y `MFA_ENCRYPTION_KEY` deben definirse en `.env` a partir de `.env.example` y conservarse entre reinicios (y entre despliegues). Ambas se generan con:

```bash
openssl rand -base64 48   # JWT_SECRET: >= 64 caracteres y al menos 10 distintos (se rechazan secretos repetitivos)
openssl rand -base64 32   # MFA_ENCRYPTION_KEY: debe decodificar a exactamente 32 bytes (AES-256)
```

Si `MFA_ENCRYPTION_KEY` cambia, los secretos TOTP ya cifrados en la base quedan indescifrables y todas las cuentas ADMIN con el segundo factor activo pierden acceso (deben reenrolar). En producción, esta clave, las credenciales de base de datos y demás secretos deben gestionarse fuera del código y del repositorio.

## API

El contrato completo, con todos los payloads, respuestas y códigos de error, está en
[docs/openapi.yaml](docs/openapi.yaml). Esta sección es un mapa rápido de qué existe y quién puede usarlo.

### Autenticación

```http
POST /api/v1/auth/register        # cliente, público
POST /api/v1/auth/login           # cualquier rol; ADMIN recibe un reto de MFA si no se omite
PUT  /api/v1/auth/password        # cambiar la propia contraseña
```

El registro exitoso responde `201`. El login exitoso responde `200` con `accessToken`, el tipo `Bearer`, la expiración y el resumen del usuario. Para consumir endpoints protegidos:

```http
Authorization: Bearer <accessToken>
```

### Segundo factor para administradores (HU-18)

```http
POST /api/v1/auth/mfa/enroll           # genera el secreto y el QR
POST /api/v1/auth/mfa/enroll/confirm   # confirma el primer código y activa el MFA
POST /api/v1/auth/mfa/verify           # valida el código en logins siguientes
POST /api/v1/auth/mfa/skip             # omite el enrolamiento por esta vez
GET  /api/v1/mfa/recovery-codes/estado
POST /api/v1/mfa/recovery-codes/regenerar
POST /api/v1/mfa/desactivar
```

El secreto TOTP se guarda cifrado (`MFA_ENCRYPTION_KEY`). `trustDevice` evita pedir el código de nuevo en el mismo navegador durante 30 días.

### Catálogo de servicios (HU-02, HU-06)

```http
GET    /api/v1/servicios                    # público
GET    /api/v1/servicios/{id}                # público
GET    /api/v1/servicios?categoria=<cat>     # público
POST   /api/v1/servicios                     # ADMIN
PUT    /api/v1/servicios/{id}                 # ADMIN
DELETE /api/v1/servicios/{id}                 # ADMIN
```

El nombre es único sin distinguir mayúsculas, la duración está entre 1 y 480 minutos (HU-06) y el precio debe ser mayor que cero. Alcance aprobado en [ADR-005](docs/ADR-005-alcance-HU-02.md).

### Profesionales (HU-22)

```http
GET  /api/v1/profesionales     # ADMIN
POST /api/v1/profesionales     # ADMIN
```

### Recursos físicos (HU-11, HU-12)

```http
GET    /api/v1/recursos                                # ADMIN
POST   /api/v1/recursos                                # ADMIN
PUT    /api/v1/recursos/{id}                           # ADMIN
GET    /api/v1/recursos/asignaciones/servicios/{serviceId} # ADMIN
POST   /api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId} # ADMIN
DELETE /api/v1/recursos/asignaciones/servicios/{serviceId}/recursos/{resourceId} # ADMIN
```

Los recursos tienen una cantidad total positiva y un nombre único sin distinguir mayúsculas. Al crear una
reserva, `ResourceCapacityValidator` verifica el uso de los recursos asociados al servicio dentro de la
transacción.

### Bloqueos de agenda (HU-05)

```http
GET    /api/v1/horarios/{professionalId}/bloqueos             # PROFESSIONAL o ADMIN
POST   /api/v1/horarios/{professionalId}/bloqueos             # PROFESSIONAL o ADMIN
DELETE /api/v1/horarios/{professionalId}/bloqueos/{blockId}   # PROFESSIONAL o ADMIN
```

Los bloqueos usan rangos `[inicio, fin)`, no pueden invertirse ni solaparse para un mismo profesional y
descuentan capacidad en el reporte de ocupación.

### Horario semanal del profesional (HU-04)

```http
GET /api/v1/horarios/{professionalId}   # el propio profesional o ADMIN
PUT /api/v1/horarios/{professionalId}   # el propio profesional o ADMIN
```

Reemplaza todas las franjas en una sola operación. Rechaza franjas con hora de fin anterior o igual a la de inicio y franjas superpuestas el mismo día (`409 SCHEDULE_CONFLICT`, con el detalle de cada conflicto).

### Disponibilidad (HU-07)

```http
GET /api/v1/disponibilidad?profesionalId=&servicioId=&desde=&hasta=     # CUSTOMER
```

`desde`/`hasta` son fechas (`yyyy-MM-dd`), rango máximo de 31 días. Cada bloque dura lo mismo que el servicio, se genera dentro del horario semanal del profesional (HU-04) y excluye lo que ya pasó o se solapa con una reserva `CONFIRMADA`, `RESCHEDULED` o `COMPLETED`. Si no hay bloques libres, responde `200` con `available=false` y un mensaje sugiriendo ampliar el filtro.

### Reporte de ocupación (HU-15)

```http
GET /api/v1/reportes/ocupacion?periodo=MES                 # ADMIN
GET /api/v1/reportes/ocupacion?desde=2026-11-01&hasta=2026-11-30
```

`periodo` es `SEMANA` (últimos 7 días) o `MES` (últimos 30 días, valor por defecto) y no distingue
mayúsculas. También se puede enviar `desde` y `hasta` juntos para un rango inclusivo de máximo 366 días.
La capacidad une franjas solapadas y descuenta bloqueos; cuentan las reservas `CONFIRMADA`, `RESCHEDULED`
y `COMPLETED`. El porcentaje no se limita a 100 y es `null` cuando hay reservas y capacidad cero.

### Reporte general de uso (HU-16)

```http
GET /api/v1/reportes/general?periodo=MES     # ADMIN con MFA verificada
```

Devuelve reservas totales, cancelaciones promedio, ingresos usando `price_at_booking` y uso de recursos.
La consulta registra `REPORT_VIEWED` en la auditoría y la sesión del reporte expira después de 15 minutos
sin actividad.

### Reservas (HU-08, HU-09)

```http
POST   /api/v1/reservas                                # CUSTOMER
PATCH  /api/v1/reservas/{id}/cancelacion                 # CUSTOMER
GET    /api/v1/reservas/cliente/{customerId}             # el propio cliente o ADMIN
GET    /api/v1/reservas/cliente/historial                # CUSTOMER, usa el id del token
GET    /api/v1/reservas/profesional/agenda               # HU-14: PROFESSIONAL (la propia) o ADMIN (?professionalId=)
GET    /api/v1/reservas/profesional/{professionalId}     # OBSOLETA (usar /agenda): el propio profesional o ADMIN
```

Dos clientes no pueden quedarse con la misma franja: la base lo garantiza con exclusión de rangos (`ADR-006`), y el perdedor de la carrera recibe `409`. Cancelar exige una anticipación mínima configurable (`BOOKING_CANCELLATION_MIN_ADVANCE_MINUTES`). El historial propio admite filtros, orden y paginación de máximo 100; la lectura administrativa del historial de otro cliente queda auditada.

### Roles (HU-17)

```http
GET  /api/v1/usuarios/buscar?q=                   # ADMIN, busca por correo o nombre
PUT  /api/v1/usuarios/{userId}/rol                 # ADMIN
POST /api/v1/usuarios/{userId}/clave/restablecer   # ADMIN
```

`PUT .../rol` cambia el rol entre `CUSTOMER`, `PROFESSIONAL` y `ADMIN`. Asignar `PROFESSIONAL` a una cuenta sin perfil exige especialidad y crea el perfil en la misma transacción. Una cuenta con perfil profesional no puede cambiar de rol (`409 ROLE_CHANGE_CONFLICT`).

No hay recuperación de contraseña por correo todavía (depende de notificaciones salientes al cliente, HU-10 solo cubre reservas). Mientras tanto, un `ADMIN` puede restablecer la contraseña de un usuario a una temporal, que debe entregarle fuera de la plataforma.

### Auditoría (HU-19)

```http
GET /api/v1/auditoria?from=&to=&page=&size=     # ADMIN
```

`from`/`to` son fecha-hora ISO-8601; paginación determinista, orden descendente por fecha. Bitácora inmutable (sin `UPDATE` ni `DELETE`) de acciones críticas: crear reserva, cancelar reserva, cambiar rol.

### Configuración inicial (HU-20)

```http
POST /api/v1/platform/setup
```

Público, de un solo uso: aprovisiona la plataforma y su administrador. Un segundo intento responde `409 PLATFORM_ALREADY_CONFIGURED`.

### Notificaciones por correo (HU-10)

No agrega endpoints REST: al confirmar o cancelar una reserva, un listener procesa el evento después del commit y envía el correo. Si la transacción de la reserva se revierte, no se envía nada. Diseño completo en [docs/HU-10-notificaciones.md](docs/HU-10-notificaciones.md) y [ADR-011](docs/ADR-011-notificaciones-por-eventos.md).

### Errores

Todos los errores comparten el mismo envoltorio:

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "El request contiene datos inválidos",
  "details": { "campo": "motivo" },
  "traceId": "...",
  "timestamp": "..."
}
```

| Código | Cuándo |
|---:|---|
| `400` | Payload inválido (`VALIDATION_ERROR`) |
| `401` | Sin autenticación válida (`UNAUTHORIZED`) o credenciales incorrectas (`INVALID_CREDENTIALS`) |
| `403` | Autenticado sin permisos (`ACCESS_DENIED`) |
| `404` | Recurso inexistente |
| `409` | Conflicto de estado (duplicado, franja ocupada, rol no permitido, etc.) |
| `423` | Cuenta bloqueada temporalmente (`ACCOUNT_LOCKED`) |

## Probar el bloqueo de cuenta

Enviar cinco veces el login con una contraseña incorrecta. Los primeros cuatro intentos responden `401`; el quinto responde `423`. El contador se puede consultar así:

```bash
docker compose exec db psql -U backendcf -d backendcf \
  -c "SELECT email, failed_login_attempts, locked_until FROM app_user;"
```

Después de 15 minutos se puede usar la contraseña correcta. El login exitoso reinicia el contador y limpia `locked_until`.

## Pruebas y calidad

```bash
./mvnw test
```

Análisis estático con SonarCloud corre en cada PR/push. El detalle de trazabilidad HU → prueba, riesgos detectados y estado del pipeline está en [README_QA.md](README_QA.md).

## Documentación relacionada

- [Calidad y pruebas](README_QA.md)
- [Arquitectura de software](README_ARQUITECTURA.md)
- [Base de datos](README_BASE_DE_DATOS.md)
- [Plan del Sprint 2](docs/PLAN-SPRINT-2.md)
- [Contrato OpenAPI](docs/openapi.yaml)
- [Decisiones de arquitectura (ADR)](docs)
