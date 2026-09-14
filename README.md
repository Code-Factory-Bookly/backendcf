# Bookly Backend

Backend de la plataforma de reservas de servicios del caso 14 de CodeF@ctory. El sistema permite gestionar usuarios y autenticación para que pacientes, profesionales y administradores puedan acceder posteriormente a reservas, agendas y herramientas de gestión.

## Estado del Sprint 1

Actualmente se encuentran implementados:

- HU-01: registro de paciente.
- HU-03: inicio de sesión seguro.
- API REST versionada bajo `/api/v1`.
- Persistencia en PostgreSQL con JPA/Hibernate.
- Contraseñas almacenadas mediante BCrypt.
- Tokens Bearer firmados con HMAC-SHA256 y expiración configurable.
- Bloqueo temporal después de 5 intentos fallidos durante 15 minutos.
- Manejo uniforme de errores con `errorCode`, `message`, `details`, `traceId` y `timestamp`.
- Ejecución local y contenerizada con Docker Compose.

La creación de reservas, agendas, reportes, MFA, revocación/rotación de tokens y observabilidad avanzada quedan para los siguientes sprints.

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
| `JWT_SECRET` | se genera temporalmente | Clave de firma; en entornos persistentes debe ser estable y secreta |
| `JWT_EXPIRATION_SECONDS` | `3600` | Duración del token |
| `LOGIN_MAX_ATTEMPTS` | `5` | Intentos antes del bloqueo |
| `LOGIN_LOCK_MINUTES` | `15` | Duración del bloqueo |

En producción, `JWT_SECRET`, credenciales de base de datos y demás secretos deben gestionarse fuera del código y del repositorio.

## API de autenticación

### Registrar paciente

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

Respuestas de seguridad:

| Código | Código interno | Situación |
|---:|---|---|
| `200` | — | Inicio exitoso |
| `401` | `INVALID_CREDENTIALS` | Correo o contraseña incorrectos |
| `423` | `ACCOUNT_LOCKED` | Cuenta bloqueada temporalmente |
| `400` | `VALIDATION_ERROR` | Payload inválido |

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

La suite incluye prueba de contexto y pruebas unitarias para login exitoso, credenciales incorrectas y bloqueo al quinto intento. El análisis estático, la cobertura mínima del 65 %, CI/CD y pruebas de integración con PostgreSQL quedan como actividades de calidad a completar en el ciclo del proyecto.

## Documentación relacionada

- [Arquitectura de software - Sprint 1](README_ARQUITECTURA.md)
- [Base de datos - Sprint 1](README_BASE_DE_DATOS.md)
- [HU-01 - Registro de paciente](HU-01_Registro_Paciente.md)
- [Lineamientos de arquitectura](Lineamientos_Arquitectura.md)
