# Contexto de implementación — HU-20 y HU-21

> Documento de traspaso para un agente de código. Contiene todo el contexto necesario para
> implementar las dos historias sin conocimiento previo del proyecto. Léelo completo antes de
> escribir código.

---

## 1. Qué estamos construyendo

**Plataforma de Reservas de Servicios** — una API REST backend multi-tenant (SaaS) para gestionar
citas, agendas y recursos físicos limitados en negocios como clínicas, consultorios, salones de
belleza y centros deportivos.

Cada negocio que se registra es una **organización** (tenant). Los datos de una organización jamás
pueden ser visibles ni modificables por otra. Ese aislamiento es el requisito arquitectónico central
del proyecto, no un detalle de seguridad añadido después.

Es un proyecto académico de la Fábrica-Escuela CodeF@ctory UdeA (edición 2026-2, Caso 14), de perfil
avanzado: **solo backend, sin frontend**.

### Stack obligatorio

| Componente | Tecnología |
|---|---|
| Lenguaje / framework | Java 17+ con Spring Boot 3.x |
| Base de datos | PostgreSQL (administrado: Supabase o Neon) |
| Migraciones | Flyway, versionadas en el repositorio |
| Build | Maven |
| Pruebas | JUnit 5, Mockito, Testcontainers, Cucumber |
| Análisis estático | SonarCloud |
| CI/CD | GitHub Actions |
| Despliegue | Docker Compose en Render |

---

## 2. Decisiones de arquitectura vigentes

Estas decisiones ya están tomadas y documentadas como ADR. **No las reabras ni propongas
alternativas**: impleméntalas como están.

### ADR-001 · Monolito modular por dominio

El código se organiza **por dominio de negocio**, no por capa técnica. Cada módulo es autocontenido
y tiene sus propias capas internas con inversión de dependencias.

```
com.codefactory.reservas
├── organization/
│   ├── domain/            entidades y reglas de negocio
│   ├── application/       casos de uso (servicios)
│   ├── infrastructure/    repositorios JPA
│   └── api/               controladores REST y DTOs
├── user/
│   └── (misma estructura)
├── auth/
│   └── (misma estructura)
└── shared/
    ├── error/             manejo uniforme de errores (ADR-004)
    ├── tenant/            TenantContext y TenantFilter (ADR-007)
    └── config/            configuración transversal
```

**Prohibido** crear paquetes `controllers/`, `services/` o `repositories/` en la raíz: eso es
organización por capa técnica y contradice el ADR-001.

Los servicios de aplicación dependen de **interfaces** de repositorio, no de implementaciones
concretas, para que las pruebas unitarias puedan mockear la persistencia sin levantar Spring.

### ADR-002 · Reparto de lógica con la base de datos

Las **reglas de negocio viven en la aplicación** (Java). En PostgreSQL solo van integridad
referencial, restricciones y —más adelante, en otra historia— auditoría por triggers.

No implementes reglas de negocio en procedimientos almacenados.

### ADR-003 · Identidad y sesión

JWT de vida corta (15 minutos) firmado, más refresh token con rotación y lista de revocación en
servidor. RBAC por endpoint. El access token **debe incluir un claim con el identificador de la
organización**; ese claim es lo que consume el TenantFilter de la HU-21.

### ADR-004 · Manejo de errores

Toda respuesta de error devuelve este envoltorio uniforme, con el código HTTP correcto:

```json
{
  "errorCode": "ORGANIZATION_ALREADY_EXISTS",
  "message": "Ya existe una organización registrada con ese NIT.",
  "details": ["nit: 900123456"],
  "traceId": "c8f1a2e4-..."
}
```

Se centraliza en un `@RestControllerAdvice` bajo `shared/error/`. Los logs son JSON estructurado y
se correlacionan con la respuesta por `traceId`. **Nunca** devuelvas trazas de excepción al cliente.

### ADR-005 · Contrato de API

REST con OpenAPI/Swagger, **versión en la ruta**: todos los endpoints cuelgan de `/api/v1/`.
El contrato se publica con springdoc-openapi.

### ADR-006 · Estrategia de pruebas

- **Unitarias**: JUnit 5 + Mockito, sin levantar contexto de Spring ni base de datos.
- **Integración**: Testcontainers con un PostgreSQL real y efímero por ejecución.
  **No uses H2**: no reproduce el comportamiento de PostgreSQL y el aislamiento por RLS es
  justamente una característica específica del motor.
