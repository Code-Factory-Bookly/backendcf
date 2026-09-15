# HU-02 · Perfil de la organización y catálogo de servicios

Resumen de los cambios de esta rama, para acompañar el pull request.

---

## Qué resuelve

El administrador configura el **perfil público** de la organización (nombre comercial, contacto y
dirección de la sede) y registra el **catálogo de especialidades y servicios**. Cualquier visitante
puede consultar perfil y catálogo sin iniciar sesión; solo un usuario con rol `ADMIN` puede
modificarlos.

> **Nota de alcance:** el Gherkin original habla de `Tenant_A`, `ADMIN_TENANT` y `tenant_id`. Como la
> plataforma atiende a **una sola organización**, se adaptó: el rol es `ADMIN` (el que ya existe desde
> HU-03) y no hay `tenant_id`.

---

## Endpoints

| Método | Ruta | Acceso | Respuestas |
|---|---|---|---|
| `GET` | `/api/v1/organization/profile` | Público | `200` · `404 ORGANIZATION_PROFILE_NOT_FOUND` |
| `PUT` | `/api/v1/organization/profile` | `ADMIN` | `200` (lo crea la primera vez, lo actualiza después) |
| `GET` | `/api/v1/specialties` | Público | `200` |
| `POST` | `/api/v1/specialties` | `ADMIN` | `201` · `409 SPECIALTY_ALREADY_EXISTS` |
| `PUT` | `/api/v1/specialties/{id}` | `ADMIN` | `200` · `404 SPECIALTY_NOT_FOUND` · `409 SPECIALTY_ALREADY_EXISTS` |
| `DELETE` | `/api/v1/specialties/{id}` | `ADMIN` | `204` · `404 SPECIALTY_NOT_FOUND` · `409 SPECIALTY_HAS_SERVICES` |
| `GET` | `/api/v1/services?specialtyId=` | Público | `200` (filtro opcional) |
| `GET` | `/api/v1/services/{id}` | Público | `200` · `404 SERVICE_NOT_FOUND` |
| `POST` | `/api/v1/services` | `ADMIN` | `201` · `404 SPECIALTY_NOT_FOUND` · `409 SERVICE_ALREADY_EXISTS` |
| `PUT` | `/api/v1/services/{id}` | `ADMIN` | `200` · `404` · `409 SERVICE_ALREADY_EXISTS` |
| `DELETE` | `/api/v1/services/{id}` | `ADMIN` | `204` · `404 SERVICE_NOT_FOUND` |

Cualquier campo obligatorio vacío o inválido responde `400 VALIDATION_ERROR` con el envoltorio
uniforme (`errorCode`, `message`, `details`, `traceId`, `timestamp`).

### Perfil

```json
PUT /api/v1/organization/profile
{
  "commercialName": "Clínica Norte",
  "contactEmail": "contacto@clinicanorte.co",
  "contactPhone": "+57 604 444 5566",
  "address": "Calle 50 # 45-20, Medellín"
}
```

Los cuatro campos son obligatorios.

### Servicio

```json
POST /api/v1/services
{
  "specialtyId": "3f2a1c4e-...",
  "name": "Instalación de brackets",
  "description": "Brackets metálicos convencionales",
  "durationMinutes": 90,
  "price": 1800000.00
}
```

Obligatorios: `specialtyId`, `name`, `durationMinutes` (entre 5 y 480) y `price` (≥ 0, hasta 2
decimales). La descripción es opcional.

---

## Esquema

Nuevo script `docker/postgres/init/003-create-organization-profile-and-catalog.sql`, siguiendo la
convención vigente en `main` (sin Flyway):

| Tabla | Contenido | Restricciones |
|---|---|---|
| `organization_profile` | Perfil público | Índice único sobre una expresión constante: **una sola fila** |
| `specialty` | Especialidades | Nombre único sin distinguir mayúsculas |
| `service_offering` | Servicios | FK a `specialty`; duración entre 5 y 480; precio ≥ 0; nombre único dentro de cada especialidad |

> ⚠️ Los scripts de `docker/postgres/init` **solo se ejecutan al crear el volumen**. Sobre un volumen
> existente hay que aplicarlo a mano:
>
> ```bash
> docker compose exec db psql -U backendcf -d backendcf \
>   -f /docker-entrypoint-initdb.d/003-create-organization-profile-and-catalog.sql
> ```

### Relación con HU-20 (PR pendiente)

HU-20 introduce Flyway y la tabla `organization`. Cuando se integre:

- Este script debe convertirse en una migración Flyway posterior a las de HU-20.
- `organization_profile` **convive** con `organization` sin conflicto: la primera guarda el perfil que
  ve el cliente; la segunda, la identidad legal (razón social, NIT).
- `GlobalExceptionHandler` y `SecurityConfiguration` tendrán un conflicto trivial de merge: ambas
  ramas añaden líneas en los mismos archivos.

---

## Decisiones tomadas

- **El perfil vive en su propia tabla**, `organization_profile`, y no en `organization`, porque esa
  tabla solo existe en el PR pendiente de HU-20. Así ninguna de las dos ramas pisa a la otra.
- **Cada servicio pertenece a una especialidad** (relación 1-N). El catálogo público se puede filtrar
  por especialidad, y deja preparada la futura relación entre profesionales y servicios.
