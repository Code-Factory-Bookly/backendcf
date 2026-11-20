# Sprint 3 — Debugging Sistemático de los 11 Bugs

**Fecha:** 2026-11-20
**Rama:** `qa/sprint3-integracion`
**Repositorio real:** `/home/mista/Escritorio/udea/2026-II/calidad-software/Proyecto-FE/backendcf` (directorio `backendcf`)
**Método:** 4 pasos obligatorios por bug — Hipótesis → Predicción → Experimento → Veredicto.
**Regla de oro aplicada:** si el único camino de reproducción es SQL que la API rechaza, el bug se REFORMULA o RECLASIFICA.

---

## Entorno de medición

| Elemento | Valor |
|---|---|
| App | `java -jar target/backendcf-0.0.1-SNAPSHOT.jar` en `:8080` |
| BD | `postgres:16-alpine` contenedor `backendcf-db`, host `:55432`, base `bookly_test` |
| Roles | `bookly_test` (escritura) |
| Token | JWT HS256 firmado con `JWT_SECRET` del `.env`; `sub` = UUID de `app_user` |
| Nota crítica | `JwtAuthenticationFilter` **ignora el `role` del token** y usa el rol de la BD (`JwtAuthenticationFilter.java:45`). Un token con rol falsificado no eleva privilegios. |
| Semilla | `bookly_test` pre-cargada (88 CONFIRMADA, 1 CANCELADA, 3 COMPLETED, 16 franjas, 0 bloqueos) |

**Control de baseline ejecutado antes de cada experimento:** el reporte arrancaba en `overall=37.72`, `MONDAY 780/3120`, `SATURDAY 870/0`. Tras todos los experimentos la BD quedó **exactamente en ese estado** (`git status` sin cambios trackeados).

---

## S3-01 — La concurrencia al bloquear la agenda devuelve 401 en vez de 409 AGENDA_BLOCK_OVERLAP

**Hipótesis:** `AgendaBlockService.create()` captura `DataIntegrityViolationException` y, dentro del `catch`, consulta el solapamiento sobre una transacción ya abortada → `JpaSystemException` → la petición cae en `/error` y el filtro de seguridad responde 401 en lugar de 409.

**Predicción:**
- Si el bug es real: el perdedor de la carrera **nunca** recibe 409 con `AGENDA_BLOCK_OVERLAP`; recibe 401 (o 500). El log del servidor debe mostrar la excepción técnica.
- Si es falso: el perdedor recibe 409 `AGENDA_BLOCK_OVERLAP` de forma estable, con la transacción aún viva.

**Experimento:**

```bash
# Secuencial (control): 201 y luego 409
POST /api/v1/horarios/{sofia}/bloqueos  {startTime:"2026-11-23T09:00:00",endTime:"2026-11-23T12:00:00"}
→ 201 {"id":"0670fccf-..."}
POST (mismo rango)
→ 409 {"errorCode":"AGENDA_BLOCK_OVERLAP","details":{"0670fccf-...":"lun 23/11 09:00–12:00"},"traceId":"1ce5f448-..."}

# Concurrente: 10 rondas x 2 POST simultáneos, mismo rango
→ round 1: 201 401 | round 2: 201 401 | round 3: 401 201 | round 4: 201 401
  round 5: 201 409 | round 6: 401 201 | round 7: 409 201 | round 8: 201 401
  round 9: 401 201 | round 10: 201 401
DISTribución (20 peticiones):  HTTP 201: 10 | HTTP 401: 8 | HTTP 409: 2
```

Log del servidor (`/tmp/opencode/app.log`):

```
WARN  org.hibernate.orm.jdbc.error : HHH000247: ErrorCode: 0, SQLState: 25P02
WARN  org.hibernate.orm.jdbc.error : ERROR: current transaction is aborted, commands ignored until end of transaction block
ERROR o.a.c.c.C.[dispatcherServlet] : Servlet.service() ... threw exception
  [Request processing failed: org.springframework.orm.jpa.JpaSystemException: could not execute statement
   [ERROR: current transaction is aborted...] [insert into bloqueo_agenda ...]]
   at ...AgendaBlockService.create(AgendaBlockService.java:65)
   at ...DispatcherServlet.doDispatch(DispatcherServlet.java:963)
   at ...ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
```

**Veredicto:** **CONFIRMADO** — pero la afirmación original era **parcialmente incorrecta**. Hay **dos** caminos de fallo, no uno:

| SQLState | Excepción | Origen | ¿Cubierta por el `catch`? |
|---|---|---|---|
| `25P02` | `JpaSystemException` | EXCLUDE viola → la transacción queda abortada → el `findOverlapping()` del `catch` (línea 68) consulta sobre la tx muerta | Sí entra en el `catch`, pero la consulta **falla** por tx abortada |
| `40P01` | `CannotAcquireLockException` | **Deadlock** de PostgreSQL al evaluar la restricción de exclusión | **NO** — `CannotAcquireLockException` extiende `PessimisticLockingFailureException` → `ConcurrencyFailureException` → `TransientDataAccessException`. **No** es `DataIntegrityViolationException` |

Verificación de la jerarquía de clases (`javap` sobre `spring-tx-7.0.8.jar`):

```
public class org.springframework.dao.CannotAcquireLockException
    extends org.springframework.dao.PessimisticLockingFailureException { }
public class org.springframework.dao.DataIntegrityViolationException
    extends org.springframework.dao.NonTransientDataAccessException { }
```

**Experimento de nivel de servicio** (JUnit temporal, eliminado tras la medición) — invocar `AgendaBlockService.create()` desde dos hilos con `CountDownLatch`, 10 rondas:

```
### DIAG RONDA 1 -> [DEADLOCK_40P01] [OK]
### DIAG RONDA 2 -> [OK] [DEADLOCK_40P01]
### DIAG RONDA 3 -> [OTRO_org.springframework.orm.jpa.JpaSystemException_root=org.postgresql.util.PSQLException] [OK]
### DIAG RONDA 4 -> [OK] [OTRO_...JpaSystemException...]
...
### DIAG TOTAL: bloques=10 conflictosNegocio=0 deadlocks=6 otros=4
```

**Resultado: 10/10 rondas el perdedor lanzó una excepción técnica. Cero conflictos de negocio.** → **el servicio también falla**, no solo la capa HTTP.

**Experimento de control decisive** — el mecanismo del 401:
```
GET /api/v1/reservas/noexiste/xyz   (ruta sin handler, endpoint protegido)
→ 401 {"errorCode":"UNAUTHORIZED","message":"Se requiere autenticación"}
```
Y `grep` sobre `GlobalExceptionHandler`: **no existe ningún `@ExceptionHandler(Exception.class)` / `(RuntimeException.class)` / `(Throwable.class)`**, y `CannotAcquireLockException` / `JpaSystemException` **no están en la lista**. La excepción escapa del `DispatcherServlet.doDispatch` y llega a `ExceptionTranslationFilter`, que responde con el `authenticationEntryPoint` → **401**.

**Camino de reproducción real:** **API** (totalmente alcanzable por el usuario).

**Defecto real reformulado:** *Dos defectos de código, no uno.*
1. `AgendaBlockService.java:63-69` — el `catch (DataIntegrityViolationException)` ejecuta `repository.findOverlapping(...)` **sobre la transacción ya abortada** (SQLState `25P02`); la consulta de recuperación nunca puede devolver datos y agrava el error en lugar deTranslate.
2. `AgendaBlockService.java:63-69` — **falta el `catch (CannotAcquireLockException)`** que sí existe en `CreateBookingService.java:128`. El deadlock (`40P01`) es el camino **más frecuente** (6/10).
3. `GlobalExceptionHandler` — no tiene manejador genérico; cualquier excepción no modelada se convierte en **401 falso**, que es un defecto **transversal** (afecta a S3-01 y al bug nuevo S3-12).

**Severidad real:** CRÍTICA — pero **menor** de lo reportado: el 401 es un **síntoma**; la pérdida de datos NO ocurre (la restricción de exclusión de la BD garantiza la integridad). El defecto real es "el servicio lanza excepciones técnicas en vez de traducirlas al código de negocio", y el 401 es un efecto secundario de la ausencia de un manejador genérico.