- **Aceptación**: Cucumber, ejecutando el Gherkin de cada historia.

### ADR-007 · Aislamiento bilayer multi-tenant

Defensa en dos niveles, ambos obligatorios:

1. **Capa de aplicación** — un `TenantFilter` que extrae el identificador de organización del JWT
   validado y lo deja disponible durante la petición.
2. **Capa de base de datos** — políticas de Row-Level Security nativas de PostgreSQL sobre toda
   tabla que tenga `tenant_id`.

La razón de las dos capas: si un desarrollador olvida filtrar por organización en una consulta, la
base de datos igual rechaza las filas ajenas. Una sola capa deja el sistema a merced de un descuido.

---

## 3. Restricciones de calidad (bloquean la integración)

El pipeline rechaza el merge si no se cumplen:

| Métrica | Umbral |
|---|---|
| Cobertura de pruebas unitarias | ≥ 65 % |
| Vulnerabilidades críticas o bloqueantes | 0 |
| Complejidad ciclomática por clase | < 50 |
| Deuda técnica | ≤ 2 días de remediación |
| Severidad de issues | Minor o mejor |

---

## 4. Convenciones

**El código va en inglés.** Clases en `PascalCase`, variables y métodos en `camelCase`, paquetes en
minúscula. Esto aplica a nombres de clases, variables, métodos, tablas, columnas y rutas de la API.

La **documentación y los mensajes de error dirigidos al usuario van en español**, porque el producto
es para negocios colombianos.

**Ramas:** `feature/<id-historia>-<descripcion-breve>`, por ejemplo `feature/HU-20-organization-signup`.
Vida máxima de 48 horas. Integración por pull request con revisión por pares y comprobaciones
automáticas. Nunca commits directos a `main`.

**Migraciones:** un archivo Flyway por cambio, versionado, nunca editar una migración ya aplicada.

---

## 5. HU-20 · Auto-registro y aprovisionamiento de Organización

### Historia

Como responsable de una clínica o centro de servicios, quiero registrar mi organización en la
plataforma y obtener una cuenta administradora, para empezar a configurar mis servicios y agendas de
forma aislada de otras organizaciones.

### Alcance

Un endpoint **público** (sin autenticación) que crea, en una sola transacción, la organización y su
usuario administrador inicial.

`POST /api/v1/organizations`

**Petición**
```json
{
  "name": "Clínica Norte",
  "taxId": "900123456",
  "adminEmail": "admin@clinicanorte.co",
  "adminPassword": "…",
  "adminFullName": "María Restrepo"
}
```

**Respuesta 201**
```json
{
  "organizationId": "3f2a...",
  "name": "Clínica Norte",
  "adminUserId": "9b71..."
}
```

### Reglas

- `taxId` (NIT) es único en toda la plataforma. Duplicado → **409** con
  `errorCode: ORGANIZATION_ALREADY_EXISTS`.
- Campos obligatorios: `name`, `taxId`, `adminEmail`, `adminPassword`. Faltante o inválido → **400**
  con el envoltorio del ADR-004 y la lista de campos en `details`.
- La contraseña se almacena con **BCrypt**. Nunca en texto plano, nunca en logs.
- La creación de organización y usuario administrador es **atómica**: si falla una, no queda nada.
- El usuario administrador queda con rol `ORG_ADMIN`.
- La respuesta **no** devuelve la contraseña ni el hash.

### Nota crítica de implementación

Este endpoint es el único que se ejecuta **sin tenant en contexto** —todavía no existe la
organización cuando se llama—. Debe quedar **excluido del TenantFilter** y sus operaciones de
escritura deben poder saltarse las políticas RLS de forma controlada. Resuélvelo con una ruta
explícitamente exenta en la configuración del filtro, no desactivando RLS globalmente.

### Criterios de aceptación

