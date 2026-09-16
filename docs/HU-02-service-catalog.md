# HU-02 · Catálogo de servicios

Resumen de los cambios de esta rama, para acompañar el pull request.

---

## Qué resuelve

El administrador registra y consulta el **catálogo de servicios** de la plataforma: nombre,
categoría, descripción, duración y precio. Cualquier visitante puede consultarlo sin iniciar sesión;
solo un usuario con rol `ADMIN` puede crear, editar o eliminar servicios.

> **Historial de alcance:** la primera versión de esta rama incluía un módulo de perfil de
> organización y una entidad de especialidad separada, con endpoints propios. Arquitectura-BD
> (Elena Vargas) redefinió el alcance en el work item de Azure DevOps: *"la plataforma no requiere
> un módulo de perfil de organización ni sobreingeniería de esquemas multitenant... Dejar esta HU
> acotada únicamente a los servicios simplifica el modelo de datos... y evita endpoints innecesarios
> de configuración corporativa"*. Esta versión implementa esa redefinición: sin perfil, y con la
> categoría como un campo del servicio en vez de un recurso propio con su propio CRUD.

---

## Endpoints

| Método | Ruta | Acceso | Respuestas |
|---|---|---|---|
| `GET` | `/api/v1/servicios` | Público | `200` (acepta `?categoria=` como filtro opcional) |
| `GET` | `/api/v1/servicios/{id}` | Público | `200` · `404 SERVICE_NOT_FOUND` |
| `POST` | `/api/v1/servicios` | `ADMIN` | `201` · `409 SERVICE_ALREADY_EXISTS` |
| `PUT` | `/api/v1/servicios/{id}` | `ADMIN` | `200` · `404` · `409` |
| `DELETE` | `/api/v1/servicios/{id}` | `ADMIN` | `204` · `404` |

Cualquier campo obligatorio vacío o inválido responde `400 VALIDATION_ERROR` con el envoltorio
uniforme (`errorCode`, `message`, `details`, `traceId`, `timestamp`).

```json
POST /api/v1/servicios
{
  "name": "Instalación de brackets",
  "description": "Brackets metálicos convencionales",
  "category": "Ortodoncia",
  "durationMinutes": 90,
  "price": 1800000.00
}
```

Obligatorios: `name`, `category`, `durationMinutes` (entero positivo, hasta 480) y `price`
(estrictamente mayor que cero, hasta 2 decimales). `description` es opcional —ni el Gherkin de la
historia ni el de Arquitectura-BD la incluyen entre los campos que se rechazan por faltar.

El campo `status` solo se usa al **actualizar**: en la creación el sistema siempre asigna `ACTIVO`,
ignorando cualquier valor que se envíe. Un `PUT` con `"status": "INACTIVO"` desactiva el servicio sin
borrarlo.

---

## Esquema

`docker/postgres/init/003-create-servicios.sql`, una sola tabla:

| Columna | Regla |
|---|---|
| `nombre` | Obligatorio, único sin distinguir mayúsculas |
| `categoria` | Obligatorio |
| `duracion_minutos` | `> 0` y `<= 480` (CHECK en la base, no solo en la aplicación) |
| `precio` | `> 0` (CHECK en la base) |
| `estado` | `ACTIVO` \| `INACTIVO`, por defecto `ACTIVO` |

> ⚠️ Los scripts de `docker/postgres/init` solo se ejecutan al crear el volumen. Sobre un volumen
> existente hay que aplicarlo a mano:
> ```bash
> docker compose exec db psql -U backendcf -d backendcf \
>   -f /docker-entrypoint-initdb.d/003-create-servicios.sql
> ```

---

## Decisiones tomadas frente al comentario de revisión