**Acción:**
- `AgendaBlockService`: añadir `catch (CannotAcquireLockException e)` → `overlapsBlock(...)`; **sacar la consulta `findOverlapping` de dentro del `catch` aborted** (usar `REQUIRES_NEW` en un servicio separado o releer en una nueva transacción).
- `GlobalExceptionHandler`: añadir `@ExceptionHandler(Exception.class)` que devuelva **500** con `ApiErrorResponse` (nunca 401) — esto elimina la clase de bugs "401 falso".
- Patrón de referencia ya existente en el repo: `CreateBookingService.java:121-134`.

---

## S3-02 — El reporte de ocupación ignora los bloqueos de agenda

**Hipótesis:** `capacityMinutes` no baja tras crear un bloqueo de agenda.

**Predicción:**
- Si es real: el `capacityMinutes` del profesional y del desglose por día son **idénticos** antes y después del bloqueo; la disponibilidad sí deja de ofrecer esas horas.
- Si es falso: la capacidad baja exactamente los minutos del bloqueo.

**Experimento** (profesional **sin reservas**: Elena Vargas, 0 reservas — el enunciado exigía esto):

```bash
# BEFORE
GET /api/v1/reportes/ocupacion  → Elena: booked=0 capacity=5280 pct=0.0
                                  FRIDAY: booked=840 capacity=3000

# Crear bloqueo de 4h DENTRO de la ventana del reporte, POR API
POST /api/v1/horarios/{elena}/bloqueos {"startTime":"2026-11-20T14:00:00","endTime":"2026-11-20T18:00:00"}
→ 201 {"id":"10936d40-e55e-47de-b669-94f32f9938ba",...}

# AFTER
GET /api/v1/reportes/ocupacion  → Elena: booked=0 capacity=5280 pct=0.0   ← DELTA = 0
                                  FRIDAY: booked=840 capacity=3000        ← DELTA = 0
```

**Contradicción demostrada en el mismo instante:**
```bash
GET /api/v1/disponibilidad?profesionalId={elena}&servicioId={60min}&desde=2026-11-20&hasta=2026-11-20
→ 200 {"available": false, "message": "El profesional no tiene horarios libres...", "days": []}
```
La disponibilidad dice **0 huecos**; el reporte dice **3000 min de capacidad** para el mismo viernes.

**Causa raíz (código):** `OccupancyQuery` (`OccupancyQuery.java:12-25`) expone únicamente `findProfessionalIds`, `findWeeklyWindows`, `findOccupyingBookings`, `professionalNames`. **No hay ningún método que exponga los bloqueos.** `OccupancyQueryAdapter` solo inyecta `ProfessionalRepository`, `WeeklySlotRepository`, `BookingRepository`, `UserAccountRepository` — **`AgendaBlockRepository` nunca se inyecta**. Y en contraste, `AvailabilityQueryAdapter.java:77-78` **sí** incluye los bloqueos como `BusyPeriod`.

**Camino de reproducción real:** **API**. El bloqueo se creó por API (201). La única sutileza: la API **rechaza bloqueos en el pasado** (`400 SCHEDULE_VALIDATION_ERROR: "No se puede bloquear un horario que ya pasó"`), por lo que el bloqueo debe ser **hoy** (o mañana, fuera de la ventana). Esto **sí** es alcanzable: hoy pertenece a la ventana del reporte `[hoy-29, hoy]`.

**Defecto real reformulado:** *El puerto de lectura del reporte (`OccupancyQuery`) no expone los bloqueos de agenda, y el adaptador no inyecta `AgendaBlockRepository`. Consecuencia: la capacidad del reporte es sistemáticamente **superior** a la disponibilidad real del profesional, y el mismo rango temporal tiene dos definiciones de "ocupado" que se contradicen.*

**Severidad real:** CRÍTICA (sin cambios) — es la contradicción más grave del sprint: el mismo intervalo es a la vez "libre" (disponibilidad) y "capacidad" (reporte), y el número que consume el administrador para decidir es incorrecto.

**Acción:**
- Añadir `List<BlockedPeriod> findActiveBlocks(LocalDateTime from, LocalDateTime to)` a `OccupancyQuery`; inyectar `AgendaBlockRepository` en `OccupancyQueryAdapter`.
- Restar los minutos bloqueados de `capacityByProfessional` y `capacityByDay` en `OccupancyCalculator`.
- **Restricción de ventana a documentar:** como la API no permite bloquear el pasado, el reporte solo puede descontar bloqueos **futuros o de hoy**. El hueco (bloqueos históricos ya consumidos) permanece. Definir explícitamente si el reporte debe ignorar o imputar los bloqueos pasados.

---

## S3-03 — Dos definiciones incompatibles de "ocupado"

**Hipótesis:** una reserva COMPLETED desaparece de la disponibilidad pero cuenta en el reporte.

**Predicción:**
- Si es real (para estados alcanzables): existiría un estado alcanzable por la API en el que disponibilidad y reporte discrepen.
- Si es falso (para estados alcanzables): para CONFIRMADA y CANCELADA (los únicos alcanzables) ambos módulos coinciden.

**Experimento 0 — ¿Qué estados produce la API?**
```bash
grep -rn "setStatus|BookingStatus\." src/main/java/ | grep -v "response.setStatus"
→ CreateBookingService.java:100  booking.setStatus(BookingStatus.CONFIRMADA);
→ CancelBookingService.java:146 booking.setStatus(BookingStatus.CANCELADA);
→ OccupancyQueryAdapter.java:26  List.of(BookingStatus.CONFIRMADA, BookingStatus.COMPLETED);  ← solo lectura
```
**La API solo produce `CONFIRMADA` y `CANCELADA`.** `COMPLETED` y `RESCHEDULED` son inalcanzables (cero `setStatus`, cero `@Scheduled`, cero endpoint).

**Experimento 1 — CONFIRMADA (alcanzable):**
```bash
# Elena, viernes 2026-11-20 14:00-15:00 (dentro de la ventana del reporte)
POST /api/v1/reservas {elena, svc60, 2026-11-20T14:00 → 15:00}   → 201
GET disponibilidad Elena 2026-11-20 → slots ['14:00','15:00','16:00','17:00']   (4 huecos)
POST → 201
GET disponibilidad Elena 2026-11-20 → slots ['15:00','16:00','17:00']          (3 huecos) ← 14:00 DESAPARECIÓ
GET reporte → Elena: booked=60 (era 0)  ✅ CUENTRA
```
**Los dos módulos COINCIDEN.**

**Experimento 2 — CANCELADA (alcanzable):**
```bash
POST /api/v1/reservas {elena, svc60, 2026-11-20T16:00 → 17:00}  → 201
PATCH /api/v1/reservas/{id}/cancelacion {"reason":"S3-03 probe"} → 200 {"status":"CANCELADA"}
GET disponibilidad Elena 2026-11-20 → ['15:00','16:00','17:00']  ← 16:00 VOLVIÓ ✅
GET reporte → Elena: booked=60 (era 120)  ← -60 ✅
```
**Los dos módulos COINCIDEN.**

**Experimento 3 — COMPLETED (inalcanzable):** mecanismo verificado por SQL.
```bash
# Suma reportada por el API vs. SQL
byDay booked sum (API)          = 5310
SQL CONFIRMADA only (en ventana)= 5220
SQL CONFIRMADA + COMPLETED      = 5310   ← el reporte SÍ cuenta COMPLETED ✅ (OCCUPYING_STATUSES)
# COMPLETED no bloquea disponibilidad:
GET disponibilidad Sofia 2026-12-28 (servicio 30 min) → 6 huecos: 09:00,09:30,10:00,10:30,11:00,11:30
# La reserva COMPLETED de 2026-12-28 09:00-10:00 NO desaparece de la disponibilidad ✅
```

**Camino de reproducción real:** **SQL-directo** para el caso COMPLETED (la API no lo alcanza). **API** para CONFIRMADA y CANCELADA — y ahí **no hay bug**.

**Veredicto:** **REFUTADO** en su formulación original. El defecto descrito **no es alcanzable por el usuario**.