```gherkin
Característica: Auto-registro y aprovisionamiento de organización

  Escenario: Registro exitoso de una organización nueva
    Dado que no existe una organización con el NIT "900123456"
    Cuando envío una solicitud POST a "/api/v1/organizations" con nombre
      "Clínica Norte" y NIT "900123456"
    Entonces recibo un código de estado 201
    Y la respuesta contiene el identificador de la organización creada
    Y queda creado un usuario administrador asociado a esa organización

  Escenario: Rechazo por organización ya registrada
    Dado que existe una organización con el NIT "900123456"
    Cuando envío una solicitud POST a "/api/v1/organizations" con ese NIT
    Entonces recibo un código de estado 409
    Y la respuesta contiene el campo "errorCode" con valor "ORGANIZATION_ALREADY_EXISTS"

  Escenario: Rechazo por datos incompletos
    Cuando envío una solicitud POST a "/api/v1/organizations" sin el campo "taxId"
    Entonces recibo un código de estado 400
    Y la respuesta contiene los campos "errorCode", "message", "details" y "traceId"

  Escenario: La contraseña nunca se almacena en texto plano
    Cuando registro una organización con la contraseña "Secreta123*"
    Entonces el valor almacenado para esa contraseña no es "Secreta123*"
    Y la respuesta no contiene ningún campo con la contraseña
```

---

## 6. HU-21 · Aislamiento lógico de datos inter-tenant

### Historia

Como responsable de una organización, quiero que los datos de mi organización sean inaccesibles
para cualquier otra, para cumplir con la confidencialidad que exige el manejo de datos de pacientes.

### Alcance

Las dos capas del ADR-007, funcionando juntas y demostradas con pruebas.

#### Capa 1 — TenantFilter (aplicación)

Un `OncePerRequestFilter` que, en cada petición autenticada:

1. Lee el claim de organización del JWT ya validado.
2. Lo guarda en un `TenantContext` respaldado por `ThreadLocal`.
3. **Limpia el ThreadLocal en un bloque `finally`.** Sin esto, el hilo del pool conserva el tenant de
   la petición anterior y se filtran datos entre organizaciones. Es el error más peligroso de esta
   historia.

Rutas exentas: `/api/v1/organizations` (registro), `/api/v1/auth/**`, Swagger y health checks.

Define la obtención del tenant detrás de una interfaz (`TenantResolver`) para que la historia HU-03
—que se está construyendo en paralelo— pueda conectar la lectura real del JWT sin tocar el filtro.

#### Capa 2 — Row-Level Security (PostgreSQL)

Para cada tabla con `tenant_id`:

```sql
ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_user FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON app_user
  USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

Tres detalles que deciden si esto funciona o es decorativo:

- **`FORCE ROW LEVEL SECURITY` es obligatorio.** Sin él, el dueño de la tabla ignora las políticas, y
  si la aplicación se conecta con el usuario dueño el aislamiento no existe.
- **El usuario de base de datos de la aplicación no debe ser dueño de las tablas ni tener
  `BYPASSRLS`.** Crea un rol específico con solo `SELECT`, `INSERT`, `UPDATE`, `DELETE`.
- **Usa `SET LOCAL`, no `SET`.** La variable debe fijarse dentro de la transacción:

  ```sql
  SET LOCAL app.tenant_id = '...';
  ```

  `SET LOCAL` se revierte al terminar la transacción. `SET` a secas persiste en la conexión, y con un
  pool esa conexión se reutiliza para la petición de otra organización: fuga de datos garantizada.

La variable se fija al abrir cada transacción, tomando el valor del `TenantContext`. Impleméntalo con
un aspecto sobre `@Transactional` o un `ConnectionPreparer`, no dejándolo al criterio de cada
servicio.

### Criterios de aceptación

```gherkin
Característica: Aislamiento lógico de datos entre organizaciones

  Escenario: Un usuario solo consulta datos de su propia organización
    Dado que existen las organizaciones "Clínica Norte" y "Clínica Sur"
    Y cada una tiene usuarios registrados
    Cuando un usuario autenticado de "Clínica Norte" consulta la lista de usuarios
    Entonces solo recibe los usuarios de "Clínica Norte"

  Escenario: La base de datos bloquea el acceso cruzado aunque la aplicación falle
    Dado que la sesión de base de datos está fijada a la organización "Clínica Norte"
    Cuando se ejecuta una consulta directa sin filtro de organización
    Entonces la política de Row-Level Security devuelve únicamente filas de "Clínica Norte"

  Escenario: Rechazo de escritura sobre otra organización
    Dado que estoy autenticado como usuario de "Clínica Norte"
    Cuando intento modificar un registro que pertenece a "Clínica Sur"
    Entonces la operación es rechazada
    Y ningún dato de "Clínica Sur" resulta alterado

  Escenario: El contexto de organización no sobrevive entre peticiones
    Dado que una petición de "Clínica Norte" terminó de procesarse
    Cuando el mismo hilo atiende una petición de "Clínica Sur"
    Entonces el contexto contiene "Clínica Sur" y no "Clínica Norte"
