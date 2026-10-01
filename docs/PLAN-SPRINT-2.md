# Plan del Sprint 2

- **Periodo:** 30/09/2026 al 21/10/2026
- **Fecha del plan:** 2026-09-28
- **Equipo:** David, Sofia, Miguel, Elena
- **Alcance:** las 8 HU pendientes del sprint: HU-04, HU-07, HU-08, HU-09, HU-10, HU-17, HU-18 y HU-19

## 1. Decisiones tomadas

| # | Decisión | Motivo |
|---|---|---|
| D1 | David toma HU-04 y HU-17 y las entrega a más tardar el **02/10**. | Son fundacionales: HU-04 desbloquea la cadena de reservas y HU-17 protege todos los endpoints nuevos. |
| D2 | Al resto del equipo se le da el máximo tiempo que permite el sprint: código congelado el **19/10**. | Se deja el 20/10 para QA y el 21/10 para cierre. |
| D3 | Cada HU se entrega solo con PR aprobado, tests y documentación actualizada (sección 6). | Evita que el margen del 20/10 se gaste corrigiendo lo que ya debía estar listo. |
| D4 | Cada persona tiene un rango propio de versiones Flyway (sección 5). | Evita colisiones de números de migración entre ramas paralelas. |
| D5 | Los contratos entre módulos se publican antes de terminar la implementación (sección 4). | Permite trabajar en paralelo sin esperar a que el módulo del otro esté cerrado. |

## 2. Asignación

| Persona | HU | Módulo |
|---|---|---|
| **David** | HU-04 Definición de horarios de atención semanales | `schedule` |
| **David** | HU-17 Asignación de roles y permisos (RBAC) | `auth` |
| **Sofia** | HU-07 Consulta de disponibilidad de agenda | `availability` |
| **Sofia** | HU-18 Autenticación multifactor (MFA) para accesos administrativos | `auth` |
| **Miguel** | HU-08 Creación y agendamiento de reserva | `booking` |
| **Miguel** | HU-09 Cancelación autónoma de reservas | `booking` |
| **Elena** | HU-19 Registro de auditoría de acciones críticas | `audit` |
| **Elena** | HU-10 Confirmación de reserva por correo electrónico | `notification` |

Criterio de reparto: David toma lo que desbloquea a los demás; Sofia conoce el módulo de profesionales (HU-22); Miguel hizo duración de servicios y recursos limitados (HU-06, HU-11), base para calcular y bloquear franjas; Elena toma dos módulos independientes que reaccionan a eventos de reserva.

## 3. Calendario

Las fechas son la entrega (merge a `main`).

| Persona | HU | Empieza | Entrega |
|---|---|---|---|
| David | HU-04 | 30/09 | **Jue 01/10** |
| David | HU-17 | 01/10 | **Vie 02/10** |
| Sofia | HU-07 | 02/10 | **Vie 09/10** |
| Sofia | HU-18 | 09/10 | **Vie 16/10** |
| Miguel | HU-08 | 30/09 | **Mié 14/10** |
| Miguel | HU-09 | 14/10 | **Lun 19/10** |
| Elena | HU-19 | 30/09 | **Vie 09/10** |
| Elena | HU-10 | 14/10 | **Lun 19/10** |

**Fechas comunes**

| Fecha | Hito |
|---|---|
| Mié 30/09 | Inicio del sprint. David publica el contrato de HU-04. |
| Jue 01/10 | Miguel publica el evento y los estados de la reserva. Se acuerda la matriz de roles. |
| Vie 02/10 | David entrega HU-04 y HU-17. |
| Lun 19/10 | **Código congelado en `main`.** |
| Mar 20/10 | Integración, QA, `README_QA.md` y `openapi.yaml`. |
| Mié 21/10 | Cierre del sprint y de los work items en Azure. |

## 4. Dependencias y contratos

```
HU-04 (David) ──► HU-07 (Sofia) ──► HU-08 (Miguel) ──► HU-09 (Miguel)
                                          │
                                          ├──► HU-10 (Elena)
                                          └──► HU-19 (Elena)  ◄── HU-17 (David)
HU-17 (David) ──► HU-18 (Sofia)
```

| Contrato | Quién lo publica | Para | Fecha |
|---|---|---|---|
| Modelo y endpoint de horarios semanales | David | Sofia, Miguel | 30/09 |
| Matriz de roles y permisos (ADMIN, PROFESSIONAL, CUSTOMER) | David | Todos | 01/10 |
| Evento de reserva (`BookingCreated`, `BookingRescheduled`, `BookingCancelled`) y estados | Miguel | Elena | 01/10 |
| Consulta de disponibilidad (entrada y salida) | Sofia | Miguel | 02/10 |