**Defecto real reformulado:** *Deuda de robustez (severidad BAJA-MEDIA), no bug de usuario.* Hay **dos listas de estados occupant** duplicadas en dos módulos, sin fuente común:
- `BookingRepository.findOverlappingBookings` → solo `CONFIRMADA` (línea 29)
- `OccupancyQueryAdapter.OCCUPYING_STATUSES` → `CONFIRMADA + COMPLETED` (línea 25-26)

El día que exista un endpoint de cierre (`setStatus(COMPLETED)`), una reserva atendida **liberará la franja para el cliente** mientras el reporte **seguirá contándola como ocupada**: exactamente el defecto descrito, pero hoy es latente. `RESCHEDULED` está en el limbo en los **tres** lugares (EXCLUDE, `findOverlappingBookings`, `OCCUPYING_STATUSES`).

**Severidad real:** **BAJA** (deuda técnica). **No accionable como bug de Sprint 3**; sí accionable como deuda: extraer un `OccupancyStatusPolicy` único (o un `BookingStatus.occupiesAgenda()`) usado por los tres puntos.

**Acción:** crear `BookingStatus#occupiesAgenda()` con la política acordada (`CONFIRMADA`, `COMPLETED` y `RESCHEDULED` si se define que ocupa) y usarlo en `findOverlappingBookings`, `OCCUPYING_STATUSES` y el predicado de la EXCLUDE de `V46`. Añadir el caso COMPLETED a las pruebas de integración (hoy solo se puede por SQL).

---

## S3-04 — El porcentaje se recorta en silencio a 100

**Hipótesis:** `booked > capacity` → `percent()` devuelve 100.0 sin avisar.

**Predicción:**
- Si es real: existe un camino por API que produce `booked > capacity`.
- Si es falso: los tres candados (L1 la reserva cabe entera en una franja, L2 las franjas no se solapan, L3 las CONFIRMADA no se solapan) garantizan `booked <= capacity` siempre.

**Experimento A — el caso del seed (SQL):**
```
Miguel Lopez: booked=4800, capacity=4680, pct=100.0
   80 reservas CONFIRMADA × 60 min, incluidas 12 sábado + 12 domingo
   → Miguel NO tiene franja sábado/domingo → capacidad 0 en esos días
   → el recorte se activa (real = 102.56%)
```
**No alcanzable por API** (probado): `POST /reservas` el domingo → `409 {"error":"SLOT_OUTSIDE_SCHEDULE"}`.

**Experimento B — ¿existe un camino POR API? (búsqueda del candado L1/L2/L3)**

L1 y L2 probados por API:
```bash
PUT /api/v1/horarios/{elena} {"slots":[{"MONDAY","09:00","12:00"},{"MONDAY","11:00","14:00"}]}
→ 409 {"errorCode":"SCHEDULE_CONFLICT","details":{"MONDAY 11:00-14:00":"Se superpone con MONDAY 09:00-12:00"}}
```
L3: `booking_no_overlapping_confirmed` (EXCLUDE) impide el solape entre CONFIRMADA — verificado: 0 pares solapados.

**PERO: el candado L1 NO cubre el caso en que el profesional CAMBIA su horario después de reservar.** La API permite `PUT /api/v1/horarios/{id}` (reemplaza todo) **en cualquier momento**, incluso con reservas CONFIRMADA vigentes.

**Experimento B2 — ruta API encontrada (scheduling shrink):**
```bash
# 1) Elena con franja VIERNES 14:00-18:00
# 2) Reservar 7 huecos de 30 min HOY (viernes) vía API → 7 × 30 = 210 min CONFIRMADA
# 3) PUT /api/v1/horarios/{elena} → reduce VIERNES a 14:00-14:30 (30 min)
#    → capacidad = 30 min × 5 viernes de la ventana = 150 min
# 4) GET /api/v1/reportes/ocupacion
→ Elena: bookedMinutes=210, capacityMinutes=150, occupancyPercent=100.0
   (real = 140.0%, recortado a 100.0)
```
**Reproducción 3/3 determinista** (`run 1/2/3 → booked 210 cap 150 pct 100.0`).

**Camino de reproducción real:** **API** (totalmente alcanzable).

**Veredicto:** **CONFIRMADO** — y **más grave de lo reportado**: el informe original afirmaba que solo se disparaba con "un profesional con dos franjas que se solapan" (creado por SQL). **La ruta real es la API, sin datos especiales**: el profesional puede encoger su propio horario después de vender los huecos.

**Defecto real reformulado:** *`OccupancyCalculator.percent()` (líneas 63-70) aplica `Math.min(100.0, percent)` de forma incondicional, sin ninguna marca de saturación. El DTO `OccupancyReportResponse.ProfessionalOccupancy` / `DayOccupancy` **no tiene ningún campo que indique que el valor fue recortado** ni el valor sin recortar. El consumidor no puede distinguir "agenda al 100%" de "dato inflado por capacidad mal calculada".*

**Severidad real:** **ALTA** (sube de ALTA a ALTA, pero con **alcance mayor**: el caso de reproducción es API, no SQL; y el defecto de contrato es independiente de si el recorte se activa).

**Acción:**
- Añadir a `OccupancyReportResponse` los campos `bookedMinutes`, `capacityMinutes` (ya existen) + `rawOccupancyPercent` y `saturated` (boolean) — **con los dos números ya presentes, el consumidor YA puede detectar la saturación**; falta el booleano explícito y la documentación.
- Sustituir `Math.min(100.0, percent)` por el valor real, o mantener el tope **solo si** `saturated=true` lo acompaña.
- Documentar en `openapi.yaml` (`overallOccupancyPercent` tiene `maximum: 100` — línea 1054) y en `docs/HU-15-reporte-ocupacion.md` ("Ocupación % = ... con dos decimales y **tope de 100**") el comportamiento.
- **Precondición de negocio a decidir:** ¿debe `PUT /api/v1/horarios/{id}` rechazar un horario que deje reservas CONFIRMADA fuera de la franja? Hoy la API lo permite y genera la incoherencia. Es la **causa raíz** del recorte silencioso.

---

## S3-05 — La capacidad se infla por franjas solapadas

**Hipótesis:** 09-12 + 11-14 se reportan como 360 en vez de 300.

**Predicción:**
- Si es real: la API **permite** guardar las dos franjas y el reporte las suma sin fusionar.
- Si es falso: la API las rechaza (409) → el caso solo existe por SQL → hay que reformular.

**Experimento:**
```bash
# ¿La API acepta franjas solapadas?
PUT /api/v1/horarios/{elena} {"slots":[{"MONDAY","09:00","12:00"},{"MONDAY","11:00","14:00"}]}
→ 409 {"errorCode":"SCHEDULE_CONFLICT","details":{"MONDAY 11:00-14:00":"Se superpone con MONDAY 09:00-12:00"}}
# Control contiguo (se tocan, no se solapan) → 200 ✅
PUT /api/v1/horarios/{elena} {"slots":[{"MONDAY","09:00","12:00"},{"MONDAY","12:00","14:00"}]} → 200
```

**El caso del seed (SQL):**
```bash
# Miguel tiene MON 09:00-12:00 Y MON 11:00-14:00 (insertados por el seed)
select dia_semana,hora_inicio,hora_fin from horario_semanal where profesional_id='{miguel}';
→ MONDAY 09:00-12:00 | MONDAY 11:00-14:00

# Efecto medido en el reporte (Elena, + una franja solapada por SQL)
BEFORE: Elena capacity=1200 (MONDAY)   occupancy=5.0%
AFTER : Elena capacity=1920 (MONDAY)   occupancy=3.13%   ← +720 min (4 lunes × 180)
# Cobertura real (unión de intervalos): 300 min/lunes → 1200 en la ventana, no 1920
```

**Cálculo independiente (Miguel, ventana [2026-10-22, 2026-11-20]):**
```
SUMA ingenua (lo que reporta el código) = 4680
UNIÓN real de intervalos                = 4440
Inflación = 240 min = 5.41%
```