```

### Prueba que no puede faltar

Una prueba de integración con **Testcontainers** que:

1. Crea dos organizaciones con datos propios.
2. Fija la sesión a la organización A.
3. Ejecuta una consulta **sin filtro de aplicación** (`SELECT * FROM app_user`).
4. Verifica que solo vuelven filas de A.

Esa es la evidencia de que la segunda capa existe de verdad. Si esa prueba pasa con la capa de
aplicación desactivada, el bilayer está bien construido.

---

## 7. Modelo de datos inicial

```
organization
  id              uuid        PK
  name            varchar     NOT NULL
  tax_id          varchar     NOT NULL UNIQUE
  status          varchar     NOT NULL   -- ACTIVE | SUSPENDED
  created_at      timestamptz NOT NULL

app_user
  id              uuid        PK
  tenant_id       uuid        NOT NULL FK → organization(id)
  email           varchar     NOT NULL
  password_hash   varchar     NOT NULL
  full_name       varchar     NOT NULL
  role            varchar     NOT NULL   -- ORG_ADMIN | PROFESSIONAL | PATIENT
  failed_attempts int         NOT NULL DEFAULT 0
  locked_until    timestamptz NULL
  created_at      timestamptz NOT NULL
  UNIQUE (tenant_id, email)
```

Notas: `organization` no lleva `tenant_id` porque **es** el tenant; su aislamiento se maneja por
autorización, no por RLS. El índice único de `app_user` es compuesto: el mismo correo puede existir
en dos organizaciones distintas, porque son negocios sin relación entre sí.

---

## 8. Fuera de alcance

No implementes nada de esto, aunque parezca natural hacerlo:

- MFA (es la HU-18, otro sprint).
- CRUD de roles y permisos (es la HU-17). Basta el campo `role` y `@PreAuthorize`.
- Triggers de auditoría (es la HU-19, Sprint 3).
- Reservas, agendas, servicios, profesionales, reportes (otras épicas).
- Recuperación de contraseña, verificación de correo, invitaciones.
- Caché, mensajería, Kubernetes.

Si detectas que algo de la lista es necesario para que las dos historias funcionen, **dilo antes de
implementarlo** en vez de ampliar el alcance por tu cuenta.

---

## 9. Definición de terminado

Una historia está terminada cuando cumple **todo** esto:

- [ ] Los criterios de aceptación en Gherkin pasan automatizados.
- [ ] Código revisado e integrado mediante pull request con pipeline en verde.
- [ ] Pruebas unitarias, de integración y de aceptación ejecutadas, con evidencia.
- [ ] Quality Gate de SonarCloud cumplido (cobertura ≥ 65 %, 0 vulnerabilidades críticas).
- [ ] Contrato OpenAPI actualizado y publicado.
- [ ] Migraciones Flyway versionadas y aplicables desde cero.
- [ ] Despliegue comprobado en Render, con logs disponibles.
- [ ] Sin defectos críticos ni vulnerabilidades críticas abiertas.

---

## 10. Orden sugerido de trabajo

1. Proyecto base Spring Boot con la estructura de paquetes del ADR-001.
2. Migración Flyway inicial: `organization` y `app_user`.
3. Manejo uniforme de errores del ADR-004 (`shared/error/`) — lo necesitan las dos historias.
4. **HU-20** completa, con sus pruebas.
5. `TenantContext` y `TenantFilter` (capa 1 de HU-21).
6. Políticas RLS y fijación de `app.tenant_id` por transacción (capa 2 de HU-21).
7. Prueba de integración con Testcontainers que demuestra el aislamiento en la capa de base de datos.
8. Contrato OpenAPI y verificación del despliegue.

---

## 11. Qué preguntar antes de decidir por tu cuenta

Levanta la mano en vez de asumir si te encuentras con esto:

- El claim exacto del JWT que lleva el tenant, si HU-03 todavía no está mergeada.
- Si el proyecto ya tiene repositorio y estructura creada, para no duplicarla.
- Credenciales y URL de la base de datos de desarrollo.
- Si el rol de base de datos sin privilegios ya fue creado en el entorno administrado, porque en
  algunos proveedores eso requiere permisos que el equipo quizá no tenga.
