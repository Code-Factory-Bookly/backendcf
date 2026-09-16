# HU-20 · Configuración inicial de la plataforma

Resumen de los cambios de esta rama, para acompañar el pull request.

---

## Qué resuelve

La plataforma es única (sin multi-tenancy). Este endpoint la aprovisiona por primera y única vez:
crea la plataforma y su usuario administrador en una sola transacción, y a partir de ahí rechaza
cualquier intento posterior.

> **Historial de alcance:** la primera versión de esta rama modelaba una "organización" con razón
> social y NIT (`taxId`), pensada para un escenario multi-tenant que luego se descartó. El work item
> de Azure DevOps la redefinió como "plataforma": sin identidad legal, solo un nombre. Esta versión
> implementa esa redefinición.

---

## Contrato del endpoint

### `POST /api/v1/platform/setup` — público, de un solo uso

```json
{
  "name": "Bookly Platform",
  "adminEmail": "admin@bookly.co",
  "adminPassword": "Secreta123*",
  "adminFullName": "María Restrepo"
}
```

**201 Created**

```json
{
  "platformId": "e16f745c-...",
  "name": "Bookly Platform",
  "adminUserId": "09f9b9b5-..."
}
```

La respuesta no incluye la contraseña ni su hash.

| Código | `errorCode` | Cuándo |
|---|---|---|
| 201 | — | Aprovisionamiento exitoso |
| 400 | `VALIDATION_ERROR` | Falta un campo obligatorio o no cumple el formato |
| 409 | `PLATFORM_ALREADY_CONFIGURED` | Ya hay una plataforma configurada: el aprovisionamiento es irrepetible |

> ⚠️ El endpoint queda público y sin autenticar, porque es la puerta de entrada inicial. Una vez
> configurada la plataforma responde 409 a todo; conviene cerrarlo por red o configuración en el
> despliegue.

---

## Migraciones

| Migración | Qué hace |
|---|---|
| `V1__baseline_app_user.sql` | Línea base de `app_user` |
| `V2__add_login_security.sql` | Bloqueo de cuenta de HU-03 |
| `V3__create_platform.sql` | Tabla `platform`: `id`, `name`, `status`, `created_at`. Un índice único sobre una expresión constante (`((true))`) permite exactamente una fila |

Sin `tax_id`, sin índice de unicidad sobre el nombre: con una sola fila garantizada por el índice
`uk_platform_singleton`, no hay nada más contra qué comparar.

---

## Decisiones tomadas

- **La unicidad la sostiene la base, no solo el servicio.** `PlatformSetupService` comprueba
  `platformRepository.count() > 0`, pero entre esa lectura y la escritura caben dos peticiones
  simultáneas. El índice `uk_platform_singleton` hace que la segunda falle en la base pase lo que
  pase.
- **Rutas en inglés.** A diferencia de HU-02, donde Arquitectura-BD pidió rutas en español, el AC de
  esta historia especifica `POST /api/v1/platform/setup` en inglés — se respetó tal cual, alineado
  además con la convención base del proyecto (código y rutas en inglés, mensajes en español).
- **Correo de bienvenida como puerto con adaptador de log**, sin cambios de fondo respecto a la
  versión anterior: `WelcomeNotificationPort`/`LoggingWelcomeNotificationAdapter`, solo renombrados
  de "organización" a "plataforma".
- **Rol del administrador: `ADMIN`**, heredado de HU-03.
- Se corrigió un mensaje residual del multi-tenant descartado: `EMAIL_ALREADY_REGISTERED` decía "El
  correo ya está registrado **en esta organización**", que ya no tenía sentido sin el concepto de
  organización. Ahora dice solo "El correo ya está registrado".
- Se quitó del comentario en `SecurityConfiguration` la referencia a un `TenantFilter`/HU-21 de
  aislamiento inter-tenant: esa historia quedó cancelada cuando se decidió que la plataforma no
  sería multi-tenant.

---

## Cómo probarlo

```bash
docker compose down -v && docker compose up --build
```

En el arranque debe verse `Successfully applied 3 migrations, now at version v3`.

```bash
curl -X POST localhost:8080/api/v1/platform/setup -H 'Content-Type: application/json' \
  -d '{"name":"Bookly Platform","adminEmail":"admin@bookly.co",
       "adminPassword":"Secreta123*","adminFullName":"María Restrepo"}'

# 409 PLATFORM_ALREADY_CONFIGURED — repetir con datos distintos
# 400 VALIDATION_ERROR — repetir omitiendo "name"
```

```sql
SELECT email, role, password_hash FROM app_user;
-- password_hash debe empezar por $2a$ y el rol del administrador ser ADMIN
```

---

## Verificación ejecutada

Contra PostgreSQL 16.10 real, no H2:

- `mvnw clean test`: **BUILD SUCCESS**, 4 pruebas, 0 fallos.
- Flyway V1→V3 desde base vacía: `Successfully applied 3 migrations, now at version v3`.
- Tabla `platform` sin `tax_id`, con `uk_platform_singleton` como única restricción de unicidad.
- Los cuatro escenarios del AC dieron el código esperado: 201 con `platformId`/`adminUserId`, 409
  `PLATFORM_ALREADY_CONFIGURED` en el segundo intento, 400 con el envoltorio completo al omitir
  `name`, y hash BCrypt en base (nunca la contraseña en claro).
- La ruta vieja `/api/v1/organizations` ya no existe: cae en `anyRequest().authenticated()` y
  responde 403 sin token, confirmando que el rename reemplazó la ruta en vez de dejarla duplicada.

---

## Pendientes conocidos

- **OpenAPI/Swagger** y **CI con Quality Gate** siguen fuera de esta rama.
- **Cerrar el endpoint tras el aprovisionamiento**, por red o configuración en el despliegue.
- **Colación de la base de despliegue**: aunque esta historia ya no depende de `lower(name)`, otras
  tablas del proyecto sí (`servicios` en HU-02); debe usarse UTF-8 o ICU, no `C`.