**Asimetría de defensa en profundidad — verificada:**
```sql
select conrelid::regclass, conname from pg_constraint where contype='x';
→ bookings       | booking_no_overlapping_confirmed
→ bloqueo_agenda | bloqueo_agenda_sin_solapamiento
```
Tablas con rango temporal: **3** (`bookings`, `bloqueo_agenda`, `horario_semanal`). **2 de 3 tienen EXCLUDE. `horario_semanal` NO tiene ninguna.**
```sql
-- Prueba de que la BD sí acepta el solape (con hora_inicio distinta):
insert into horario_semanal (... 'MONDAY','11:00','14:00' ...) → INSERT 0 1  ✅
insert con la MISMA hora_inicio → ERROR: uk_horario_semanal_inicio  (solo bloquea inicio idéntico)
```

**Camino de reproducción real:** **SQL-directo** para el solape. **API** para el resto.

**Veredicto:** **CONFIRMADO** como asimetría de esquema, pero **REFUTADO** como bug de usuario: el usuario **nunca** puede crear el solape (la API lo rechaza con 409 antes de llegar a la BD).

**Defecto real reformulado:** *`horario_semanal` es la única tabla de rango temporal del esquema sin restricción de exclusión. La defence depende 100% del servicio (`WeeklyScheduleService.validate`), mientras que `bookings` y `bloqueo_agenda` tienen defensa en la BD. Consecuencia: cualquier escritura por SQL (seed, migración, script de carga, job, ETL, consola) introduce franjas solapadas que inflan la capacidad y subestiman todos los porcentajes de ocupación — sin que ningún control del sistema lo detecte.*

**Severidad real:** **MEDIA** (baja de ALTA). No hay ruta de usuario; el impacto real es sobre la **calidad de los datos seed y de cualquier carga masiva**, y sobre la **fiabilidad del reporte** como insumo de decisión.

**Acción:**
- **Defensa en profundidad (recomendado):** migración `V5x` que añada a `horario_semanal` un `EXCLUDE USING gist (profesional_id WITH =, tsrange(hora_inicio, hora_fin, '[)') WITH &&)` (con `btree_gist`, ya instalado por V46/V50). Requiere normalizar `hora_inicio/hora_fin` a `time`; PostgreSQL no soporta `tsrange` sobre `time` directamente → usar `EXCLUDE` con `int4range` sobre minutos, o migrar a `timestamp`.
- **Corregir los datos seed** (Miguel MON 11:00-14:00) — están=falseando la medición de todo el sprint.
- **Corregir `OccupancyCalculator`**: fusionar las franjas solapadas antes de sumar (`capacityByProfessional` y `capacityByDay`).
- Añadir una prueba de contrato: `sum(byDayOfWeek.capacity) == sum(byProfessional.capacity)` (ya se cumple hoy: 13920 == 13920).

---

## S3-06 — La ruta vieja de historial no pagina ni audita

**Hipótesis:** ADMIN lee 111 reservas en 60KB sin paginar y sin rastro en `audit_log`.

**Predicción:**
- Si es real: la ruta `/reservas/cliente/{id}` devuelve un array plano sin metadatos de paginación, y `audit_log` no crece.
- Si es falso: la ruta devuelve una página con metadatos y deja auditoría.

**Experimento:**
```bash
# ADMIN en la ruta vieja
GET /api/v1/reservas/cliente/{hu13.customer}  (90 reservas en la BD)
→ 200, array plano de 90 elementos, 48.575 bytes
   claves de cada elemento: id, customerId, professionalId, serviceId, customerName,
                            professionalName, serviceName, startTime, endTime, status,
                            createdAt, updatedAt, cancelledAt, cancellationReason
   SIN page / totalElements / totalPages

# ADMIN en la ruta nueva
GET /api/v1/reservas/cliente/historial?page=0&size=50
→ 403 {"errorCode":"ACCESS_DENIED","message":"No tiene permisos para este recurso"}

# Auditoría: 3 lecturas de la ruta vieja
audit_log count antes=59  después=59  → delta=0   ❌ SIN RASTRO
# Y la ruta nueva (como CUSTOMER) tampoco audita: delta=0
```

**Auditoría: la acción de lectura NI EXISTE en el esquema.**
```sql
select conname, pg_get_constraintdef(oid) from pg_constraint
where conname='ck_audit_log_action_type';
→ CHECK (action_type::text = ANY (ARRAY['BOOKING_CREATED','BOOKING_CANCELLED','ROLE_CHANGED']))
```
`AuditActionType` solo tiene 3 acciones de **escritura**. **No hay ninguna acción de lectura**, y el CHECK de la BD **impide** añadirla sin migración. → No es "el código olvidó auditar": es **diseño de esquema**.

**Los tres puntos del enunciado, verificados:**

| Punto | Veredicto | Evidencia |
|---|---|---|
| (a) No pagina | ✅ **CONFIRMADO** | 200 + array plano de 90 elementos / 48 KB, sin metadatos. El problema de DoS es real: el growth es lineal y sin techo. |
| (b) No audita | ✅ **CONFIRMADO (y más grave)** | delta=0. Y el CHECK `ck_audit_log_action_type` **impide** auditar lecturas. |
| (c) Incoherencia interna ADMIN 403 vs 200 | ✅ **CONFIRMADO** | `SecurityConfiguration.java:126-128` restringe `/reservas/cliente/historial` a `hasRole("CUSTOMER")`; la ruta vieja usa `OwnershipGuard` que **concede el acceso al ADMIN sin dejar rastro** (`OwnershipGuard.java:24-26`). |

**¿Debería el ADMIN poder ver el historial de otro cliente? — `docs/HU-13` dice:**
> "Se conserva la ruta existente `/api/v1/reservas/cliente/{customerId}` **para compatibilidad con consumidores actuales**."

→ Es **deliberado** (compatibilidad). El `openapi.yaml` de la ruta vieja declara **solo `401`**, sin `403` → el acceso del ADMIN es intencional.

**Camino de reproducción real:** **API** (100% alcanzable).

**Defecto real reformulado:** *Dos rutas para la misma operación de negocio con contratos incompatibles: (i) la ruta de compatibilidad no pagina y no audita; (ii) la ruta nueva pagina pero excluye al ADMIN. Consecuencia: el ADMIN tiene una vía **ilimitada y sin rastro** para leer el historial completo de cualquier cliente (datos personales de terceros), mientras que el usuario legítimo de esa misma vista (el propio cliente) solo puede ver 50 registros por página y no tiene forma de saber que existe una lectura de su historial por terceros.*

**Severidad real:** **MEDIA-ALTA**. Por trazabilidad de datos personales y por DoS no acotado.

**Acción:**
- **Auditoría de lectura:** migración que añada `HISTORY_READ` a `ck_audit_log_action_type` y a `AuditActionType`; invocar `AuditRecorder` en el `OwnershipGuard` cuando el actor es ADMIN y el recurso pertenece a un tercero (registrar `actor`, `resource_id` = customerId, `source_ip`, `occurred_at`).
- **Paginación en la ruta vieja:** delegar en `CustomerBookingHistoryService.find(...)` (ya paginado) o deprecar la ruta con `@Deprecated` + cabecera `Deprecation`/`Sunset`.
- **Unificar el criterio de rol:** o `hasAnyRole("CUSTOMER","ADMIN")` en ambas rutas, o denegar el ADMIN en ambas. Decidir y documentar en `HU-13` + `openapi.yaml`.
- **Aplicar el mismo tratamiento a `/reservas/profesional/{professionalId}`** (ver bug nuevo S3-13: 80 reservas / 43 KB sin paginar).

---

## S3-07 — La disponibilidad devuelve horas repetidas

**Hipótesis:** con franjas solapadas salen 12 horas para 9 únicas (11:00 y 11:30 ×2).

**Predicción:**
- Si es real por API: existe alguna combinación de franjas **no solapadas** que produzca slots duplicados.
- Si es falso: los duplicados **exigen** franjas solapadas, y la API las rechaza → el caso solo es alcanzable por SQL.

