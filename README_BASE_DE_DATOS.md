# Base de datos - Sprint 1

## 1. Propósito y alcance

Este documento presenta el diseño lógico y físico implementado de Bookly para HU-01 (registro de cliente), HU-02 (catálogo de servicios), HU-03 (inicio de sesión seguro), HU-06 (duración estándar de servicios), HU-20 (configuración inicial de la plataforma) y HU-22 (registro de profesionales). PostgreSQL es la fuente de verdad para las cuentas, los perfiles de profesionales, el catálogo —incluida la duración de cada servicio—, la plataforma, los roles y el estado de protección contra intentos fallidos.

El modelo está preparado para extenderse en los siguientes sprints con agendas y reservas. Las especialidades de HU-02 se representan inicialmente mediante `servicios.categoria`, de acuerdo con el [ADR-005](docs/ADR-005-alcance-HU-02.md); no existe una entidad independiente mientras no haya reglas que la justifiquen. La especialidad de un profesional (HU-22) sigue el mismo criterio: es un atributo de `profesionales.especialidad`.

## 2. Entidades y relaciones

### Diagrama ER

```mermaid
erDiagram
    APP_USER {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR full_name
        VARCHAR role
        BOOLEAN enabled
        INTEGER failed_login_attempts
        TIMESTAMPTZ locked_until
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    PLATFORM {
        UUID id PK
        VARCHAR name
        VARCHAR status
        TIMESTAMPTZ created_at
    }
    SERVICIOS {
        UUID id PK
        VARCHAR nombre UK
        VARCHAR descripcion
        VARCHAR categoria
        INTEGER duracion_minutos
        NUMERIC precio
        VARCHAR estado
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    PROFESIONALES {
        UUID id PK, FK
        VARCHAR rol FK
        VARCHAR especialidad
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
  APP_USER ||--o| PROFESIONALES : "tiene perfil"
```

Existen las entidades persistidas `app_user`, `platform`, `servicios` y `profesionales`. La única relación
FK es `profesionales (id, rol) → app_user (id, role)`: es 1 a 1, comparte la clave primaria y solo admite
cuentas con rol `PROFESSIONAL`. Una cuenta tiene como máximo un perfil de profesional y un perfil no existe
sin su cuenta. La plataforma es única y el catálogo se mantiene independiente hasta que las HUs de agendas y
reservas definan sus relaciones. `app_user.email` y `servicios.nombre` tienen unicidad para sus respectivas
reglas de negocio.

## 3. Reglas de negocio persistidas

| Regla | Implementación |
|---|---|
| Cada cuenta tiene un identificador | `id UUID PRIMARY KEY` |
| El correo identifica una cuenta | `email NOT NULL UNIQUE`, longitud máxima 320 |
| No se almacenan contraseñas en claro | `password_hash` recibe BCrypt desde la aplicación |
| Toda cuenta tiene rol | `role NOT NULL` y `CHECK` para `CUSTOMER`, `PROFESSIONAL`, `ADMIN` |
| Una cuenta nueva está habilitada | `enabled NOT NULL`, valor inicial `TRUE` |
| Los fallos son contables | `failed_login_attempts NOT NULL DEFAULT 0` |
| El bloqueo puede estar ausente | `locked_until` permite `NULL` cuando no hay bloqueo |
| Las fechas son trazables | `created_at` y `updated_at` son obligatorias |
| Solo una cuenta `PROFESSIONAL` puede tener perfil de profesional | `profesionales.rol` vale siempre `PROFESSIONAL` (`DEFAULT` y `CHECK`) y la FK compuesta `(id, rol)` apunta a `app_user (id, role)`: la base rechaza el perfil de una cuenta con otro rol |
| Cada cuenta tiene como máximo un perfil | `profesionales.id` es la clave primaria y a la vez la FK hacia la cuenta |
| La especialidad es obligatoria | `especialidad NOT NULL`, máximo 100 y `CHECK` que impide cadenas vacías o de solo espacios |
| Una cuenta con perfil no se elimina ni cambia de rol | La FK no tiene `ON DELETE CASCADE` ni `ON UPDATE CASCADE`: la cuenta se deshabilita con `enabled = FALSE` |

