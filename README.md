# Bookly Backend

Backend de la plataforma de reservas de servicios del caso 14 de CodeF@ctory. El sistema permite gestionar usuarios y autenticación para que clientes, profesionales y administradores puedan acceder posteriormente a reservas, agendas y herramientas de gestión.

## Estado del Sprint 1

Sprint 1 comprende 7 HU según el tablero real de Azure DevOps. Estado a 2026-09-21:

**Mergeadas a `main`:**

- HU-01: registro de cliente.
- HU-02: catálogo de servicios con consulta pública y administración restringida a `ADMIN`.
- HU-03: inicio de sesión seguro.
- HU-20: configuración inicial de la plataforma, de un solo uso.
- HU-21: control de acceso a recursos ajenos (`OwnershipGuard`) — mecanismo genérico de autorización por dueño de recurso, independiente de que exista todavía la entidad de reservas (HU-08).

**Con PR abierto, pendientes de mergear:**

- HU-06: configuración de duración estándar de servicios (PR #14).
- HU-22: creación de especialistas y profesionales (PR #15).

El estado detallado de cobertura de pruebas, Quality Gate de SonarCloud y riesgos por HU está en [README_QA.md](README_QA.md).

Además:

- API REST versionada bajo `/api/v1`.
- Persistencia en PostgreSQL con JPA/Hibernate.
- Migraciones de esquema administradas por Flyway (`V1` a `V5` en `main`; `V6` para HU-22 vive en el PR #15 hasta que se mergee).
- Roles unificados: `CUSTOMER`, `PROFESSIONAL` y `ADMIN`.
- Contraseñas almacenadas mediante BCrypt.
- Tokens Bearer firmados con HMAC-SHA256 y expiración configurable.
- Bloqueo temporal después de 5 intentos fallidos durante 15 minutos.
- Manejo uniforme de errores con `errorCode`, `message`, `details`, `traceId` y `timestamp`.
- Ejecución local y contenerizada con Docker Compose.

La creación de reservas, agendas, reportes, MFA, revocación/rotación de tokens y observabilidad avanzada quedan para los siguientes sprints. En HU-02, la especialidad se representa mediante la categoría del servicio; el nombre comercial y los datos generales de la plataforma pertenecen a HU-20.

## Tecnologías

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Backend | Spring Boot 4.0.8 |
| API | Spring MVC REST |
| Persistencia | Spring Data JPA / Hibernate |
| Base de datos | PostgreSQL 16 |
| Seguridad | Spring Security, BCrypt, HMAC-SHA256 |
| Pruebas | JUnit 5 |
| Contenedores | Docker Compose |
| Construcción | Maven Wrapper |

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
existente, Flyway la marca en la línea base y aplica solo lo pendiente.

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
| `JWT_SECRET` | obligatorio | Clave de firma persistente de al menos 32 bytes; nunca debe cambiar entre reinicios |
| `JWT_EXPIRATION_SECONDS` | `3600` | Duración del token |
| `LOGIN_MAX_ATTEMPTS` | `5` | Intentos antes del bloqueo |
| `LOGIN_LOCK_MINUTES` | `15` | Duración del bloqueo |

`JWT_SECRET` debe definirse en `.env` a partir de `.env.example` y conservarse entre reinicios. En producción, esta clave, las credenciales de base de datos y demás secretos deben gestionarse fuera del código y del repositorio.

## API de autenticación

### Registrar cliente

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "juan@example.com",
  "password": "Segura@1",
  "fullName": "Juan Pérez"
}
```

La contraseña debe tener entre 8 y 64 caracteres e incluir mayúscula, minúscula, número y carácter especial. La respuesta exitosa es `201 Created`.

### Iniciar sesión

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "juan@example.com",
  "password": "Segura@1"
}
```

Una respuesta exitosa (`200 OK`) contiene un `accessToken`, el tipo `Bearer`, la expiración y el resumen del usuario. Para consumir endpoints protegidos:

```http
Authorization: Bearer <accessToken>
```

## Catálogo y configuración de plataforma

### Catálogo de servicios (HU-02)

Las consultas son públicas:

```http
GET /api/v1/servicios
GET /api/v1/servicios/{id}
GET /api/v1/servicios?categoria=<categoria>
```

La administración requiere un JWT con rol `ADMIN`:

```http
POST   /api/v1/servicios
PUT    /api/v1/servicios/{id}
DELETE /api/v1/servicios/{id}
```

El alta recibe `nombre`, `descripcion` opcional, `categoria`, `durationMinutes` y `price`; el
estado inicial siempre es `ACTIVO`. La actualización permite además `status` (`ACTIVO` o
`INACTIVO`). El nombre es único sin distinguir mayúsculas, la duración está entre 1 y 480 minutos
y el precio debe ser mayor que cero. El alcance aprobado está documentado en [ADR-005](docs/ADR-005-alcance-HU-02.md).

### Configuración inicial (HU-20)

```http
POST /api/v1/platform/setup
Content-Type: application/json
```

Este endpoint es público para permitir el aprovisionamiento inicial, pero solo admite una plataforma.
Un segundo intento responde `409 PLATFORM_ALREADY_CONFIGURED`. La estructura completa de payloads y
respuestas se encuentra en el [contrato OpenAPI](docs/openapi.yaml).

Respuestas de seguridad:

| Código | Código interno | Situación |
|---:|---|---|
| `200` | — | Inicio exitoso |
| `401` | `INVALID_CREDENTIALS` | Correo o contraseña incorrectos |
| `423` | `ACCOUNT_LOCKED` | Cuenta bloqueada temporalmente |
| `400` | `VALIDATION_ERROR` | Payload inválido |
| `401` | `UNAUTHORIZED` | Solicitud protegida sin autenticación válida |
| `403` | `ACCESS_DENIED` | Usuario autenticado sin permisos suficientes |

Los errores de seguridad mantienen el mismo envoltorio JSON con `errorCode`, `message`, `details`,
`traceId` y `timestamp`.

## Probar el bloqueo

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

16 tests en `main` (login, registro de cliente, reglas de dominio de `UserAccount`, `OwnershipGuard` de HU-21 y el smoke test de contexto Spring). Sumando lo que está en PR abiertos sin mergear (HU-06, HU-22 y la cobertura de `JwtTokenService`), el total sube a 41. Análisis estático con SonarCloud ya corre en cada PR/push; el Quality Gate del proyecto completo sigue en `ERROR` por cobertura de código nuevo acumulado hasta que se mergeen esos PR. Detalle completo de trazabilidad HU → prueba, riesgos detectados y estado del pipeline en [README_QA.md](README_QA.md).

## Documentación relacionada

- [Calidad y pruebas - Sprint 1](README_QA.md)
- [Arquitectura de software - Sprint 1](README_ARQUITECTURA.md)
- [Base de datos - Sprint 1](README_BASE_DE_DATOS.md)
- [Contrato OpenAPI](docs/openapi.yaml)