**Experimento A — reproducción con el seed (SQL):**
```bash
GET /api/v1/disponibilidad?profesionalId={miguel}&servicioId={60min}&desde=2026-11-23&hasta=2026-11-23
→ slots: ['09:00','10:00','11:00','11:00','12:00','13:00']   count=6 unique=5   dups={'11:00': 2}  ✅
```

**Experimento B — ¿hay una vía por API? Análisis exacto del algoritmo + búsqueda exhaustiva.**
`AvailabilityCalculator.freeSlots` (líneas 26-44): por cada ventana, `cursor` se reinicia en `window.startTime` y avanza en pasos de `duration`. Un slot duplicado exige que **existan dos ventanas con un instante `t` y `t+duration` dentro de ambas** → las ventanas **comparten intervalo** → **se solapan**.

Búsqueda exhaustiva sobre **todas** las combinaciones de 2 y 3 ventanas disjuntas en la rejilla de 30 min entre 08:00 y 18:00, para duraciones 30/45/60/90:

```
=== BUSQUEDA EXHAUSTIVA: existe alguna combinacion DISJUNTA que duplique? ===
  NINGUN contraejemplo: con franjas disjuntas NUNCA hay duplicados
```

**Experimento C — carrera en `PUT /api/v1/horarios/{id}` (6 rondas concurrentes, 2 requests con horarios distintos):**
```
round 1: req1=401 req2=200  → queda "MONDAY 11:00-14:00 | WEDNESDAY 09:00-12:00"
round 2: req1=200 req2=401  → queda "MONDAY 09:00-12:00 | TUESDAY 09:00-12:00"
round 3: req1=401 req2=200  → queda "MONDAY 11:00-14:00 | WEDNESDAY 09:00-12:00"
...
→ SIEMPRE queda UNO de los dos horarios completos, NUNCA una mezcla, NUNCA solapado
```
La operación `deleteAll` + `flush` + `saveAll` es atómica por transacción → **nunca** genera solapes. Pero **una de las dos peticiones siempre recibe 401** (bug nuevo S3-12).

**Camino de reproducción real:** **SQL-directo** (franjas solapadas). No hay ruta por API.

**Veredicto:** **REFUTADO como bug de usuario.** La duplicación **exige** franjas solapadas, y la API las rechaza con 409 `SCHEDULE_CONFLICT`.

**Defecto real reformulado:** *`AvailabilityCalculator.freeSlots` no fusiona las franjas del día antes de generar los huecos. Es la misma raíz que S3-05 (falta de fusión de intervalos), pero **el impacto en disponibilidad está bloqueado por la validación del servicio**. El defecto queda latente: si algún día `WeeklyScheduleService.validate` se relaja, o si entra una carga por SQL, aparecen horas repetidas — y el propio `AvailabilityCalculator` es el **único** que debe defender el invariante "cada hora de inicio se ofrece una sola vez", cosa que hoy no hace.*

**Severidad real:** **BAJA** (de MEDIO). Defecto de robustez latente, no accionable como bug de Sprint 3.

**Acción:**
- `AvailabilityCalculator.freeSlots`: fusionar las ventanas del día (unión de intervalos) antes de generar huecos → elimina la clase de bug y hace el cálculo correcto por construcción.
- Alternativa mínima y barata: deduplicar el resultado con `distinct()` sobre `AvailableSlot.startTime` — pero la fusión es la solución de raíz y también corrige S3-05.
- **Prueba de regresión**: `AvailabilityCalculator` con dos ventanas solapadas debe emitir cada inicio una sola vez (hoy la suite de integración de S3-07 sí falla, lo que demuestra que la prueba es válida).

---

## S3-08 — Día con reservas pero sin horario se reporta como 0%

**Hipótesis:** 1590 min en sábado/domingo reportados como 0%.

**Predicción:**
- Si es real: existen días con `bookedMinutes > 0` y `capacityMinutes = 0` que muestran `occupancyPercent = 0.0`.
- Si es falso: todo día con capacidad 0 no tiene reservas, o el DTO expresa "no calculable".

**Experimento:**
```bash
GET /api/v1/reportes/ocupacion
→ MONDAY    booked= 780 cap=3120 pct=25.0
   TUESDAY   booked= 720 cap=2400 pct=30.0
   WEDNESDAY booked= 780 cap=2400 pct=32.5
   THURSDAY  booked= 480 cap=3000 pct=16.0
   FRIDAY    booked= 840 cap=3000 pct=28.0
   SATURDAY  booked= 870 cap=   0 pct= 0.0    ← 870 min de actividad reportados como 0%
   SUNDAY    booked= 780 cap=   0 pct= 0.0    ← 780 min de actividad reportados como 0%
```
**Cifra del informe original: "1590 min" → la medición actual da 1650 min** (870+780). El informe original subestimó.

**Sesgo del global — medido:**
```
overall reportado (con los minutos de días sin capacidad): 38.15 %
overall excluyendo esos minutos:                            26.29 %
→ el global queda INFLADO en 11.86 puntos porcentuales
```
El cálculo es `sum(booked) / sum(capacity)`: los minutos de días con capacidad 0 entran **íntegros en el numerador** y **no aportan nada al denominador**.

**¿Es alcanzable por API?** El caso del seed (sáb/dom con reservas) es **SQL-directo** (probado: `POST` el domingo → `409 SLOT_OUTSIDE_SCHEDULE`). **PERO** encontré una ruta **por API**: encoger el horario tras reservar.
```bash
# Elena tiene 1 reserva CONFIRMADA hoy (viernes) y su horario se borra por API
PUT /api/v1/horarios/{elena} {"slots":[]}
GET /api/v1/reportes/ocupacion
→ Elena: bookedMinutes=60, capacityMinutes=0, occupancyPercent=0.0   ← API, no SQL
```

**Camino de reproducción real:** **API** (ruta del encogimiento de horario) **y** SQL-directo (el caso del seed).

**Veredicto:** **CONFIRMADO** — alcanzable por API.

**Defecto real reformulado:** *`OccupancyCalculator.percent()` (líneas 65-67) usa `0.0` como centinela para "capacidad 0 / no calculable", valor indistinguible de "ocupación real del 0%". El DTO `DayOccupancy.occupancyPercent` / `ProfessionalOccupancy.occupancyPercent` es un `double` primitivo, por lo que **ni siquiera puede expresar `null`**. Consecuencia: un día con 870 min de actividad y 0% de ocupación se presenta como un día libre, y el porcentaje global queda inflado 11.86 pp.*

**Severidad real:** **MEDIA** (sin cambios), pero **el caso del seed se服务和 el caso API se solapan en el mismo defecto** — lo que **sí** es accionable.

**Acción:**
- Cambiar `occupancyPercent` de `double` a `Double` (nullable) y devolver `null` cuando `capacityMinutes == 0`; **o** añadir un campo `calculable: boolean`. Requiere actualizar `openapi.yaml` y `docs/HU-15`.
- Corregir el global: excluir del numerador los minutos de días con capacidad 0 (o publicar un `overallOccupancyPercent` y otro `overallBookedMinutesInDaysWithoutCapacity`).
- **Causa raíz de los datos:** el seed crea 12 reservas de sábado y 12 de domingo para un profesional **sin franja en sábado/domingo**. Corregir el seed: o añadir franja, o no crear reservas fuera de horario. Es el **mismo defecto que S3-08**, y su raíz está en el seed, no en el código.
- Nota: `docs/HU-15` dice *"Si la capacidad es 0, la ocupación es 0 (no hay división por cero)"* → la decisión está documentada pero **no distingue los dos casos**. Documentar el cambio.

---

## S3-09 — Dos contratos de error distintos

**Hipótesis:** `BookingController` devuelve `record ErrorResponse(error,message)`; el resto usa `ApiErrorResponse(errorCode,message,details,traceId,timestamp)`.

**Predicción:**
- Si es real: un 409 de reserva tiene 2 campos y ningún `traceId`; un 409 de otro módulo tiene 5 campos.
- Si es falso: ambos tienen la misma forma.