## 4. Modelo lógico

### APP_USER

| Atributo | Tipo lógico | Nulabilidad | Clave / dominio | Descripción |
|---|---|---:|---|---|
| `id` | Identificador UUID | No | PK | Identificador interno |
| `email` | Cadena de correo | No | UK, máximo 320 | Credencial funcional normalizada a minúsculas |
| `password_hash` | Cadena hash | No | — | Resultado de BCrypt |
| `full_name` | Cadena | No | Máximo 150 | Nombre mostrado |
| `role` | Enumeración | No | CUSTOMER, PROFESSIONAL, ADMIN | Rol de autorización futuro |
| `enabled` | Booleano | No | — | Permite deshabilitar la cuenta |
| `failed_login_attempts` | Entero | No | >= 0 por regla de aplicación | Intentos fallidos acumulados |
| `locked_until` | Fecha-hora con zona | Sí | — | Fin del bloqueo temporal |
| `created_at` | Fecha-hora con zona | No | — | Fecha de creación |
| `updated_at` | Fecha-hora con zona | No | — | Última actualización |

El modelo se encuentra normalizado para este alcance: cada atributo es atómico, no existen grupos repetidos ni dependencias parciales, y los atributos describen únicamente a la cuenta. El diseño cumple el objetivo de 3FN para la entidad actual.

### PLATFORM

| Atributo | Tipo lógico | Nulabilidad | Clave / dominio | Descripción |
|---|---|---:|---|---|
| `id` | Identificador UUID | No | PK, una sola fila | Identificador de la plataforma |
| `name` | Cadena | No | Máximo 200 | Nombre de la plataforma |
| `status` | Enumeración | No | ACTIVE, SUSPENDED | Estado operativo |
| `created_at` | Fecha-hora con zona | No | — | Fecha de aprovisionamiento |

La unicidad de `platform` se garantiza mediante el índice único sobre la expresión constante `true`.

### SERVICIOS

| Atributo | Tipo lógico | Nulabilidad | Clave / dominio | Descripción |
|---|---|---:|---|---|
| `id` | Identificador UUID | No | PK | Identificador interno |
| `nombre` | Cadena | No | Único sin distinguir mayúsculas, máximo 150 | Nombre comercial del servicio |
| `descripcion` | Cadena | Sí | Máximo 500 | Descripción opcional |
| `categoria` | Cadena | No | Máximo 100 | Especialidad funcional del servicio |
| `duracion_minutos` | Entero | No | 1 a 480 | Duración usada por futuras agendas |
| `precio` | Decimal | No | Mayor que 0, máximo 2 decimales | Precio del servicio |
| `estado` | Enumeración | No | ACTIVO, INACTIVO | Disponibilidad del servicio |
| `created_at` | Fecha-hora con zona | No | — | Fecha de creación |
| `updated_at` | Fecha-hora con zona | No | — | Última actualización |

`servicios` se mantiene independiente en este sprint. La asignación de profesionales a servicios y la
relación con reservas se agregarán cuando las HUs correspondientes definan sus claves foráneas y reglas
de negocio.

### PROFESIONALES

| Atributo | Tipo lógico | Nulabilidad | Clave / dominio | Descripción |
|---|---|---:|---|---|
| `id` | Identificador UUID | No | PK; FK compuesta con `rol` hacia `app_user` | Mismo identificador de la cuenta del profesional |
| `rol` | Cadena | No | Siempre `PROFESSIONAL` | Rol que debe tener la cuenta; sostiene la FK compuesta |
| `especialidad` | Cadena | No | Máximo 100, no vacía | Especialidad funcional del profesional |
| `created_at` | Fecha-hora con zona | No | — | Fecha de creación del perfil |
| `updated_at` | Fecha-hora con zona | No | — | Última actualización |