| Pedido | Resuelto cómo |
|---|---|
| Eliminar el perfil de organización | Se eliminó el módulo `organization` completo: entidad, repositorio, servicio, controlador, DTOs, rutas y la tabla `organization_profile` |
| Rutas en `/api/v1/servicios` | Hecho. También se tradujo el query param de filtro a `categoria` |
| Campo de estado activo | `ServiceStatus { ACTIVO, INACTIVO }`. Lo asigna el sistema al crear; se puede cambiar al actualizar |
| Precio estrictamente positivo | `@DecimalMin(value = "0.0", inclusive = false)` en la API y `CHECK (precio > 0)` en la base — verificado que la base lo rechaza aunque se salte la aplicación |
| Confirmar si la descripción es obligatoria | No lo es: ni el Gherkin original ni la propuesta de Arquitectura-BD la incluyen entre los campos que se validan como faltantes |
| Pruebas del CRUD | No incluidas en este commit. El tablero tiene una tarea separada, **"77 · \[Arquitectura-BD\] Probar escenarios de aceptación"**, distinta de la implementación |

También se eliminó la especialidad como recurso independiente (`Specialty`, su repositorio, servicio,
controlador y endpoints `/api/v1/specialties`): el Gherkin de Arquitectura-BD trata la categoría como
un campo del servicio (`nombre, categoría, duración, precio`), no como una entidad con su propio
ciclo de vida. Si más adelante se necesita gestionar categorías de forma independiente (renombrarlas,
fusionarlas), es un cambio localizado a esta tabla.

La duración se mantiene acotada a 480 minutos (8 horas) como cota superior de sentido común; ninguna
de las dos propuestas la pone en duda y evita valores absurdos sin contradecir "debe ser positiva".

---

## Cómo probarlo

```bash
docker compose down -v && docker compose up --build
```

Crear un administrador (en `main` no hay endpoint para eso todavía):

```bash
curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"admin@clinicanorte.co","password":"Secreta123*","fullName":"María Restrepo"}'

docker compose exec db psql -U backendcf -d backendcf \
  -c "UPDATE app_user SET role = 'ADMIN' WHERE email = 'admin@clinicanorte.co';"
```

Luego iniciar sesión en `/api/v1/auth/login` y usar el token en los endpoints de `/api/v1/servicios`.

---

## Verificación ejecutada

Contra PostgreSQL 16.10 real, no H2:

- `mvnw clean test`: **BUILD SUCCESS**, 4 pruebas, 0 fallos.
- Arranque con `ddl-auto=validate`: correcto.
- Solo dos tablas resultantes: `app_user` y `servicios` — ninguna tabla de perfil ni de especialidad.

| # | Escenario | Esperado | Obtenido |
|---|---|---|---|
| 1 | Crear con nombre, categoría, duración y precio (Gherkin de Arquitectura-BD) | 201, `estado` `ACTIVO` | `201`, `"status":"ACTIVO"` |
| 2 | Faltan nombre, duración o precio | 400 | `400 VALIDATION_ERROR` con los tres campos |
| 3 | Duración = 0 | Rechazado | `400`, "La duración debe ser un valor positivo" |
| 4 | Precio = 0 | Rechazado | `400`, "El precio debe ser un valor positivo" |
| 5 | Precio negativo | Rechazado | `400` |
| 6 | Nombre duplicado (otras mayúsculas) | 409 | `409 SERVICE_ALREADY_EXISTS` |
| 7–9 | Consultar catálogo, un servicio y filtrar por categoría, sin sesión | 200 | `200` |
| 10 | Crear sin sesión | Rechazado | `403` |
| 11 | Crear con token de paciente | 403 | `403` |
| 12–13 | Desactivar (`PUT status=INACTIVO`) y verlo reflejado en el `GET` | 200 | `200`, `"status":"INACTIVO"` |
| 14 | Eliminar | 204 | `204` |
| — | `INSERT` directo en la base con precio 0, sin pasar por la app | Rechazado | `ERROR: violates check constraint "ck_servicios_precio"` |

---

## Pendientes conocidos

- **Las respuestas de seguridad no usan el envoltorio uniforme, y una petición sin sesión recibe 403
  en vez de 401.** Sin un `AuthenticationEntryPoint` configurado, Spring Security responde 403 con
  cuerpo vacío en ambos casos. Corresponde al alcance de la HU-21 (control de acceso).
- **`README_BASE_DE_DATOS.md`** todavía describe solo `app_user`.
