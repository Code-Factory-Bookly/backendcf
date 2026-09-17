# Arquitectura de software - Sprint 1

## 1. Propósito y alcance

Este documento describe la arquitectura implementada del backend de Bookly para el Sprint 1. Su alcance incluye el proyecto base Spring Boot, HU-01 (registro de cliente), HU-02 (catálogo de servicios), HU-03 (inicio de sesión seguro), HU-20 (configuración inicial), la persistencia y el despliegue con Docker Compose.

La solución se mantiene como un monolito modular porque el alcance actual es pequeño, existe un único dominio principal y el equipo necesita reducir la complejidad operativa. La separación por módulos permite evolucionar a nuevos dominios sin iniciar prematuramente una arquitectura de microservicios.

## 2. Requisitos arquitectónicos

| Tipo | Requisito |
|---|---|
| Funcional | Registrar clientes e iniciar sesión con credenciales |
| Seguridad | Hash de contraseñas, validación, bloqueo temporal y autenticación Bearer |
| Integridad | Correo único, campos obligatorios y roles válidos |
| Operación | Ejecutar backend y PostgreSQL mediante Docker Compose |
| Mantenibilidad | Separar presentación, aplicación, dominio e infraestructura |
| Interoperabilidad | API REST versionada y JSON |

## 3. Vista de contexto C4

```mermaid
flowchart LR
    Usuario[Cliente / Profesional / Administrador]
    Sistema[Bookly Backend<br/>API REST de reservas]
    DB[(PostgreSQL)]
    Usuario -->|HTTP JSON / Bearer| Sistema
    Sistema -->|JPA / SQL| DB
```

El cliente HTTP representa Postman durante las pruebas del Sprint 1 y posteriormente podrá ser un frontend web. El backend es responsable de validar, autenticar y aplicar reglas; PostgreSQL conserva los datos transaccionales.

HU-02 se implementa como un catálogo independiente de servicios. La categoría representa la
especialidad en este alcance; no se crea una entidad independiente. HU-20 concentra el nombre y la
configuración general de la plataforma.

## 4. Vista de contenedores C4

```mermaid
flowchart TB
    Client[Cliente REST]
    subgraph Docker Compose
      API[Backend Bookly<br/>Spring Boot / Java 17]
      DB[(PostgreSQL 16<br/>app_user / platform / servicios)]
    end
    Client -->|REST /api/v1| API
    API -->|Spring Data JPA| DB
```

## 5. Vista de despliegue

```mermaid
flowchart TB
    Client[Cliente REST / Frontend futuro]
    subgraph Runtime[Entorno de ejecución]
        App[Contenedor Bookly API<br/>Spring Boot / Java 17<br/>Puerto 8080]
        Db[Contenedor PostgreSQL 16<br/>Volumen postgres_data<br/>Puerto 5432 interno]
    end
    Client -->|HTTP JSON / Bearer| App
    App -->|JDBC / Flyway| Db
```

El backend es stateless y la base conserva el estado transaccional. El volumen de PostgreSQL debe
respaldarse y no debe compartirse entre entornos. `JWT_SECRET` se inyecta como secreto de configuración
y no se almacena en la imagen.

El contenedor backend es stateless: no mantiene sesión HTTP. El estado de cuenta, intentos fallidos y bloqueo se almacenan en PostgreSQL. El token firmado permite autenticar solicitudes posteriores.

## 6. Vista de componentes del módulo de autenticación

```mermaid
flowchart LR
    C[AuthController]
    R[RegisterCustomerService]
    L[LoginService]
    A[LoginAttemptRecorder]
    T[JwtTokenService]
    F[JwtAuthenticationFilter]
    P[UserAccountRepository]
    U[(UserAccount / app_user)]
    C --> R
    C --> L
    R --> P
    L --> P
    L --> A
    L --> T
    A --> P
    P --> U
    F --> T
    F -->|SecurityContext| C
```

### Interfaces principales

| Componente | Interfaz o responsabilidad |
|---|---|
| `AuthController` | `POST /api/v1/auth/register` y `POST /api/v1/auth/login` |
| `RegisterCustomerService` | Normalizar email/nombre, validar duplicados y guardar BCrypt como `CUSTOMER` |
| `LoginService` | Validar cuenta, contraseña, estado de bloqueo y emitir respuesta |
| `LoginAttemptRecorder` | Persistir fallos en una transacción independiente para evitar rollback del contador |
| `JwtTokenService` | Firmar y validar token HMAC-SHA256 con `sub`, `email`, `role` y `exp` |
| `JwtAuthenticationFilter` | Leer `Authorization: Bearer`, validar token y poblar `SecurityContext` |
| `UserAccountRepository` | Acceso JPA a `app_user` por UUID y email |
| `GlobalExceptionHandler` | Contrato uniforme para errores de validación, registro y autenticación |

### Componentes de HU-02 y HU-20

| Módulo | Responsabilidad |
|---|---|
| `ServiceOfferingController` | CRUD de `/api/v1/servicios` y filtro público por `categoria` |
| `ServiceOfferingService` | Normalización, validación funcional y estado inicial `ACTIVO` |
| `ServiceOfferingRepository` | Persistencia y unicidad lógica del nombre del servicio |
| `PlatformSetupController` | Aprovisionamiento único mediante `POST /api/v1/platform/setup` |
| `PlatformSetupService` | Validación de la configuración y traducción de concurrencia a `409` |

## 7. Flujo de login