El nombre, el correo y la contraseña del profesional viven en `app_user`; `profesionales` solo guarda lo
que describe al perfil, por lo que no hay datos duplicados. La columna `rol` es la única redundancia y es
deliberada: existe para que la FK compuesta `(id, rol)` haga que la base, y no solo la aplicación, garantice
que únicamente una cuenta `PROFESSIONAL` puede tener perfil. Como vale siempre `PROFESSIONAL` por defecto,
la aplicación no la mapea ni la envía.

Compartir la clave primaria hace que el identificador del profesional sea el mismo UUID que el token JWT
lleva en `sub`, que es el valor que `OwnershipGuard` compara al proteger recursos propios como la agenda.

Consecuencia: mientras una cuenta tenga perfil de profesional no se puede cambiar su rol. Cuando exista el
flujo de cambio de roles (HU-17), quitar a alguien de `PROFESSIONAL` exigirá eliminar antes su perfil.

## 5. Preguntas de negocio y consultas clave

Las siguientes preguntas cubren filtros, agregaciones y consultas de estado. Las consultas son ejemplos para PostgreSQL y deben ejecutarse sobre el esquema creado por Flyway.

### 1. ¿Existe una cuenta con un correo determinado?

```sql
SELECT id, email, enabled
FROM app_user
WHERE email = LOWER('juan@example.com');
```

### 2. ¿Cuántas cuentas existen por rol?

```sql
SELECT role, COUNT(*) AS total
FROM app_user
GROUP BY role
ORDER BY role;
```

### 3. ¿Qué cuentas están bloqueadas actualmente?

```sql
SELECT email, failed_login_attempts, locked_until
FROM app_user
WHERE locked_until IS NOT NULL
  AND locked_until > CURRENT_TIMESTAMP
ORDER BY locked_until;
```

### 4. ¿Qué cuentas acumulan más intentos fallidos?

```sql
SELECT email, failed_login_attempts, locked_until
FROM app_user
WHERE failed_login_attempts > 0
ORDER BY failed_login_attempts DESC, email;
```

### 5. ¿Cuántas cuentas se registraron por día?

```sql
SELECT created_at::date AS registration_date, COUNT(*) AS total
FROM app_user
GROUP BY created_at::date
ORDER BY registration_date DESC;
```

### 6. ¿Qué porcentaje de cuentas está habilitado?

```sql
SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE enabled) / NULLIF(COUNT(*), 0), 2)
       AS enabled_percentage
FROM app_user;
```

### 7. ¿Qué servicios activos existen por categoría?

```sql
SELECT categoria, COUNT(*) AS total_servicios
FROM servicios
WHERE estado = 'ACTIVO'
GROUP BY categoria
ORDER BY categoria;
```

### 8. ¿Qué servicios requieren más tiempo de atención?

```sql
SELECT nombre, categoria, duracion_minutos, precio
FROM servicios
WHERE estado = 'ACTIVO'
ORDER BY duracion_minutos DESC, nombre;
```
### 9. ¿Cuántos profesionales hay por especialidad?

```sql
SELECT especialidad, COUNT(*) AS total_profesionales
FROM profesionales
GROUP BY especialidad
ORDER BY total_profesionales DESC, especialidad;
```

### 10. ¿Qué profesionales están habilitados y con qué especialidad?

```sql
SELECT u.id, u.full_name, u.email, p.especialidad
FROM profesionales p
JOIN app_user u ON u.id = p.id
WHERE u.enabled
ORDER BY p.especialidad, u.full_name;
```

### 11. ¿Qué especialidades no tienen ningún servicio activo en el catálogo?