**Experimento:**
```bash
# 409 de BookingController
POST /api/v1/reservas {elena, svc60, 2026-11-20T14:00→15:00}  (ya reservada)
→ 409 {"error":"SLOT_OCUPADO","message":"El horario seleccionado ya no está disponible"}
# 409 de BookingController, otro código
POST /api/v1/reservas {..., 2026-11-22T09:00→10:00}  (domingo)
→ 409 {"error":"SLOT_OUTSIDE_SCHEDULE","message":"El horario seleccionado está fuera de la disponibilidad del profesional"}
# 409 de otro módulo
PUT /api/v1/horarios/{elena} {MONDAY 09-12 + MONDAY 11-14}
→ 409 {"errorCode":"SCHEDULE_CONFLICT","message":"El horario contiene franjas en conflicto",
        "details":{"MONDAY 11:00-14:00":"Se superpone con MONDAY 09:00-12:00"},
        "traceId":"d60f18b0-...","timestamp":"2026-11-20T12:43:27.416..."}
```

**Contrato publicado (`docs/openapi.yaml`):**
```yaml
ApiError:
  required: [errorCode, message, details, traceId, timestamp]
  properties: { errorCode, message, details, traceId, timestamp }

/reservas:
  post:
    responses:
      '409': { $ref: '#/components/responses/ConflictError' }   → ApiError
```

**Camino de reproducción real:** **API** (100% alcanzable).

**Veredicto:** **CONFIRMADO**.

**Defecto real reformulado:** *`BookingController` declara un `private record ErrorResponse(String error, String message)` (línea 132) y captura `BookingConflictException` / `IllegalArgumentException` **localmente** (líneas 105-111), en lugar de dejar que el `GlobalExceptionHandler` construya el `ApiErrorResponse` estándar. Consecuencia: (i) el `openapi.yaml` describe un contrato que la implementación **no cumple** en 2 de los códigos de error de `POST /reservas`; (ii) los errores de reserva — **los más frecuentes de reportar por el usuario** — son los **únicos sin `traceId`**, lo que impide correlacionar un reporte de usuario con la traza del servidor; (iii) los errores de reserva **no tienen `details`**, por lo que el cliente recibe el mismo detalle pobre que en el camino secuencial.*

**Severidad real:** **MEDIA** (sin cambios). Agrava S3-01 (el 401 no tiene `errorCode` de negocio ni `details`).

**Acción:**
- Eliminar el `try/catch` local de `BookingController.createBooking` y dejar que `BookingConflictException` (que ya es un `ResourceConflictException`) lo maneje el `GlobalExceptionHandler`; eliminar el `record ErrorResponse`.
- Verificar que `BookingConflictException` herede de `ResourceConflictException` (o registrar su handler con `errorCode`).
- **Prueba de contrato**: para cada endpoint, para cada status ≥ 400, assert que el body tiene las 5 claves de `ApiError`. Hoy fallaría en `POST /reservas` (409 y 400).

---

## S3-10 — Una RESCHEDULED puede solaparse con una CONFIRMADA

**Hipótesis:** la EXCLUDE de V46 solo aplica a CONFIRMADA; RESCHEDULED es invisible en disponibilidad y reporte.

**Predicción:**
- Si es real: existe una ruta por API que produce RESCHEDULED.
- Si es falso: RESCHEDULED es inalcanzable → el defecto es latente.

**Experimento:**
```bash
grep -rn "setStatus|BookingStatus\." src/main/java/
→ CreateBookingService.java:100  setStatus(CONFIRMADA)
→ CancelBookingService.java:146 setStatus(CANCELADA)
   (ningún setStatus(COMPLETED), ningún setStatus(RESCHEDULED))
```
**Consumidores de `BookingRescheduledEvent`:**
```bash
grep -rn "BookingRescheduledEvent" src/main/java/
→ BookingEvent.java:8                       (permits)
→ BookingRescheduledEvent.java              (la clase)
→ NotificationService.java:106              (envía el email de reprogramación)
```
→ **Existe el consumidor (email), pero NO hay publicador.** El evento es un **contrato muerto**: `NotificationService` tiene una rama `instanceof BookingRescheduledEvent` que **nunca se ejecuta**.

**Artefactos huérfanos del flujo:**
- `RescheduleBookingRequest` (DTO con `@Future`) — **no lo referencia nadie** (verificado por grep).
- `NotificationEventType.BOOKING_RESCHEDULED` — definido.
- `templates/email/booking-rescheduling.txt` — existe.
- `NotificationTemplateRenderer.rescheduling(...)` — implementado.

**Camino de reproducción real:** **NO-REPRODUCIBLE por API** (SQL-directo únicamente).

**Veredicto:** **REFUTADO** en su formulación original (no hay ruta de usuario) — pero **REFORMULADO** como deuda de diseño **latente y real**.

**Defecto real reformulado:** *La reprogramación es una **vertical cortada**: existe el contrato de evento, el tipo de notificación, la plantilla y el handler del email, pero **no existe ni el endpoint ni el servicio que la produzca**. Mientras tanto, el estado `RESCHEDULED` está ausente de los **tres** puntos de control de ocupación: (i) la EXCLUDE `booking_no_overlapping_confirmed` (`WHERE (status = 'CONFIRMADA')`), (ii) `BookingRepository.findOverlappingBookings` (`b.status = 'CONFIRMADA'`), (iii) `OCCUPYING_STATUSES` (`CONFIRMADA + COMPLETED`). En consecuencia, **cuando se construya el endpoint**: una RESCHEDULED podrá solaparse con una CONFIRMADA (la EXCLUDE no lo impide), será invisible en la disponibilidad (el cliente podrá reservar sobre su franja) y no computará en el reporte (la ocupación real se subestimará).*

**Severidad real:** **MEDIA** → **BAJA-MEDIA** (de "bug" a "deuda de diseño"). La severidad es la **deuda**, no el síntoma actual.

**Acción:**
- **Decisión de dominio (bloqueante para el sprint siguiente):** ¿una reserva reprogramada ocupa agenda? Si sí → añadir `RESCHEDULED` a la EXCLUDE (migración), a `findOverlappingBookings` y a `OCCUPYING_STATUSES`. Si no → documentarlo y **eliminar** `BookingRescheduledEvent` + `NotificationEventType.BOOKING_RESCHEDULED` + la plantilla + `RescheduleBookingRequest` huérfano.
- **No construir el endpoint hasta tomar la decisión**: hoy el código tiene las dos mitades (productor ausente, consumidor presente) y eso es exactamente donde un wired-up-de-la-carrera genera el bug.
- Alternativa: añadir un `BookingStatus#occupiesAgenda()` (ver S3-03) que sea la **única** fuente, y usarlo en la definición del predicado de la EXCLUDE.

---

## S3-11 — Disponibilidad y reporte nunca observan el mismo periodo

**Hipótesis:** la disponibilidad rechaza el pasado; el reporte usa una ventana fija. Nunca coinciden.

**Predicción:**
- Si es real: no existe **ninguna** combinación de fechas en la que ambas consultas sean válidas.
- Si es falso: existe al menos un rango común.

**Experimento:**
```bash
# disponibilidad: rango que termina en el pasado
GET /api/v1/disponibilidad?...&desde=2026-11-01&hasta=2026-11-19
→ 400 {"errorCode":"VALIDATION_ERROR","details":{"hasta":"Debe ser hoy o una fecha futura"}}

# disponibilidad: ayer..HOY  (el extremo 'hasta' = hoy NO es "antes de hoy")
GET /api/v1/disponibilidad?...&desde=2026-11-19&hasta=2026-11-20
→ 200 {"from":"2026-11-19","to":"2026-11-20","available":true,
        "days":[{"date":"2026-11-20","slots":[...]}]}   ← ¡VALIDO!

# reporte: ignora from/to, usa la ventana fija
GET /api/v1/reportes/ocupacion?from=2026-01-01&to=2026-01-31
→ 200 {"period":"MES","from":"2026-10-22","to":"2026-11-20"}   ← los from/to se descartan
GET /api/v1/reportes/ocupacion?periodo=SEMANA
→ 200 {"period":"SEMANA","from":"2026-11-14","to":"2026-11-20"}
```