- **Perfil y servicios se guardan por separado.** El escenario "sin servicios mínimos" se interpreta
  como la validación de los campos obligatorios de cada servicio.
- **No se puede borrar una especialidad con servicios** (`409 SPECIALTY_HAS_SERVICES`): dejaría
  servicios huérfanos.
- **La entidad se llama `ServiceOffering`**, no `Service`, para no confundirse con la anotación
  `@Service` de Spring. La ruta pública sigue siendo `/api/v1/services`.
- **Autorización por rol** con `hasRole("ADMIN")` en `SecurityConfiguration`. Funciona sin cambios en
  la autenticación: el `JwtAuthenticationFilter` de HU-03 ya convierte el rol del token en authority.
- **Dos excepciones base en `shared/error`**, `ResourceNotFoundException` (404) y
  `ResourceConflictException` (409). Cada excepción concreta aporta su `errorCode`, y el manejador
  global las atiende con dos métodos en vez de uno por excepción.
- **La duración es obligatoria** porque la épica de agendas la necesitará para calcular qué huecos
  ocupa una cita. El precio se guarda como `NUMERIC(12,2)` y `BigDecimal`, nunca como coma flotante.
- **El listado de servicios carga la especialidad en la misma consulta** (`@EntityGraph`), para no
  lanzar una consulta adicional por cada servicio.

---

## Cómo probarlo

1. Base limpia, para que corra el script 003:
   ```bash
   docker compose down -v && docker compose up --build
   ```
2. Crear un administrador. En `main` todavía no hay endpoint para eso (lo trae HU-20), así que se
   registra un paciente y se le cambia el rol:
   ```bash
   curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
     -d '{"email":"admin@clinicanorte.co","password":"Secreta123*","fullName":"María Restrepo"}'

   docker compose exec db psql -U backendcf -d backendcf \
     -c "UPDATE app_user SET role = 'ADMIN' WHERE email = 'admin@clinicanorte.co';"
   ```
3. Abrir `docs/evidencia/HU-02.http` en VS Code (extensión REST Client) y ejecutar los bloques en
   orden. El bloque 0 inicia sesión y el token se reutiliza en los demás.

---

## Verificación ejecutada

Contra PostgreSQL 16.10 con el esquema de `main` más el script 003, no contra H2:

- `mvnw clean test`: **BUILD SUCCESS**, 4 pruebas, 0 fallos.
- Arranque con `ddl-auto=validate`: correcto, las entidades cuadran con el esquema real.

| # | Escenario | Esperado | Obtenido |
|---|---|---|---|
| 1 | Consultar el perfil antes de configurarlo | 404 | `404 ORGANIZATION_PROFILE_NOT_FOUND` |
| 2 | Configurar el perfil como `ADMIN` | 200 | `200`, nombre con espacios normalizados y correo en minúsculas |
| 3 | Consultar el perfil sin sesión | 200 | `200` |
| 4 | Perfil con el nombre comercial en blanco | 400 | `400 VALIDATION_ERROR`, `details.commercialName` |
| 5 | Crear especialidad | 201 | `201` |
| 6 | Especialidad repetida con otras mayúsculas | 409 | `409 SPECIALTY_ALREADY_EXISTS` |
| 7 | Crear servicio | 201 | `201`, incluye `specialtyName` |
| 8 | Servicio sin nombre, duración ni precio | 400 | `400 VALIDATION_ERROR` con los tres campos en `details` |
| 9 | Servicio con especialidad inexistente | 404 | `404 SPECIALTY_NOT_FOUND` |
| 10 | Servicio repetido en la misma especialidad | 409 | `409 SERVICE_ALREADY_EXISTS` |
| 11 | Duración de 3 minutos y precio negativo | 400 | `400 VALIDATION_ERROR` con ambos campos |
| 12–15 | Catálogo, filtro por especialidad, servicio por id y especialidades, sin sesión | 200 | `200` |
| 16 | Borrar una especialidad con servicios | 409 | `409 SPECIALTY_HAS_SERVICES` |
| 17 | Crear servicio sin sesión | Rechazo | `403` (ver pendientes) |
| 18 | Crear servicio con token de paciente | 403 | `403` |
| 19 | Editar servicio como `ADMIN` | 200 | `200`; la `description` omitida queda en `null`, porque `PUT` reemplaza el recurso completo |
| 20 | Borrar el servicio y después la especialidad ya vacía | 204 | `204` y `204` |

---

## Pendientes conocidos

- **Las respuestas de seguridad no usan el envoltorio uniforme, y una petición sin sesión recibe 403
  en vez de 401.** Sin un `AuthenticationEntryPoint` configurado, Spring Security responde 403 con
  cuerpo vacío en ambos casos. Distinguir 401 de 403 y darles envoltorio es el alcance de la HU-21.
- **Un JSON malformado o un UUID inválido en la ruta** devuelven el 400 por defecto de Spring, sin
  envoltorio.
- **Borrado físico de servicios.** Cuando exista la épica de reservas habrá que pasar a borrado
  lógico, para no perder servicios que tengan citas asociadas.
- **`README_BASE_DE_DATOS.md`** todavía describe solo `app_user`.