```sql
SELECT DISTINCT p.especialidad
FROM profesionales p
WHERE NOT EXISTS (
    SELECT 1
    FROM servicios s
    WHERE s.estado = 'ACTIVO'
      AND lower(s.categoria) = lower(p.especialidad)
)
ORDER BY p.especialidad;
```

### 12. Consistencia: ¿hay cuentas `PROFESSIONAL` sin perfil?

Con datos consistentes debe devolver 0 filas. La base impide el caso contrario (un perfil sobre una cuenta
que no es `PROFESSIONAL`), pero no que una cuenta con ese rol exista sin perfil.

```sql
SELECT u.id, u.email
FROM app_user u
LEFT JOIN profesionales p ON p.id = u.id
WHERE u.role = 'PROFESSIONAL'
  AND p.id IS NULL;
```
## 6. Modelo físico PostgreSQL

El modelo físico lo aplica **Flyway** al arrancar, desde `src/main/resources/db/migration`:

- `V1__baseline_app_user.sql`: creación inicial de `app_user`.
- `V2__add_login_security.sql`: columnas de bloqueo de cuenta y CHECK de roles (antes `002-add-login-security.sql`).
- `V3__create_platform.sql`: tabla `platform`, limitada a una sola fila.
- `V4__rename_patient_role_to_customer.sql`: migra el rol histórico `PATIENT` a `CUSTOMER` y actualiza la restricción.
- `V5__create_service_catalog.sql`: tabla `servicios` y sus restricciones de catálogo.
- `V6__create_professionals.sql`: tabla `profesionales` (perfil 1 a 1 de `app_user` con la especialidad) y la restricción que solo admite cuentas con rol `PROFESSIONAL`.

La unicidad de `app_user.email` y la existencia de una sola fila en `platform` se resuelven en
PostgreSQL mediante restricciones/índices únicos. La aplicación traduce sus violaciones a errores
funcionales `409`, según [ADR-006](docs/ADR-006-concurrencia-integridad.md).

DDL equivalente para una base nueva:

```sql
CREATE TABLE IF NOT EXISTS app_user (
    id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role
        CHECK (role IN ('CUSTOMER', 'PROFESSIONAL', 'ADMIN'))
);
```

Las tablas `platform`, `servicios` y `profesionales` se crean mediante `V3__create_platform.sql`,
`V5__create_service_catalog.sql` y `V6__create_professionals.sql`, respectivamente. No deben copiarse
manualmente en producción: Flyway administra el orden y el historial de aplicación.

DDL de `profesionales` (HU-22), tal como lo aplica `V6__create_professionals.sql`:

```sql
ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_id_role UNIQUE (id, role);

CREATE TABLE profesionales (
    id UUID NOT NULL,
    rol VARCHAR(30) NOT NULL DEFAULT 'PROFESSIONAL',
    especialidad VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_profesionales PRIMARY KEY (id),
    CONSTRAINT ck_profesionales_rol CHECK (rol = 'PROFESSIONAL'),
    CONSTRAINT ck_profesionales_especialidad CHECK (length(btrim(especialidad)) > 0),
    CONSTRAINT fk_profesionales_app_user_rol FOREIGN KEY (id, rol) REFERENCES app_user (id, role)
);
```

`uk_app_user_id_role` no agrega una regla nueva, porque `id` ya es la clave primaria de `app_user`: PostgreSQL
exige una restricción de unicidad sobre `(id, role)` para aceptar la FK compuesta. La clave primaria de
`profesionales` cubre la búsqueda por identificador y el `JOIN` con `app_user`. No se indexa `especialidad`
en este sprint: con el volumen esperado no hay una consulta que lo justifique.

El índice único generado para `email` soporta la consulta principal de login y evita duplicados. No se agregan índices redundantes en Sprint 1. Si las consultas de cuentas bloqueadas aumentan, se puede evaluar un índice parcial:

```sql
CREATE INDEX IF NOT EXISTS idx_app_user_locked_until
    ON app_user (locked_until)
    WHERE locked_until IS NOT NULL;
```