Puntos justos: HU-09 y HU-10 terminan el 19/10 y dependen de HU-08. Por eso el evento y los estados de la reserva se publican el 01/10, aunque HU-08 no esté terminada. Si HU-04 se pasa del 01/10, se mueve toda la cadena; por eso el contrato sale el 30/09.

## 5. Flyway en el Sprint 2

**Estado actual:** las migraciones llegan hasta `V6__create_professionals.sql`. Producción usa `spring.flyway.baseline-on-migrate=true` y `ddl-auto=validate`. En pruebas Flyway está deshabilitado y el esquema lo genera Hibernate con `create-drop` sobre H2.

### Rangos por persona

| Persona | Rango | Migraciones previstas |
|---|---|---|
| David | `V10` a `V19` | `V10` horarios semanales (HU-04), `V11` roles y permisos (HU-17) |
| Sofia | `V20` a `V29` | `V20` factor MFA (HU-18). HU-07 no debería necesitar tabla nueva; si necesita un índice, `V21`. |
| Miguel | `V30` a `V39` | `V30` reservas (HU-08), `V31` cancelación (HU-09) |
| Elena | `V40` a `V49` | `V40` bitácora de auditoría inmutable (HU-19), `V41` registro de correos (HU-10) |

`V7` a `V9` quedan libres como reserva del equipo para correcciones urgentes.

### Convenciones

- Nombre: `V<número>__<descripcion_en_snake_case>.sql`, por ejemplo `V30__create_bookings.sql`. Igual que las existentes.
- Cabecera de comentario con la HU y la decisión de diseño, como en `V6`.
- Nunca se edita una migración ya mergeada a `main`; se corrige con una migración nueva.
- Las restricciones críticas (únicas, CHECK, claves foráneas) van en la base, no solo en el código (ADR-006). Es especialmente importante para evitar dobles reservas en HU-08.
- Sin `ON DELETE CASCADE` sobre datos de negocio; se deshabilita en lugar de eliminar.

### Riesgo: orden de aplicación

Flyway rechaza por defecto una migración pendiente con versión menor que una ya aplicada. Ejemplo: una base local con `V30` aplicada falla al traer `V20` desde otra rama.

Decisión propuesta: agregar `spring.flyway.out-of-order=true` en el perfil de desarrollo local. Cada persona debe saber que, si una base local falla por este motivo, se recrea con `docker compose down -v` y `docker compose up`. **Esta propiedad no está aplicada todavía; requiere un PR pequeño de David.**

### Validación antes de cada PR

Como las pruebas no ejecutan Flyway, un error en una migración no lo detecta el CI. Antes de abrir el PR:

1. Levantar Postgres con `docker compose up` y arrancar la aplicación para que Flyway aplique la migración y Hibernate valide el esquema (`ddl-auto=validate`).
2. Confirmar que las entidades JPA coinciden con las columnas creadas.

## 6. Criterios de entrega por HU

Una HU se da por entregada cuando cumple todo lo siguiente, como en el Sprint 1:

- Código con arquitectura por capas (`application`, `domain`, `infrastructure`, `presentation`), igual que los módulos existentes.
- Tests unitarios y de controlador (incluida seguridad por rol cuando aplique).
- Migración Flyway dentro del rango propio, validada contra Postgres.
- `docs/openapi.yaml` actualizado con los endpoints nuevos.
- `README_QA.md` actualizado con las pruebas realizadas.
- Documento `docs/HU-XX-*.md` cuando la HU tenga decisiones de diseño relevantes, como en HU-02, HU-06 y HU-20.
- PR revisado y aprobado por otra persona del equipo (`.github/CODEOWNERS`).
- Rama por HU (`feature/hu-04`, etc.) y merge a `main`.

## 7. Trabajo después de la entrega de David (desde el 05/10)

David queda liberado antes que el resto. Su prioridad es:

1. Revisar los PR de los demás.
2. Apoyar a Miguel en HU-08 y HU-09, las más delicadas por concurrencia (ADR-006).
3. Adelantar el `openapi.yaml` y las pruebas de integración de punta a punta para el 20/10.
4. Cubrir cualquier atraso en la cadena crítica.

## 8. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| HU-04 no llega el 01/10 | Se atrasan HU-07, HU-08, HU-09 y HU-10 | Publicar el contrato el 30/09; Sofia y Miguel trabajan contra él |
| HU-08 termina tarde | Elena y Miguel quedan sin margen para HU-10 y HU-09 | Evento y estados publicados el 01/10; David apoya desde el 05/10 |
| Colisión de migraciones | Base local o despliegue rotos | Rangos por persona y `out-of-order` en desarrollo |
| Migración con error que el CI no detecta | Fallo al arrancar en Postgres | Validación manual contra Postgres antes de cada PR |
| Retrasos acumulados al final | El 20/10 se consume en correcciones | HU entregada solo con PR aprobado y tests (D3) |