```mermaid
sequenceDiagram
    actor Usuario
    participant API as AuthController
    participant Login as LoginService
    participant DB as PostgreSQL
    participant JWT as JwtTokenService

    Usuario->>API: POST /auth/login
    API->>Login: LoginRequest validado
    Login->>DB: Buscar por email normalizado
    alt Cuenta inexistente o contraseña incorrecta
        Login->>DB: Registrar fallo en REQUIRES_NEW
        Login-->>API: 401 o 423
    else Cuenta bloqueada
        Login-->>API: 423 ACCOUNT_LOCKED
    else Credenciales correctas
        Login->>DB: Reiniciar contador y bloqueo
        Login->>JWT: Firmar token con expiración
        JWT-->>Login: accessToken
        Login-->>API: 200 LoginResponse
    end
```

## 8. Seguridad implementada y pendientes

Implementado: BCrypt, política de contraseña, normalización de email, respuestas genéricas ante credenciales inválidas, bloqueo temporal, token firmado y expiración, API stateless, validación de payloads y secretos configurables por ambiente. `JWT_SECRET` es obligatorio, persistente y no se genera automáticamente al iniciar. Las solicitudes sin autenticación responden `401 UNAUTHORIZED` y los usuarios autenticados sin permisos responden `403 ACCESS_DENIED`, ambos con el contrato de error uniforme.

Pendiente para completar los lineamientos avanzados: MFA para administración, revocación y rotación de tokens, cookies `HttpOnly`/`Secure` si el cliente las requiere, CORS restringido, rate limiting por IP/cuenta, logs estructurados de eventos de seguridad y SCA/SAST/DAST.

## 9. Decisiones arquitectónicas (ADR)

### ADR-001: Monolito modular

- **Estado:** Aceptada.
- **Contexto:** El Sprint 1 contiene autenticación y persistencia inicial.
- **Decisión:** Usar un backend Spring Boot organizado por módulos (`auth`, `shared`) y capas explícitas.
- **Alternativas:** Microservicios desde el inicio; arquitectura CRUD sin separación.
- **Consecuencias:** Menor costo de despliegue y depuración; se requiere disciplina para evitar acoplamiento entre dominios.

### ADR-002: PostgreSQL como persistencia transaccional

- **Estado:** Aceptada.
- **Contexto:** Usuarios, roles, restricciones y estado de bloqueo requieren consistencia.
- **Decisión:** PostgreSQL 16 como fuente de verdad, accedido mediante Spring Data JPA.
- **Alternativas:** Base documental; servicio administrado sin control local.
- **Consecuencias:** Integridad mediante constraints y facilidad de ejecución local; las migraciones deben versionarse y aplicarse correctamente.

### ADR-003: BCrypt y token Bearer HMAC-SHA256

- **Estado:** Aceptada para Sprint 1.
- **Contexto:** Se necesita autenticación stateless sin almacenar contraseñas en claro.
- **Decisión:** Guardar solo BCrypt y emitir tokens firmados con expiración configurable.
- **Alternativas:** Sesiones HTTP; OAuth/OIDC externo; almacenar contraseñas cifradas reversiblemente.
- **Consecuencias:** Escalamiento sencillo y menor estado en el backend; se deben agregar rotación, revocación y MFA para producción avanzada.

### ADR-004: Persistir el fallo fuera de la transacción de respuesta

- **Estado:** Aceptada.
- **Contexto:** Devolver una excepción después de incrementar el contador provocaba rollback.
- **Decisión:** `LoginAttemptRecorder` usa `REQUIRES_NEW` para confirmar el contador antes de responder `401` o `423`.
- **Consecuencias:** El bloqueo es durable; requiere revisar concurrencia y control de intentos distribuidos en una futura evolución.

### ADR-005: Alcance reducido de HU-02

- **Estado:** Aceptada.
- **Decisión:** HU-02 se limita al catálogo de servicios; la especialidad se representa mediante la
  categoría del servicio. La configuración general y los datos de contacto pertenecen a HU-20.
- **Consecuencia:** No se crean tablas ni endpoints independientes de organización o especialidades
  en este sprint.
- **Detalle:** [ADR-005 - Alcance HU-02](docs/ADR-005-alcance-HU-02.md).

### ADR-006: Concurrencia e integridad en operaciones únicas

- **Estado:** Aceptada.
- **Decisión:** PostgreSQL garantiza la unicidad del correo y de la plataforma; los casos de uso
  ejecutan `saveAndFlush()` y traducen las violaciones a respuestas `409` funcionales.
- **Detalle:** [ADR-006 - Concurrencia e integridad](docs/ADR-006-concurrencia-integridad.md).

## 10. Contrato de API

El contrato REST versionado se encuentra en [docs/openapi.yaml](docs/openapi.yaml). Toda modificación
de rutas, payloads, códigos HTTP o esquemas debe actualizar este archivo en el mismo cambio.

## 11. Estructura del código

```text
src/main/java/com/bookly/backendcf/
├── auth/
│   ├── application/       # casos de uso y reglas de autenticación
│   ├── configuration/      # BCrypt y Spring Security
│   ├── domain/model/       # UserAccount y UserRole
│   ├── infrastructure/    # repositorios JPA
│   ├── presentation/      # controlador y DTOs REST
│   └── security/           # emisión y filtro JWT
└── shared/error/          # errores HTTP uniformes
```

## 12. Evidencia y próximos pasos

La aplicación compila y cuenta con despliegue definido mediante Docker Compose. Se validó el arranque
contra PostgreSQL 16 en contenedor: Flyway validó las cinco migraciones y dejó el esquema en V5, con
las tablas `app_user`, `platform` y `servicios`. También se verificó que un endpoint protegido responde
`401` con el contrato uniforme. Las pruebas automatizadas, Quality Gate y la evidencia funcional
detallada pertenecen al rol de Calidad.