**Hay solapamiento**: `hoy` pertenece a **ambas** ventanas (disponibilidad acepta `hasta=hoy`; el reporte termina hoy). El rango `[ayer, hoy]` es válido en disponibilidad y `[hoy-29, hoy]` / `[hoy-6, hoy]` en el reporte → **existe un rango contrastable**.

**¿La ventana fija fue deliberada?** — `docs/HU-15-reporte-ocupacion.md`:
> | `periodo` | No (por defecto `MES`) | `SEMANA` = últimos 7 días; `MES` = últimos 30 días. **Ambos terminan hoy.** |

Y `openapi.yaml` (líneas 638-643): el único parámetro es `periodo` con `enum: [SEMANA, MES]`. **No hay `from`/`to` en el contrato publicado** → la ventana fija es **deliberada**.

**Camino de reproducción real:** **API** (ambos endpoints).

**Veredicto:** **REFUTADO** en su formulación ("nunca observan el mismo periodo" es falso) — y la causa raíz es una **decisión de diseño documentada**, no un descuido.

**Defecto real reformulado:** *El reporte de ocupación **no es consultable para un periodo arbitrario** (solo `SEMANA` o `MES`, ambos anclados a hoy), mientras que la disponibilidad sí acepta un rango arbitrario de hasta 31 días. Consecuencia: (i) **no se puede auditar un periodo histórico concreto** — el caso de uso analítico principal de un reporte de ocupación; (ii) el reporte no se puede comparar con un rango de disponibilidad fuera de la ventana actual. No es una incoherencia de lógica, sino una **limitación de usabilidad y de auditabilidad no declarada en el `openapi` ni en la respuesta**.*

**Severidad real:** **BAJA** (de MEDIO). No es un defecto de corrección de datos.

**Acción:**
- **Opción A (recomendada, barata):** documentar la limitación en `docs/HU-15` y en el `openapi` (la descripción del endpoint ya dice "ambos terminando hoy" — añadirlo al resumen) y, sobre todo, **exponer `from`/`to` en la respuesta** (ya están) para que el cliente sepa qué ventana cubre.
- **Opción B:** añadir `desde`/`hasta` opcionales al reporte con la ventana actual por defecto (permite auditar histórico y habilita el caso de integración que el criterio de aceptación de S3-11 describe).
- **Independiente de A/B:** el criterio de aceptación original ("un caso de integración compara, para el mismo rango, los minutos bloqueados en la disponibilidad contra los minutos de capacidad del reporte") **hoy es ejecutable** usando `hasta=hoy` en disponibilidad y el reporte `SEMANA`/`MES`. Rehacer la prueba así en lugar de declarar el bug irresoluble.

---

# TABLA RESUMEN

| ID | Veredicto original | Veredicto corregido | ¿Accionable? | Nuevo S3-XX si se reescribe |
|---|---|---|---|---|
| **S3-01** | Confirmado (401 vs 409) | **CONFIRMADO** — 2 caminos de fallo (25P02 + 40P01), **el servicio también falla** (0/10 conflictos de negocio); el 401 es efecto del `ExceptionTranslationFilter` | **Sí** — CRÍTICA | Se reescribe → **S3-12** (401 falso por `ExceptionTranslationFilter`) + **S3-01** (manejo de excepciones de concurrencia en `AgendaBlockService`) |
| **S3-02** | Confirmado (CRÍTICA) | **CONFIRMADO** — API, delta=0, contradicción con disponibilidad demostrada | **Sí** — CRÍTICA | Sin cambio (S3-02) |
| **S3-03** | Confirmado (ALTA) | **REFUTADO** para estados alcanzables (CONFIRMADA y CANCELADA **coinciden**); REFORMULADO como deuda de estado no alcanzable | **Parcial** — como deuda de robustez (BAJA) | → **S3-14** (política única de estados occupant) |
| **S3-04** | Confirmado (ALTA) | **CONFIRMADO** — y **alcanzable por API** (encogimiento del horario), no solo por SQL | **Sí** — ALTA | Sin cambio (S3-04) |
| **S3-05** | Confirmado (ALTA) | **CONFIRMADO como asimetría de esquema**; la API lo impide → **no hay bug de usuario** | **Sí** — MEDIA (defensa en profundidad + corregir seed) | Sin cambio (S3-05) |
| **S3-06** | Confirmado (MEDIO) | **CONFIRMADO** — los 3 puntos; además el CHECK de `audit_log` **impide** auditar lecturas | **Sí** — MEDIA-ALTA | Sin cambio (S3-06) |
| **S3-07** | Confirmado (MEDIO) | **REFUTADO** — la duplicación **exige** franjas solapadas (búsqueda exhaustiva) y la API las rechaza | **No urgente** — fusionar ventanas (misma raíz que S3-05) | Fusionar en **S3-05** |
| **S3-08** | Confirmado (MEDIO) | **CONFIRMADO** — alcanzable por API; 1650 min (no 1590); el global se infla 11.86 pp | **Sí** — MEDIA | Sin cambio (S3-08) |
| **S3-09** | Probable (MEDIO) | **CONFIRMADO** — contradice el `openapi` publicado | **Sí** — MEDIA | Sin cambio (S3-09) |
| **S3-10** | Probable (MEDIO) | **REFUTADO** como bug de usuario; REFORMULADO como vertical cortada / deuda de diseño | **Parcial** — decidir política de estados (BAJA-MEDIA) | → **S3-15** (decisión de dominio + implementar o eliminar la reprogramación) |
| **S3-11** | Probable (MEDIO) | **REFUTADO** ("nunca" es falso: `[ayer,hoy]` es válido en ambos); la ventana fija es **deliberada y documentada** | **Sí** — como usabilidad/documentación (BAJA) | → **S3-16** (`desde`/`hasta` opcionales en el reporte) |

---

# BUGS NUEVOS DESCUBIERTOS

## S3-12 — MEDIA-ALTA · Transversal · Cualquier excepción no modelada se convierte en 401 "Se requiere autenticación"

**Descubierto en:** S3-01 y en la carrera de `PUT /api/v1/horarios`.

**Defecto:** `GlobalExceptionHandler` **no tiene ningún manejador genérico** (`@ExceptionHandler(Exception.class)` / `(RuntimeException.class)` / `(Throwable.class)`) y ninguna de las excepciones que escapan (`CannotAcquireLockException`, `JpaSystemException`, `ObjectOptimisticLockingFailureException`) está en su lista. La excepción escapa del `DispatcherServlet.doDispatch` y llega a `ExceptionTranslationFilter`, que responde con el `authenticationEntryPoint` → **401**.

**Experimento de control (aisla el mecanismo):**
```bash
GET /api/v1/reservas/noexiste/xyz   (ruta sin handler, endpoint protegido, token válido)
→ 401 {"errorCode":"UNAUTHORIZED","message":"Se requiere autenticación","traceId":"18acc972-..."}
```
Un 404 de enrutamiento debería ser 404. Llega a 401 por la misma vía.

**Camino de reproducción real:** **API**.

**Severidad real:** **ALTA** — es la causa raíz del "401" de S3-01 y afecta a **toda** clase de error no modelada (deadlocks, fallos de serialización JSON, errores de pool de conexiones, `NullPointerException`, etc.). El cliente **cree que su token expiró** cuando el problema era del servidor.

**Acción:** añadir `@ExceptionHandler(Exception.class)` → **500** `ApiErrorResponse` con `traceId`; **nunca** 401 por una excepción de servidor. Esto **arregla S3-01 parcialmente por sí solo** (el 401 pasaría a 500, que ya es honesto) y **deja obsoleto el `catch` de `AgendaBlockService` para el síntoma**, aunque el defecto de negocio (traducir a 409) sigue necesitando su arreglo.

---

## S3-13 — MEDIA · HU-13 · La ruta `/reservas/profesional/{professionalId}` tampoco pagina ni audita

**Descubierto en:** S3-06 (extensión del mismo patrón).

**Defecto:** gemela exacta de la ruta de cliente, y **tampoco** mentioned en el informe original.
```bash
GET /api/v1/reservas/profesional/{miguel}  (ADMIN)
→ 200, array plano de 80 elementos, 43.185 bytes, SIN metadatos de paginación
```
El mismo `OwnershipGuard` concede el acceso al ADMIN sin rastro, y la ruta no tiene `details` de por qué.