Debe medirse su beneficio con `EXPLAIN (ANALYZE, BUFFERS)` antes de incorporarlo al script definitivo.

## 7. Integridad, seguridad y operación

- El usuario de aplicación de Compose es configurable y no debe ser el superusuario en producción.
- Las credenciales se entregan mediante variables de entorno; no se guardan en el código.
- Las consultas se realizan con Spring Data JPA; no se concatenan entradas del usuario en SQL.
- `password_hash` nunca contiene la contraseña original.
- El conteo de fallos se confirma en una transacción independiente para evitar que el error HTTP haga rollback del bloqueo.
- Las migraciones versionadas de Flyway son la fuente de verdad del esquema. `IF NOT EXISTS` se usa
  únicamente donde la migración debe ser tolerante con objetos creados por versiones anteriores; no
  reemplaza el historial ni la validación de Flyway.
- La conexión de producción debe usar TLS y respaldos protegidos.
- No se registran contraseñas, hashes, tokens ni secretos en logs.

## 8. Volumen y crecimiento inicial

El volumen esperado del Sprint 1 es bajo y la tabla es pequeña. La clave UUID evita exponer una secuencia predecible y facilita futuras integraciones. El campo `email` es el principal acceso de lectura. Para sprints posteriores deberán estimarse usuarios, reservas, historial y concurrencia antes de decidir particionamiento o desnormalización.

## 9. Ejecución y verificación

Levantar la base y la aplicación para que Flyway aplique las migraciones:

```bash
docker compose up -d --build
```

Si el puerto local `5432` está ocupado, PostgreSQL puede ejecutarse con un puerto de host alterno
modificando temporalmente el mapeo de Compose; la aplicación siempre debe conectarse al puerto interno
`5432` del servicio `db`.

Verificar estructura y datos:

```bash
docker compose exec db psql -U backendcf -d backendcf -c '\dt'
docker compose exec db psql -U backendcf -d backendcf -c '\d app_user'
docker compose exec db psql -U backendcf -d backendcf -c '\d platform'
docker compose exec db psql -U backendcf -d backendcf -c '\d servicios'
docker compose exec db psql -U backendcf -d backendcf -c '\d profesionales'
docker compose exec db psql -U backendcf -d backendcf -c 'SELECT installed_rank, version, description FROM flyway_schema_history ORDER BY installed_rank;'
```

La verificación esperada es que el historial termine en la versión `6 - create professionals` y
que todas las migraciones tengan `success = true`. En bases existentes, V1 puede aparecer como
`<< Flyway Baseline >>` y las versiones posteriores se aplican normalmente.

Las pruebas de integridad de HU-22 (restricciones de `profesionales`, incluida la que rechaza el perfil de
una cuenta que no es `PROFESSIONAL`) se ejecutan contra la base creada por Flyway. El script corre dentro de
una transacción que termina en `ROLLBACK`, por lo que no deja datos:

```powershell
Get-Content docs/evidencia/HU-22-integridad.sql | docker exec -i backendcf-db psql -U backendcf -d backendcf
```

Cada prueba imprime `OK prueba N: ...` y, si alguna restricción no se cumple, el script se detiene con
`FALLO prueba N: ...`.

Si ya habías levantado el proyecto con una versión anterior de `V6`, Flyway detecta el cambio de contenido
y falla al arrancar. En un entorno de desarrollo se resuelve con `docker compose down -v` (borra los datos)
y un nuevo `docker compose up --build`.

## 10. Pendientes de evolución

Para los siguientes sprints se deberán agregar y relacionar las entidades que surjan de las HU: profesional, disponibilidad, reserva, cancelación e historial. También se deberán completar auditoría de acciones críticas, roles de base de datos con mínimo privilegio, pruebas de restauración y procedimientos o triggers únicamente cuando exista una regla que justifique su ejecución dentro de PostgreSQL.