**Camino de reproducción real:** **API**.

**Severidad real:** **MEDIA** (misma naturaleza que S3-06).

**Acción:** mismo tratamiento que S3-06: paginación + auditoría de lectura + criterio de rol unificado + marcar como obsoleta.

---

## S3-14 — MEDIA · HU-05/HU-04 · La carrera en `PUT /api/v1/horarios/{id}` responde 401 y **descarta silenciosamente** el horario del perdedor

**Descubierto en:** S3-07 (experimento C).

**Defecto:** `WeeklyScheduleService.replace()` hace `deleteAll` + `flush` + `saveAllAndFlush`. Dos reemplazos concurrentes producen:
```bash
→ round 1: req1=401 req2=200  → queda "MONDAY 11:00-14:00 | WEDNESDAY 09:00-12:00"
   round 2: req1=200 req2=401  → queda "MONDAY 09:00-12:00 | TUESDAY 09:00-12:00"
   ... 6/6 rondas: SIEMPRE un 401 + un 200
```
Causa raíz (log):
```
org.springframework.orm.ObjectOptimisticLockingFailureException:
  Unexpected row count (expected row count 1 but was 0) [delete from horario_semanal where id=?]
  for entity [WeeklySlot with id '693c446d-...']
```
→ **no hay ninguna fila que borrar** (la otra transacción ya la borró) → `StaleStateException` → `ObjectOptimisticLockingFailureException` → sin manejador → **401** (mismo mecanismo que S3-12).

**Agravante propio:** el 200 **no** significa "tu horario se guardó" — el perdedor recibe un 401 y su horario **desaparece** sin que nadie se entere. El profesional cree que tiene `{MON, TUE}` y en realidad tiene `{MON, WED}`.

**Camino de reproducción real:** **API** (2 `PUT` concurrentes, 6/6 rondas).

**Severidad real:** **MEDIA-ALTA** (pérdida silenciosa de configuración de agenda + 401 falso).

**Acción:** capturar `ObjectOptimisticLockingFailureException` → 409 `SCHEDULE_CONFLICT` con mensaje explícito; o serializar el replace con un bloqueo pesimista por `professional_id` (`@Lock(PESSIMISTIC_WRITE)` sobre una fila del profesional), de modo que el segundo pedido reciba un 409 legible en vez de un 401.

---

## S3-15 — MEDIA · HU-08 · La duración de la reserva no tiene que coincidir con la duración del servicio

**Descubierto en:** caza de bugs nuevos (experimento 16).

**Defecto:** `CreateBookingService` valida el rango de tiempo, el futuro, el horario del profesional, los bloqueos y el solapamiento — pero **no valida que `(endTime - startTime) == duración del servicio**.
```bash
POST /api/v1/reservas {elena, svc "Limpieza profunda" (60 min), 2026-11-20T16:00 → 16:15}
→ 201 {"status":"CONFIRMADA"}      ← 15 minutos para un servicio de 60
```
Consecuencia en el reporte:
```
Elena: bookedMinutes=15   (por un servicio de 60 min vendido)
```
→ **la ocupación se subestima** frente al servicio realmente prestado, y el cliente puede "recortar" una reserva de 60 min a 15 min y **liberar el resto de la franja** (que la disponibilidad volverá a ofrecer).

**Camino de reproducción real:** **API**.

**Severidad real:** **MEDIA**. Es una inconsistencia entre el modelo de disponibilidad (slots de duración fija del servicio) y el de reserva (rango arbitrario).

**Acción:** validar en `CreateBookingService` que la duración de la reserva sea exactamente la del servicio activo (o al menos que no sea menor); si se admiten duraciones variables, `AvailabilityCalculator` debe ofrecer slots de esa duración variable.

---

## S3-16 — BAJA-MEDIA · HU-15 · La reserva que cruza la medianoche se imputa entera a un solo día

**Descubierto en:** caza de bugs nuevos (experimento 1).

**Defecto:** `OccupancyCalculator.bookedByDay` usa `booking.startTime().getDayOfWeek()` → **toda** la duración va a un único día, aunque la reserva termine al día siguiente.
```sql
select ... from bookings where end_time::date <> start_time::date;
→ 88888888-8888-4888-8888-000000000001 | 2026-10-30 23:00 → 2026-10-31 01:00 | CONFIRMADA | 120 min
```
→ 120 min imputados íntegros al **viernes**, aunque la capacidad de esa franja de 23:00–01:00 **cruza al sábado** (que tiene capacidad 0 en el seed). Refuerza el defecto S3-08.

**Camino de reproducción real:** **SQL-directo** — la API lo impide: `POST` con `startTime` y `endTime` en días distintos → `409 {"error":"SLOT_OUTSIDE_SCHEDULE"}` (`CreateBookingService.validateWithinProfessionalSchedule` exige `startTime.toLocalDate().equals(endTime.toLocalDate())`).

**Severidad real:** **BAJA** (inalcanzable por API hoy).

**Acción:** documentar la regla de imputación (día de inicio) en `docs/HU-15`, o repartir la duración por días en `bookedByDay`. Mismo tipo de deuda que S3-03.

---

## Resumen de los bugs nuevos

| ID | Severidad | Módulo | Alcanzable por API | Resumen |
|---|---|---|---|---|
| **S3-12** | MEDIA-ALTA | Transversal | **Sí** | Cualquier excepción no modelada → **401 falso** en vez de 500. Causa raíz del 401 de S3-01. |
| **S3-13** | MEDIA | HU-13 | **Sí** | `/reservas/profesional/{id}` sin paginación ni auditoría (gemela de S3-06). |
| **S3-14** | MEDIA-ALTA | HU-05/HU-04 | **Sí** | `PUT /horarios` concurrente → **401** + **pérdida silenciosa** del horario del perdedor. |
| **S3-15** | MEDIA | HU-08 | **Sí** | La duración de la reserva no debe coincidir con la del servicio; infla/subestima la ocupación. |
| **S3-16** | BAJA-MEDIA | HU-15 | No (SQL) | La reserva a medianoche se imputa entera a un solo día. |

---

## Conclusiones para el equipo

1. **El 401 de S3-01 no es "el token falló"**: es `ExceptionTranslationFilter` convertiendo una excepción de servidor no modelada. **Arreglar S3-12 es la prioridad**, porque es transversal y afecta a toda la API.
2. **El patrón de corrección ya existe en el repo**: `CreateBookingService.java:121-134` maneja `DataIntegrityViolationException` **y** `CannotAcquireLockException`. `AgendaBlockService` solo maneja el primero. `POST /reservas` bajo concurrencia da **201 + 409×3** de forma estable; `POST /bloqueos` da **201 + 401** el 40% de las veces.
3. **Dos bugs "de SQL" resultaron ser alcanzables por API** (S3-04 y S3-08): la vía es **encoger el horario después de vender los huecos** (`PUT /api/v1/horarios/{id}`), que la API permite sin ninguna restricción. **Esa es la precondición de negocio que hay que decidir**, y es más urgente que los parches de los 11 bugs originales.
4. **El seed falsea la medición de todo el sprint**: las franjas solapadas de Miguel (S3-05) y las 24 reservas de fin de semana de Miguel (S3-04/S3-08) **no son alcanzables por la API**, pero distorsionan el reporte y son el "escenario" de 3 de los 11 bugs.
5. **Tres bugs de los 11 son, en realidad, el mismo defecto con tres síntomas**: la falta de una **fuente única de verdad** — (i) la definición de "ocupa agenda" (S3-03, S3-10), (ii) la fusión de intervalos (S3-05, S3-07), (iii) la forma de los errores (S3-09, S3-12). Resolver las tres causas reduce 8 work items a 3.
6. **Ningún cambio de código de producción fue realizado.** El único artefacto temporal (un test JUnit de diagnóstico en `src/test/`) fue **eliminado**; `git status` no muestra modificaciones trackeadas y la base de datos quedó **exactamente en el estado de la semilla** (`88/1/3` reservas, `16` franjas, `0` bloqueos, reporte `overall=37.72`).
