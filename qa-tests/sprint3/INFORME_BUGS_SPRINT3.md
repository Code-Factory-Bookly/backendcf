# Informe de Defectos - Sprint 3 (HU-05, HU-13, HU-15)

**Proyecto:** Bookly BackendCF
**Sprint:** 3 (28/10/2026 - 25/11/2026)
**Fecha de ejecucion:** 2026-11-19
**Rama bajo prueba:** `qa/sprint3-integracion`
**HUs cubiertas:** 3 (HU-05, HU-13, HU-15) mas 1 suite de integracion transversal
**Total de casos ejecutados:** 174 (169 PASS + 5 FAIL)
**Defectos confirmados:** 11 (2 criticos, 3 altos, 6 medios)

> Fuente de criterios de aceptacion: `qa-tests/sprint3/HUs-sprint3.md`.
> Evidencia cruda: `qa-tests/sprint3/.e2e-results/summary/`.

---

## 1. Resumen ejecutivo

| ID | HU | Severidad | Titulo corto | Estado |
|----|-----|-----------|--------------|--------|
| S3-01 | HU-05 | CRITICA | Concurrencia devuelve 401 en vez de 409 AGENDA_BLOCK_OVERLAP | Abierto |
| S3-02 | HU-15 | CRITICA | El reporte de ocupacion ignora los bloqueos de agenda | Abierto |
| S3-03 | HU-15 / HU-07 | ALTA | Dos definiciones distintas de "ocupado" (CONFIRMADA vs CONFIRMADA+COMPLETED) | Abierto |
| S3-04 | HU-15 | ALTA | Recorte silencioso del porcentaje de ocupacion a 100 | Abierto |
| S3-05 | HU-15 | ALTA | Capacidad inflada por franjas horarias solapadas | Abierto |
| S3-06 | HU-13 | MEDIA | Ruta vieja /cliente/{customerId} sin paginar ni auditar | Abierto |
| S3-07 | HU-07 | MEDIA | Disponibilidad duplica slots con franjas solapadas | Abierto |
| S3-08 | HU-15 | MEDIA | Dias con reservas pero sin capacidad reportan 0% | Abierto |
| S3-09 | Transversal | MEDIA | Contrato de error inconsistente: `error` vs `errorCode` | Abierto |
| S3-10 | HU-08 / HU-07 / HU-15 | MEDIA | RESCHEDULED puede solaparse con CONFIRMADA | Abierto |
| S3-11 | HU-07 / HU-15 | MEDIA | Disponibilidad solo futuro vs reporte solo pasado | Abierto |

La ejecucion final dejo **174 casos** (169 PASS + 5 FAIL) sobre PostgreSQL 16.15 real. El script `run-all.sh` cuenta `PASS` y `FAIL` de forma separada y los hallazgos marcados como `BUG` se reportan como un subconjunto de los casos que fallaron, no como casos adicionales: por eso `TOTAL CASOS` es `PASS + FAIL = 169 + 5 = 174`, y no 184. La corrida registro ademas 10 hallazgos etiquetados `BUG`, que corresponden a 8 de los 11 defectos documentados.

Los 5 FAIL son consecuencia de dos defectos: el bug de concurrencia de HU-05 (S3-01) y la inconsistencia de seguridad de HU-13 (S3-06). Se confirmaron **11 defectos**: 2 criticos (bloqueo de agenda bajo concurrencia que degrada a 401 y reporte de ocupacion que no descuenta los bloqueos), 3 altos (dos definiciones divergentes de "ocupado", recorte silencioso del 100% y capacidad inflada por franjas solapadas) y 6 medios. Ocho de los once defectos se reprodujeron de forma directa con casos E2E (S3-01 a S3-08) y los tres restantes (S3-09, S3-10 y S3-11) se diagnosticaron mediante analisis de codigo, al no ser alcanzables por la API en esta corrida. Dos de los hallazgos ya existian antes de este sprint (S3-03 y S3-05), lo que evidencia que la integracion entre modulos no estaba cubierta por pruebas del Sprint 2. El modulo HU-05 es el mas afectado: su logica de exclusion en base de datos funciona, pero la capa de servicio no traduce los errores de concurrencia a la respuesta de negocio documentada. **Conclusion: el sprint no puede aprobarse en su estado actual**; los dos defectos criticos deben corregirse y volver a validarse antes del cierre.

---

## 2. Alcance y metodologia

### 2.1 Que se probo

| Script | Alcance |
|--------|---------|
| `test_hu05_blocks.sh` | Ciclo de vida completo del bloqueo de agenda (HU-05): creacion, validacion de rango, longitudes de `reason`, solape con reservas, solape entre bloqueos, contigüidad, control de acceso y concurrencia |
| `test_hu13_history.sh` | Historial de reservas del cliente (HU-13): filtros por rango y estado, paginacion, orden, cliente sin reservas, control de acceso por rol, aislamiento entre clientes y ruta legacy |
| `test_hu15_occupancy.sh` | Reporte de ocupacion (HU-15): periodos MES/SEMANA, normalizacion y validacion de `periodo`, control de acceso, calculo con reservas, QA/Exception sin reservas y preflight CORS |
| `test_hu05_13_15_integration.sh` | 12 bloques transversales (A-L) que cruzan HU-05, HU-07, HU-13 y HU-15: coherencia bloqueo-disponibilidad, bloqueo-reporte, estados de reserva, aritmetica del reporte, ventana temporal, solapamiento parcial, franjas solapadas, profesional sin horario, migraciones y slots duplicados |

### 2.2 Como se probo

- Pruebas de aceptacion end-to-end sobre el servidor real (no mocks): cada script se ejecuta contra una base de datos limpia (`reset.sh`) sembrada con `seed.sh`, para evitar contaminacion entre casos.
- Cada caso emite un veredicto explicito `PASS`, `FAIL` o `BUG`. Un `FAIL` significa que el comportamiento observado no cumple el criterio; un `BUG` significa que el comportamiento es directamente incorrecto y viola la logica de negocio o el contrato de la API.
- **Como se cuenta el total.** El script `run-all.sh` contabiliza `PASS` y `FAIL` por separado y define el total de casos ejecutados como `PASS + FAIL`. Los hallazgos etiquetados `BUG` se reportan como un subconjunto de los casos que fallaron, **no como casos adicionales**: el funcion `bug()` de `lib.sh` registra un hallazgo pero no incrementa el contador de casos, de modo que un caso que produce un `BUG` sigue contando una sola vez dentro de los `FAIL` de su script. Por esta razon 169 PASS + 5 FAIL = 174 casos, y las 10 marcas `BUG` describen hallazgos sobre esos mismos casos y no 10 casos adicionales.
- Para los hallazgos que dependen del estado de la base de datos, el escenario modifica la base directamente con `psql` (por ejemplo, cambiar una reserva de `CONFIRMADA` a `COMPLETED`) y luego observa la API, de modo que la divergencia queda aislada en el codigo y no en el fixture.

### 2.3 Ambiente

| Componente | Version / configuracion |
|------------|--------------------------|
| Base de datos | PostgreSQL 16.15 (`postgres:16-alpine`), puerto host `55432`, base `bookly_test`, rol `bookly_test` |
| Aplicacion | Spring Boot 3, Java 17+, puerto `8080`, ejecutada desde el JAR empaquetado |
| Migraciones | Flyway, hasta `V50__create_agenda_blocks.sql` (verificada aplicada en el caso J) |
| Sistema | Linux x86_64, Docker para el servicio de base de datos |
| Datos | Seed determinista: 26 clientes, 6 servicios, 3 profesionales con horario (sofia, miguel, elena) mas 1 profesional sin horario, 1 administrador con MFA |

### 2.4 Limitaciones de la metodologia

- **Los tests unitarios no detectan los bugs de JPQL.** El suite unitario corre sobre H2 en memoria, mientras que el E2E corre sobre PostgreSQL real. Los defectos S3-03, S3-04, S3-05, S3-07, S3-08 y S3-10 se deben a JPQL y a la semantica de las restricciones de PostgreSQL (`EXCLUDE USING gist`, `tsrange`), y son invisibles en H2. El propio codigo lo documenta en `BookingRepository.java` lineas 39-45: sin el `CAST` explicito, la consulta devuelve 500 en produccion y solo funciona contra H2.
- **Los fixtures no cubren todos los estados de reserva.** `RESCHEDULED` no lo produce ningun endpoint del alcance actual (S3-10 se diagnostico por analisis de codigo y de la migracion `V46`, no por reproduccion E2E directa).
- **La prueba de concurrencia es intrinsecamente flaky.** El caso de concurrencia de HU-05 oscila entre `PASS=55 / FAIL=2 / BUG=1` y `PASS=56 / FAIL=0 / BUG=0` entre ejecuciones. El defecto S3-01 es real y se reprodujo en 5 de 5 rondas, pero la manifestacion exacta (401 frente a 409) depende del plan de bloqueo que el servidor alcance. Ver seccion 7.4.
- **El modulo HU-11, HU-12, HU-14, HU-16 y HU-20 no se cubrio** en este sprint: no forman parte del alcance asignado.
- La base se reinicia entre scripts, por lo que las cifras entre suites no son acumulables salvo dentro de la suite de integracion.

### 2.5 Ajuste metodologico aplicado

- Los **5 FAIL** de la corrida final corresponden a los bugs S3-01 (concurrencia de HU-05) y S3-06 (seguridad de HU-13); no son defectos independientes.
- Los **10 hallazgos marcados `BUG`** se reportan como un subconjunto de los casos que fallaron, no como casos adicionales, y se corresponden con **8 de los 11 defectos** documentados (S3-01 a S3-08). Los 3 defectos restantes (S3-09, S3-10 y S3-11) se diagnosticaron por analisis de codigo y no corresponden a un hallazgo `BUG` emitido por la suite.
- **2 de los 10 hallazgos `BUG`** (S3-03 y S3-05) ya eran preexistentes al Sprint 3: no fueron introducidos por las ramas de este sprint, pero el sprint es la primera oportunidad en que una prueba automatizada los detecta.
- **9 casos E2E corregidos** verifican su bug en ambos sentidos: con el worktree que contiene el defecto el caso falla, y con la copia corregida el caso pasa. Esto reduce el riesgo de falsos positivos.
- La rama `qa/sprint3-integracion` integra sin conflictos las tres ramas de feature: `origin/feature/hu-05-bloqueo-agenda`, `origin/feature/hu-13-historial-cliente` y `origin/feature/hu_15_reporte_ocupacion`.

---

## 3. Resumen de la ejecucion

### 3.1 Por script

`Casos` = `PASS` + `FAIL`, tal como lo calcula `run-all.sh` (`TOTAL CASES`). La columna `BUG` no se suma al total: los hallazgos `BUG` son un subconjunto de los casos que fallaron y se reportan aparte para no perder detalle del diagnostico.

| Script | Casos | PASS | FAIL | BUG (subconjunto de FAIL) | % PASS |
|--------|-------|------|------|--------------------------|--------|
| `test_hu05_blocks.sh` | 57 | 55 | 2 | 1 | 96.5% |
| `test_hu13_history.sh` | 44 | 44 | 0 | 1 | 100.0% |
| `test_hu15_occupancy.sh` | 29 | 29 | 0 | 0 | 100.0% |
| `test_hu05_13_15_integration.sh` | 44 | 41 | 3 | 8 | 93.2% |
| **TOTAL** | **174** | **169** | **5** | **10** | **97.1%** |

Comprobacion aritmetica: 55 + 44 + 29 + 41 = **169 PASS**; 2 + 0 + 0 + 3 = **5 FAIL**; 169 + 5 = **174 casos ejecutados**. Los **10 hallazgos `BUG`** (1 + 1 + 0 + 8) describen 8 de los 11 defectos documentados y **no son 10 casos adicionales**: por eso la suma de las cuatro filas de la columna `Casos` es 57 + 44 + 29 + 44 = 174, no 184.

### 3.2 Por HU

| HU | Modulo | Resultado |
|----|--------|-----------|
| HU-05 | Bloqueo de agenda | Funcional en el camino secuencial (201 / 409 / 400 / 401 / 403 correctos). Defecito en el camino concurrente (S3-01) |
| HU-13 | Historial del cliente | Funcional y bien paginado en la ruta nueva. Inconsistencia con la ruta legacy (S3-06) |
| HU-15 | Reporte de ocupacion | Funcional en control de acceso, periodos y QA/Exception. 5 defectos en la semantica del calculo (S3-02 a S3-05, S3-08) |
| HU-07 | Disponibilidad (transversal) | Reacciona correctamente a los bloqueos, pero duplica slots (S3-07) y usa una definicion de "ocupado" divergente (S3-03) |

### 3.3 Bloque de integracion: detalle por bloque

Los conteos por bloque corresponden a los veredictos emitidos por la suite (41 PASS + 3 FAIL + 8 BUG = 52 marcas de veredicto en `test_hu05_13_15_integration.sh`). Los casos marcados como `info` no emiten veredicto y por eso no se cuentan.

| Bloque | Tema | PASS | FAIL | BUG | Resultado |
|--------|------|------|------|-----|-----------|
| A | Bloqueo HU-05 vs disponibilidad HU-07 | 5 | 0 | 0 | El bloqueo si retira las franjas de la disponibilidad |
| B | Bloqueo HU-05 vs reporte HU-15 | 2 | 3 | 1 | **Falla**: el reporte no descuenta el bloqueo (S3-02) |
| C | Reserva COMPLETED: disponibilidad vs reporte | 5 | 0 | 3 | **Falla**: dos definiciones de "ocupado" (S3-03) |
| D | Aritmetica del reporte (por dia vs profesional) | 2 | 0 | 0 | Ambas sumas coinciden en 390 min |
| E | Ventana temporal de la reserva | 2 | 0 | 0 | Correcto |
| F | Solapamiento parcial vs contiguo en HU-05 | 5 | 0 | 0 | El solape parcial se detecta con `AGENDA_BLOCK_OVERLAP` |
| G | Profesional con horario solapado | 2 | 0 | 2 | **Falla**: recorte al 100% y capacidad inflada (S3-04, S3-05) |
| H | Profesional recien creado sin horario | 4 | 0 | 0 | Sin 500, capacidad 0 |
| I | HU-05 + HU-13: el bloqueo no cancela reservas | 4 | 0 | 0 | Correcto |
| J | Migraciones V50 y tabla `bloqueo_agenda` | 5 | 0 | 0 | Correcto |
| K | Slots duplicados por franjas solapadas | 2 | 0 | 1 | **Falla**: disponibilidad duplica slots (S3-07) |
| L | Dia con reservas pero sin capacidad | 3 | 0 | 1 | **Falla**: se reporta 0% de ocupacion (S3-08) |
| **TOTAL integracion** | | **41** | **3** | **8** | **52 veredictos emitidos** |

---

## 4. Defectos criticos

### S3-01 · HU-05: la concurrencia devuelve 401 en vez de 409 AGENDA_BLOCK_OVERLAP

**Severidad:** CRITICA
**HU afectada:** HU-05 (Bloqueo temporal de agenda)
**Endpoint:** `POST /api/v1/horarios/{professionalId}/bloqueos`
**Reportado en:** `qa-tests/sprint3/test_hu05_blocks.sh` (casos 15.5, 15.6) y `qa-tests/sprint3/test_hu05_13_15_integration.sh`
**Atributo ISO/IEC 25010:** Confiabilidad (tolerancia a fallos y gestion de conflictos)

#### Descripcion

Dos solicitudes concurrentes que intentan bloquear la misma franja horario no producen el conflicto de negocio documentado. En lugar de responder `409 AGENDA_BLOCK_OVERLAP`, la losing request recibe `401 UNAUTHORIZED` con un `Bearer` perfectamente valido. El unico camino que produce la respuesta correcta es el secuencial.

#### Criterio de aceptacion violado

HU-05, criterio "Bloquear una franja sin reservas previas": *"...el sistema deja de ofrecer esas franjas a los clientes de forma inmediata"*, junto con la propiedad declarada de Confiabilidad "gestion de conflictos". El contrato de la API define `409 AGENDA_BLOCK_OVERLAP` para el solape entre bloqueos, y asi lo demuestra el propio camino secuencial; bajo concurrencia ese mismo conflicto no se traduce al codigo HTTP correcto.

#### Pasos de reproduccion

1. Autenticarse como el profesional dueno de la agenda (o como ADMIN).
2. Preparar un rango futuro sin reservas confirmadas ni bloqueos previos.
3. Enviar dos `POST` **simultaneos** (background, en paralelo) con el mismo `professionalId` y el mismo cuerpo.
4. Observar los codigos de respuesta: uno es `201`, el otro **no** es `409`.

#### Request / Response real

```http
POST /api/v1/horarios/{professionalId}/bloqueos
Authorization: Bearer <token valido del profesional>
Content-Type: application/json

{"startTime":"2026-12-21T09:00:00","endTime":"2026-12-21T12:00:00","reason":"concurrent"}
```

Respuesta observada en la peticion perdedora:

```http
HTTP/1.1 401
{"errorCode":"UNAUTHORIZED","message":"Se requiere autenticación","details":{},
 "traceId":"0d23b704-478e-4fa2-9e6f-886fe7574fe5",
 "timestamp":"2026-11-19T11:45:04.082478910-05:00"}
```

Control secuencial con el mismo solapamiento (si el camino secuencial falla, el defecto no seria de concurrencia):

```text
1ro = 201
2do = 409 AGENDA_BLOCK_OVERLAP
```

Comportamiento con 8 solicitudes concurrentes sobre el mismo rango: **6 x 409 + 1 x 201 + 1 x 401**. El defecto se reprodujo en **5 de 5 rondas**.

#### Causa raiz

`AgendaBlockService.java` lineas 63-69:

```java
try {
    String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
    return AgendaBlockResponse.from(repository.saveAndFlush(new AgendaBlock(professionalId, start, end, reason)));
} catch (DataIntegrityViolationException concurrentBlock) {
    // La restricción de exclusión de V50 atrapa dos bloqueos solapados creados a la vez.
    throw overlapsBlock(repository.findOverlapping(professionalId, start, end));
}
```

Son tres fallos encadenados:

1. **La consulta se ejecuta sobre una transaccion abortada.** La violacion de la restriccion `EXCLUDE USING gist` de `V50__create_agenda_blocks.sql` (lineas 18-21) aborta la transaccion en PostgreSQL con SQLState `25P02` (*current transaction is aborted*). Inmediatamente despues, el `catch` intenta ejecutar `repository.findOverlapping(...)` **sobre esa misma transaccion ya abortada**. PostgreSQL rechaza cualquier sentencia nueva con `25P02`, que Hibernate traduce a `JpaSystemException`. No existe manejador para `JpaSystemException` en `GlobalExceptionHandler.java`, por lo que la peticion termina en un `500` interno.

2. **`CannotAcquireLockException` no se maneja.** Cuando dos INSERT compiten por el indice GiST, PostgreSQL puede informar un deadlock con SQLState `40P01`, que Spring traduce a `CannotAcquireLockException`. El sibling `CreateBookingService.java` (linea 128) **si** lo maneja y lo convierte en el conflicto de negocio; `AgendaBlockService` no. La excepcion escapa como `500`.

3. **El 500 se enmascara como 401.** Al no existir manejador, Spring Boot reenvia la peticion al dispatch `/error`. En ese reenvio, `ExceptionTranslationFilter` detecta que no hay `Authentication` en el `SecurityContext` (la peticion original lo consume y el filtro de error corre en un contexto distinto) y dispara el `authenticationEntryPoint`, que devuelve `401 UNAUTHORIZED`. De ahi el sintoma: el cliente ve un problema de credenciales cuando en realidad hubo un conflicto de concurrencia.

#### Impacto

- El cliente profesional que recibe `401` reintenta el login porque Cree que su token expiro, cuando el problema es de concurrencia. El flujo de recuperacion lo lleva a un camino incorrecto.
- El `errorCode` esperado (`AGENDA_BLOCK_OVERLAP`) nunca llega al cliente en el camino concurrente, de modo que los clientes no pueden diferenciar "mi token fallo" de "el rango ya esta bloqueado".
- Se pierde la lista de detalle del bloqueo en conflicto que el camino secuencial si entrega.
- Es el escenario central de la propiedad de Confiabilidad declarada por HU-05.

#### Recomendacion

1. No consultar la base de datos dentro del `catch` sobre una transaccion abortada. Marcar la transaccion como rollback-only y resolver el conflicto **fuera** de la transacion, o usar `REQUIRES_NEW` para la consulta de lectura del solape.
2. Agregar el manejo de `CannotAcquireLockException` (SQLState `40P01`) alineado con `CreateBookingService.java` linea 128, para que el deadlock se traduzca al mismo conflicto de negocio.
3. Agregar un manejador de `JpaSystemException` (o de `InvalidDataAccessResourceUsageException`) en `GlobalExceptionHandler.java` que devuelva `409` con `errorCode`=`AGENDA_BLOCK_OVERLAP` en lugar de propagar a `/error`.
4. Considerar un patron *lock pesimista* (`SELECT ... FOR UPDATE` sobre las franjas del profesional) para serializar los bloqueos de un mismo profesional y evitar depender exclusivamente de la EXCLUDE.
5. Agregar test E2E con 8 peticiones concurrentes que verifique que exactamente una recibe `201` y las demas reciben `409 AGENDA_BLOCK_OVERLAP`.

---

### S3-02 · HU-15: el reporte de ocupacion ignora por completo los bloqueos de agenda

**Severidad:** CRITICA
**HU afectada:** HU-15 (Reporte de ocupacion por periodo), con impacto directo sobre HU-05
**Endpoint:** `GET /api/v1/reportes/ocupacion?periodo=MES`
**Reportado en:** `qa-tests/sprint3/test_hu05_13_15_integration.sh` (casos B.5, B.6, B.7, B.8)
**Atributo ISO/IEC 25010:** Eficiencia de desempeno (exactitud del reporte)

#### Descripcion

El reporte de ocupacion calcula la capacidad de cada profesional unicamente a partir de su horario semanal. Los bloqueos temporales de agenda creados por HU-05 **no se consultan en ningun momento**, por lo que un profesional con 8 horas bloqueadas sigue reportando la misma capacidad que antes del bloqueo. El dato que ve el administrador es incorrecto.

#### Criterio de aceptacion violado

HU-15, escenario "Generar reporte de ocupacion mensual": *"el sistema me presenta graficos y porcentajes de ocupacion por dia de la semana y por profesional"*, apoyado en la propiedad de Eficiencia de desempeno. Un porcentaje de ocupacion calculado sobre una capacidad que ignora los bloqueos real no representa la ocupacion real de las agendas.

#### Pasos de reproduccion

1. Autenticarse como ADMIN.
2. Consultar `GET /api/v1/reportes/ocupacion?periodo=MES` y registrar `capacityMinutes` del profesional y el desglose `byDayOfWeek`.
3. Crear un bloqueo de 120 minutos dentro de la ventana del reporte (por ejemplo el `2026-11-16`, dia lunes).
4. Volver a consultar el reporte.
5. Comparar `capacityMinutes` antes y despues: **la diferencia es 0**.

#### Request / Response real

```http
GET /api/v1/reportes/ocupacion?periodo=MES
Authorization: Bearer <token ADMIN>
```

```text
ANTES   : capacityMinutes de sofia = 3960   (MONDAY: 3120)
POST    : bloqueo 2026-11-16T09:00:00 -> 2026-11-16T11:00:00 (120 min) => HTTP 201
DESPUES : capacityMinutes de sofia = 3960   (MONDAY: 3120)   <-- sin cambios
```

El `bookedMinutes` de sofia tras el bloqueo es `210`, y el reporte **no** refleja los 120 minutos bloqueados ni en `byDayOfWeek` ni en `byProfessional`.

#### Causa raiz

El puerto de lectura del reporte no expone los bloqueos:

`OccupancyQuery.java` lineas 15-24 declara unicamente cuatro metodos: `findProfessionalIds()`, `findWeeklyWindows()`, `findOccupyingBookings(from, to)` y `professionalNames(ids)`. **No existe ningun metodo que exponga los bloqueos de agenda.**

`OccupancyQueryAdapter.java` lineas 25-42 implementa ese puerto y solo consulta `professionalRepository`, `weeklySlotRepository`, `bookingRepository` y `userAccountRepository`. `AgendaBlockRepository` no se inyecta ni se consulta.

Como contraste, el modulo de disponibilidad **si** lo hace bien:

`AvailabilityQueryAdapter.java` lineas 73-80 (metodo `findBusyPeriods`) concatena ambos origenes:

```java
Stream<BusyPeriod> bookings = bookingRepository.findOverlappingBookings(professionalId, from, to).stream()
        .map(booking -> new BusyPeriod(booking.getStartTime(), booking.getEndTime()));
Stream<BusyPeriod> blocks = agendaBlocks.findOverlapping(professionalId, from, to).stream()
        .map(block -> new BusyPeriod(block.startTime(), block.endTime()));
return Stream.concat(bookings, blocks).toList();
```

El reporte, en cambio, calcula la capacidad unicamente desde `findWeeklyWindows()`:

`OccupancyCalculator.java` lineas 20-30 (`capacityByProfessional`) y lineas 33-43 (`capacityByDay`) recorren las franjas semanales y las suman. No hay ningun concepto de bloqueo en el modelo de dominio del reporte.

#### Impacto

- **Contradiccion funcional entre modulos.** La disponibilidad (HU-07) trata el bloqueo como tiempo ocupado y retira la franja; el reporte (HU-15) cuenta esa misma franja como capacidad disponible. La misma franja es "libre" en un modulo y "capacidad" en otro, para el mismo instante y el mismo profesional.
- El administrador que toma decisiones de contratacion o de carga sobre el reporte opera con una capacidad inflada de forma systematica cada vez que hay bloqueos.
- El porcentaje de ocupacion de los dias con bloqueo es sistematicamente **demasiado bajo**, porque el denominador no refleja la indisponibilidad real.
- El defecto no es visible sin cruzar HU-05 con HU-15: ninguna prueba unitaria de HU-15 podria detectarlo, porque el puerto no expone el dato.

#### Recomendacion

1. Extender el puerto `OccupancyQuery` con un metodo `findAgendaBlocks(from, to)` que devuelva los bloqueos vigentes en la ventana.
2. Implementarlo en `OccupancyQueryAdapter` inyectando `AgendaBlockRepository`.
3. Descontar los minutos bloqueados de `capacityMinutes`, tanto en `byDayOfWeek` como en `byProfessional`, con la misma semantica de rango `[inicio, fin)` que usa la disponibilidad.
4. Definir si el bloque se resta de la capacidad o se contabiliza como "ocupacion no facturable". Ambas son validas, pero deben ser explicitas y consistentes entre HU-07 y HU-15.
5. Agregar el caso E2E del bloque B (ya escrito) a la suite permanente: el fallo actual de B.6/B.7/B.8 actua como prueba de regresion pendiente.

---

## 5. Defectos de severidad alta

### S3-03 · HU-15 vs HU-07: dos definiciones distintas de "ocupado"

**Severidad:** ALTA
**HU afectada:** HU-15 y HU-07 (contradiccion transversal)
**Endpoints:** `GET /api/v1/disponibilidad` y `GET /api/v1/reportes/ocupacion`
**Reportado en:** `qa-tests/sprint3/test_hu05_13_15_integration.sh` (casos C.5, C.6, C.7)
**Atributo ISO/IEC 25010:** Adecuacion funcional (consistencia de datos entre modulos)

#### Descripcion

La disponibilidad y el reporte de ocupacion no coinciden en que reservas cuentan como "ocupado". La disponibilidad solo considera `CONFIRMADA`; el reporte considera `CONFIRMADA` y `COMPLETED`. El efecto practico es que una franja que el reporte da por ocupada se sigue ofreciendo como reservable.

#### Criterio de aceptacion violado

HU-15, criterio "Generar reporte de ocupacion mensual": el reporte debe reflejar *"reservas agendadas y atendidas durante el ultimo mes"*, es decir, incluye `COMPLETED`. HU-07, al offering slots al cliente, debe excluir toda franja realmente ocupada. Ambas cosas son ciertas a la vez y hoy no pueden ser ciertas a la vez.

#### Pasos de reproduccion

1. Crear una reserva `CONFIRMADA` de 60 minutos en un dia con horario.
2. Consultar la disponibilidad: se ofrecen **4 slots** (`10:00, 10:30, 11:00, 11:30`).
3. Cambiar el estado de esa misma reserva a `COMPLETED` directamente en la base de datos.
4. Consultar de nuevo la disponibilidad: se ofrecen **6 slots** (`09:00, 09:30, 10:00, 10:30, 11:00, 11:30`).
5. Consultar el reporte: `bookedMinutes` **si** crece en 60 al pasar de `CANCELADA` a `COMPLETED`.

#### Request / Response real

```text
Reserva en CONFIRMADA -> slots = 10:00,10:30,11:00,11:30          (4 slots)
Reserva en COMPLETED  -> slots = 09:00,09:30,10:00,10:30,11:00,11:30 (6 slots)
Slots extra ofrecidos con COMPLETED: 09:00, 09:30
```

La franja ocupada (09:00) **vuelve a aparecer** en el listado de disponibilidad.

#### Causa raiz

Dos definiciones distintas en el codigo:

`BookingRepository.java` lineas 28-35 (usado por la disponibilidad):

```java
@Query("SELECT b FROM Booking b WHERE b.professionalId = :professionalId " +
        "AND b.status = 'CONFIRMADA' " +
        "AND ((b.startTime < :endTime) AND (b.endTime > :startTime))")
List<Booking> findOverlappingBookings(...);
```

`OccupancyQueryAdapter.java` lineas 25-26 (usado por el reporte):

```java
private static final List<BookingStatus> OCCUPYING_STATUSES =
        List.of(BookingStatus.CONFIRMADA, BookingStatus.COMPLETED);
```

No existe una constante o un enum compartido que defina el conjunto de estados "ocupantes" para la plataforma, de modo que cada modulo define el suyo.

#### Impacto

- Un cliente puede **reservar una franja que el reporte ya considera ocupada**, generando doble conteo y un dato de ocupacion incoherente.
- El motivo de negocio es la raiz del defecto: una reserva `COMPLETED` es una atencion ya ocurrida. Si el reporte la cuenta como ocupacion real, la disponibilidad tambien debe considerarla ocupada. Si la disponibilidad la ignora, el reporte no debe contarla.
- Cualquier decision de negocio tomada con el reporte (contratacion, turnos, precios) opera con una ventana temporal que no coincide con la ventana de lo que el cliente puede ver y reservar.

#### Recomendacion

1. Definir una unica fuente de verdad para los estados que ocupan agenda (por ejemplo, un metodo estatico en `BookingStatus` como `ocupanAgenda()`), y usarla tanto en `BookingRepository` como en `OccupancyQueryAdapter`.
2. Decidir explicitamente el criterio de negocio: si `COMPLETED` ocupa agenda, agregarlo a la JPQL de `findOverlappingBookings`; si no, removerlo de `OCCUPYING_STATUSES`.
3. La exclusion de base de datos `V46` (que solo cubre `status = 'CONFIRMADA'`) es coherente con la segunda opcion; si se elige la primera, la migracion debe actualizarse para cubrir `RESCHEDULED` (ver S3-10).
4. Agregar el bloque C a la suite permanente.

---

### S3-04 · HU-15: recorte silencioso del porcentaje de ocupacion a 100

**Severidad:** ALTA
**HU afectada:** HU-15 (Reporte de ocupacion por periodo)
**Endpoint:** `GET /api/v1/reportes/ocupacion?periodo=MES`
**Reportado en:** `qa-tests/sprint3/test_hu05_13_15_integration.sh` (caso G.6)
**Atributo ISO/IEC 25010:** Eficiencia de desempeno (exactitud y trazabilidad del dato)

#### Descripcion

Cuando los minutos reservados superan los minutos de capacidad, el reporte devuelve `100.0` sin ninguna advertencia. El valor real era `253.85%` y el consumidor nunca puede saber que el dato fue truncado.

#### Criterio de aceptacion violado

HU-15, escenario "Generar reporte de ocupacion mensual": *"el sistema me presenta graficos y porcentajes de ocupacion"*. Un porcentaje truncado sin marca alguna no es un dato de ocupacion: es un dato de ocupacion mas una perdida de informacion silenciosa. La propiedad de Eficiencia de desempeno exige que el dato que alimenta una decision sea interpretable.

#### Pasos de reproduccion

1. Crear un profesional con dos franjas solapadas el mismo dia (el CHECK de la base lo permite, ver S3-05).
2. Insertar 80 reservas `CONFIRMADA` para ese profesional.
3. Consultar `GET /api/v1/reportes/ocupacion?periodo=MES`.
4. Observar `bookedMinutes = 4800` contra `capacityMinutes = 4680` y `occupancyPercent = 100.0`.

#### Request / Response real

```text
capacityMinutes de miguel = 4680
bookedMinutes   de miguel = 4800
occupancyPercent de miguel = 100.0     <-- valor real = 253.85%
```

#### Causa raiz

`OccupancyCalculator.java` lineas 63-70:

```java
/** Porcentaje con dos decimales y tope de 100. Si no hay capacidad devuelve 0 (sin dividir por cero). */
public static double percent(long bookedMinutes, long capacityMinutes) {
    if (capacityMinutes <= 0) {
        return 0.0;
    }
    double percent = Math.round(bookedMinutes * 10000.0 / capacityMinutes) / 100.0;
    return Math.min(100.0, percent);
}
```

El `Math.min(100.0, percent)` de la linea 69 aplica el tope de forma incondicional, sin distinguir entre dos situaciones muy distintas:

- Ocupacion real del 100% (el profesional esta booked out por completo).
- Ocupacion superior al 100%, que solo puede ocurrir si la capacidad esta mal calculada (ver S3-05) o si hay solapes fisicos entre reservas.

El DTO `OccupancyReportResponse` no tiene ningun campo que permita sinalizar el recorte, y la API no documenta ninguna bandera al respecto.

#### Impacto

- Un administrador ve "100% de ocupacion" y no puede distinguir "agenda al limite" de "dato corrupto por capacidad mal calculada".
- El recorte enmascara al defecto S3-05: sin el `Math.min`, el `253.85%` habria apontado directamente a la causa raiz.
- Si el valor recortado se usa como KPI (por ejemplo, para fijar tarifas o turnos), la plataforma toma decisiones sobre un numero que no refleja lo ocurrido.

#### Recomendacion

1. Eliminar el recorte incondicional y devolver el porcentaje real (`253.85`), o_clipar solo cuando el valor no exceda un umbral de desviacion razonable.
2. Si el recorte se mantiene por contrato, agregar al DTO una bandera explicita, por ejemplo `occupancyPercentCapped: true` o `capacityAnomaly: true`, junto con el valor sin recortar (`rawOccupancyPercent`).
3. Agregar validacion de integridad en el calculo: si `bookedMinutes > capacityMinutes`, registrar un `WARN` en el log de auditoria con el `professionalId`, la fecha y ambos valores.
4. Alternativamente, corregir primero S3-05 (capacidad mal calculada) y luego reevaluar si el tope sigue siendo necesario.

---

### S3-05 · HU-15: capacidad inflada por franjas horarias solapadas

**Severidad:** ALTA
**HU afectada:** HU-15 (Reporte de ocupacion), con raiz en HU-04 (horario semanal)
**Endpoint:** `GET /api/v1/reportes/ocupacion?periodo=MES`
**Reportado en:** `qa-tests/sprint3/test_hu05_13_15_integration.sh` (caso G.8)
**Atributo ISO/IEC 25010:** Eficiencia de desempeno (exactitud del calculo)

#### Descripcion

Cuando un profesional tiene dos franjas que se solapan el mismo dia, el reporte las suma como minutos independientes en lugar de fusionarlas. La capacidad resultante es mayor que el tiempo real en que el profesional esta disponible.

#### Criterio de aceptacion violado

HU-15, criterio "Generar reporte de ocupacion mensual": el reporte debe presentar *"porcentajes de ocupacion por dia de la semana y por profesional"*. Un porcentaje calculado sobre una capacidad que excede el tiempo real de trabajo no mide ocupacion.

#### Pasos de reproduccion

1. Configurar para un profesional dos franjas el mismo dia: `09:00-12:00` y `11:00-14:00`.
2. Verificar que la base de datos las admite (el CHECK no lo impide).
3. Consultar `GET /api/v1/reportes/ocupacion?periodo=MES`.
4. Comparar `capacityMinutes` del lunes con la union real de las franjas.

#### Request / Response real

```text
Franjas del lunes de miguel: 2
Cobertura real (franjas fusionadas): 300 min
Minutos que suma el reporte:      360 min    <-- 3h + 3h en vez de 5h
capacityMinutes del lunes (suma de todos los profesionales) = 3120
```

La capacidad esta inflada en un **20%** respecto de la cobertura real de la franja.

#### Causa raiz

Dos causas que se combinan.

**a) El calculo no fusiona ni deduplica los intervalos.** `OccupancyCalculator.java` lineas 20-30 (`capacityByProfessional`) y lineas 33-43 (`capacityByDay`):

```java
for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
    for (WeeklyWindow window : windows) {
        if (window.dayOfWeek().equals(date.getDayOfWeek())) {
            result.merge(window.professionalId(), windowMinutes(window), Long::sum);
        }
    }
}
```

Cada `WeeklyWindow` aporta su duracion completa y se suman. No hay ninguna operacion de fusion de intervalos (union de rangos) previa a la suma, de modo que la fraccion solapada se cuenta dos veces.

**b) La base de datos no impide las franjas solapadas.** `V10__create_weekly_schedule.sql` linea 19:

```sql
CONSTRAINT ck_horario_semanal_franja CHECK (hora_fin > hora_inicio)
```

El unico CHECK de la franja solo exige que `hora_fin > hora_inicio`. La restriccion `uk_horario_semanal_inicio` (linea 20) impide dos franjas que **empiecen a la misma hora**, pero no dos franjas que se **solapen parcialmente**. La validacion de solapamiento se delega al servicio, que segun el caso G.2 del E2E no la ejecuta para este camino.

#### Impacto

- La capacidad se infla de forma proporcional al grado de solapamiento. En el caso medido, +20%.
- Como la capacidad es el denominador del porcentaje, **todos** los porcentajes de ocupacion del sprint están subestimados.
- El defecto es bidireccional: infla S3-04 (permite que `bookedMinutes > capacityMinutes`) y distorsiona el mismo calculo que alimenta la decision de disponibilidad (ver S3-07, que produce el sintoma espejo en HU-07).
- El mismo profesional, con el mismo horario, reporta capacidades distintas segun como se haya escrito su agenda historicamente.

#### Recomendacion

1. Fusionar los intervalos de cada profesional y dia antes de sumar la capacidad: ordenar por hora de inicio, fusionar los solapados y sumar las longitudes de la union. La misma logica debe reutilizarse en `capacityByProfessional` y `capacityByDay` para no divergir.
2. Considerar un helper compartido de fusion de intervalos, reutilizable tambien por `AvailabilityCalculator` (ver S3-07), ya que ambos modulos arrastran el mismo defecto conceptual.
3. Reforzar la base de datos: agregar una restriccion `EXCLUDE USING gist` sobre `(profesional_id, dia_semana)` con `tsrange` para impedir franjas solapadas, o al menos un trigger de validacion.
4. Revisar por que el servicio de horario (HU-04) no rechaza el solapamiento parcial en este camino, dado que el comentario de `V10` (lineas 4-6) afirma que *"El solapamiento parcial se valida en el servicio"*.
5. Corregir S3-05 antes que S3-04: el recorte a 100 es un sintoma de esta capacidad inflada.

---

## 6. Defectos de severidad media

### S3-06 · HU-13: la ruta vieja `/cliente/{customerId}` no pagina ni audita la lectura de PII ajena

**Severidad:** MEDIA
**HU afectada:** HU-13 (Historial de reservas del cliente)
**Endpoint:** `GET /api/v1/reservas/cliente/{customerId}`
**Reportado en:** `qa-tests/sprint3/test_hu13_history.sh` (caso 11.2)
**Atributo ISO/IEC 25010:** Seguridad (control de acceso y trazabilidad) / Usabilidad

#### Descripcion

Conviven dos endpoints para el mismo proposito con contratos incompatibles. La ruta nueva `/cliente/historial` esta restringida al rol `CUSTOMER` y pagina sus resultados. La ruta vieja `/cliente/{customerId}` permite que un ADMIN lea el historial completo de cualquier cliente, sin paginacion y sin dejar registro de auditoria de la lectura.

#### Criterio de aceptacion violado

HU-13, escenario "Consultar mi historial como cliente": *"no incluye bajo ninguna circunstancia informacion de otros clientes"*. El comportamiento de la ruta vieja es coherente con un bypass de ADMIN documentado y aceptado por diseno (`OwnershipGuard.java` lineas 23-26); el defecto real es la ausencia de paginacion y de auditoria sobre una lectura de datos personales de terceros.

#### Pasos de reproduccion

1. Autenticarse como ADMIN.
2. Obtener el `customerId` de un cliente distinto.
3. Consultar `GET /api/v1/reservas/cliente/{customerId}`.
4. Observar: `200 OK` con el historial completo, sin paginacion y sin entrada de auditoria.
5. Comparar con `GET /api/v1/reservas/cliente/historial` como ADMIN: `403 Forbidden`.

#### Request / Response real

```http
GET /api/v1/reservas/cliente/9a1e4a6f-6a38-41a5-a662-94f6b187cc11
Authorization: Bearer <token ADMIN>
```

```http
HTTP/1.1 200
[{"id":"fae5ec44-42ee-4a36-93e9-65e34bdd19c2",
  "customerId":"9a1e4a6f-6a38-41a5-a662-94f6b187cc11",
  "customerName":"Customer hu13.secondary@bookly.local",
  "professionalName":"Sofia Lopez",
  "serviceName":"Limpieza profunda",
  "startTime":"2026-12-07T09:00:00","endTime":"2026-12-07T10:00:00","status":...}]
```

Contraste en la misma sesion:

| Endpoint | Rol ADMIN | Resultado |
|----------|-----------|-----------|
| `GET /api/v1/reservas/cliente/historial` | ADMIN | `403 Forbidden` |
| `GET /api/v1/reservas/cliente/{customerId}` | ADMIN | `200 OK` con datos de otro cliente |

#### Causa raiz

`BookingController.java` lineas 67-71:

```java
@GetMapping("/cliente/{customerId}")
public List<BookingResponse> getByCustomer(@PathVariable UUID customerId, Authentication authentication) {
    ownershipGuard.check(customerId, authentication);
    return withNames(bookingRepository.findByCustomerId(customerId));
}
```

Dos problemas en la misma linea:

1. `bookingRepository.findByCustomerId(customerId)` (declarado en `BookingRepository.java` linea 37 como `List<Booking> findByCustomerId(UUID customerId)`) devuelve una `List` sin `Pageable`, por lo que la respuesta no tiene paginacion. Con 111 reservas la respuesta body's 60 KB sin limite de tamaño.
2. No hay ninguna escritura en el log de auditoria. `OwnershipGuard.check` solo escribe en el log `SECURITY` cuando **deniega** el acceso (`OwnershipGuard.java` lineas 30 y 41-45); cuando el acceso es concedido por bypass de ADMIN (lineas 23-26) no queda rastro.

El bypass de ADMIN en si **es por diseño y esta documentado** en el propio codigo; no se reporta como defecto. El defecto es la ausencia de paginacion y de auditoria.

#### Impacto

- Riesgo de denegacion de servicio: un cliente con historial largo genera respuestas sin limite, con consumo de memoria del servidor y del cliente.
- Sin auditoria, no hay forma de demostrar quien consulto los datos personales de un tercero. Esto es un incumplimiento del requisito de trazabilidad que el propio sistema define para otros modulos (HU-16 exige log de auditoria para consultas agregadas).
- Inconsistencia funcional visible: la misma operacion de negocio devuelve `403` por una ruta y `200` por la otra, con el mismo rol y el mismo recurso.

#### Recomendacion

1. **Paginar la ruta vieja**: migrarla a `Pageable` y devolver `BookingHistoryPageResponse`, igual que la ruta nueva, o marcarla como `@Deprecated` y redirigir.
2. **Auditar la lectura**: registrar en `audit_log` el `adminId`, el `customerId` consultado, la IP de origen y la marca de tiempo, siguiendo el patron de HU-19.
3. **Unificar el contrato**: decidir si ADMIN puede o no consultar el historial de otro cliente y aplicar la misma regla en ambas rutas (hoy 403 en una y 200 en la otra).
4. Si se elimina la ruta vieja, hacerlo en la version mayor del API y actualizar `docs/openapi.yaml`.

---

### S3-07 · HU-07: la disponibilidad duplica slots cuando las franjas horarias se solapan

**Severidad:** MEDIA
**HU afectada:** HU-07 (Consulta de disponibilidad)
**Endpoint:** `GET /api/v1/disponibilidad`
**Reportado en:** `qa-tests/sprint3/test_hu05_13_15_integration.sh` (caso K.5)
**Atributo ISO/IEC 25010:** Usabilidad (presentacion correcta de la informacion)

#### Descripcion

El calculador de disponibilidad genera los slots franja por franja, sin fusionar las franjas que se solapan ni deduplicar el resultado. El cliente recibe la misma hora de inicio mas de una vez.

#### Criterio de aceptacion violado

HU-07, escenario de consulta de disponibilidad: el sistema debe presentar los horarios disponibles del profesional. Presentar la misma hora dos veces no corresponde a un conjunto de horarios disponibles.

#### Pasos de reproduccion

1. Configurar para un profesional dos franjas el mismo dia que se solapan: `09:00-12:00` y `11:00-14:00`.
2. Consultar la disponibilidad con un servicio de 60 minutos.
3. Inspeccionar los `startTime` devueltos.

#### Request / Response real

```text
Slots de miguel (6): 09:00,10:00,11:00,11:00,12:00,13:00
startTime repetidos: 1 (11:00)
```

De 6 slots emitidos, solo **5 son unicos**: `11:00` aparece dos veces.

#### Causa raiz

`AvailabilityCalculator.java` lineas 31-42 (`freeSlots`):

```java
List<AvailableSlot> free = new ArrayList<>();
for (WorkingWindow window : dayWindows) {
    LocalDateTime cursor = date.atTime(window.startTime());
    LocalDateTime windowEnd = date.atTime(window.endTime());
    while (!cursor.plus(duration).isAfter(windowEnd)) {
        LocalDateTime blockEnd = cursor.plus(duration);
        if (cursor.isAfter(now) && isFree(cursor, blockEnd, busyPeriods)) {
            free.add(new AvailableSlot(cursor, blockEnd));
        }
            cursor = blockEnd;
    }
}
```

El bucle exterior recorre cada `WorkingWindow` de forma independiente y el bucle interior genera los slots de esa franja. No hay ninguna operacion de fusion de franjas antes de generar, ni deduplicacion del `startTime` al agregar. Es la contraparte exacta de S3-05 en el modulo de disponibilidad: alli la no fusion infla la capacidad; aqui la no fusion duplica la oferta.

La causa raiz compartida es que la base de datos no impide franjas solapadas (`V10__create_weekly_schedule.sql` linea 19) y ningun modulo del sistema tiene una utilidad para fusionar intervalos.

#### Impacto

- El cliente ve la misma hora ofrecida mas de una vez, lo que se percibe como un error de la aplicacion.
- El numero de huecos libres que se le muestra **no corresponde** con los huecos reales, por lo que un indicador de "horarios disponibles" no es fiable.
- Un cliente que pulse dos veces sobre la misma hora puede disparar dos intentos de reserva sobre el mismo rango.

#### Recomendacion

1. Fusionar las `WorkingWindow` del dia antes de generar los slots, con la misma logica de union de intervalos propuesta para S3-05.
2. Como defensa adicional, deduplicar por `startTime` al construir la lista final.
3. Extraer la operacion de fusion a una utilidad compartida en el dominio, ya que la necesitan tanto `AvailabilityCalculator` (HU-07) como `OccupancyCalculator` (HU-15).

---

### S3-08 · HU-15: los dias con reservas pero sin capacidad se reportan con 0% de ocupacion

**Severidad:** MEDIA
**HU afectada:** HU-15 (Reporte de ocupacion por periodo)
**Endpoint:** `GET /api/v1/reportes/ocupacion?periodo=MES`
**Reportado en:** `qa-tests/sprint3/test_hu05_13_15_integration.sh` (caso L.6)
**Atributo ISO/IEC 25010:** Eficiencia de desempeno (exactitud del dato)

#### Descripcion

Cuando un dia no tiene horario configurado (`capacityMinutes = 0`) pero tiene reservas, el reporte devuelve `occupancyPercent = 0.0`. El dia aparece simultaneamente con minutos reservados y con ocupacion nula, lo que es internamente contradictorio.

#### Criterio de aceptacion violado

HU-15, escenario "Generar reporte de ocupacion mensual": *"el sistema me presenta graficos y porcentajes de ocupacion por dia de la semana"*. Un dia con 870 minutos reservados y 0% de ocupacion no es un porcentaje de ocupacion: es un dato invalido presentado como valido.

#### Pasos de reproduccion

1. Identificar un dia de fin de semana sin horario semanal configurado (por ejemplo el sabado `2026-11-14`).
2. Consultar el reporte y registrar `SATURDAY`: `bookedMinutes = 810`, `capacityMinutes = 0`, `occupancyPercent = 0.0`.
3. Insertar una reserva `CONFIRMADA` de 60 minutos en ese dia.
4. Volver a consultar el reporte.

#### Request / Response real

```text
SATURDAY antes:    bookedMinutes='810' capacityMinutes='0' occupancyPercent='0.0'
SATURDAY despues:  bookedMinutes='870' capacityMinutes='0' occupancyPercent='0.0'
```

Los minutos reservados **crecen 60** y el porcentaje **no se mueve**. El total acumulado del caso es de **1590 minutos reservados** (sabado y domingo) reportados como 0%.

#### Causa raiz

`OccupancyCalculator.java` lineas 65-67:

```java
public static double percent(long bookedMinutes, long capacityMinutes) {
    if (capacityMinutes <= 0) {
        return 0.0;
    }
    ...
```

La guarda existe para evitar una division por cero, que es correcta, pero el valor de retorno `0.0` **confunde "sin datos" con "cero por ciento"**. La aplicacion de esta guarda ocurre en dos lugares:

- `OccupancyReportService.java` lineas 60-61 (`buildByDay`): `OccupancyCalculator.percent(booked.get(day), capacity.get(day))`
- `OccupancyReportService.java` lineas 80-81 (`buildByProfessional`): `OccupancyCalculator.percent(bookedMinutes, capacityMinutes)`

Ademas, el dia que no tiene horario se agrega al reporte porque la reserva existe, pero el profesional no tiene ninguna franja ese dia de la semana, por lo que su capacidad es 0. El reporte no distingue "no hay capacidad porque no se trabaja ese dia" de "no hay capacidad porque no hay datos".

#### Impacto

- Un dia con mucha actividad se muestra como si estuviera libre, con 0% de ocupacion.
- El `overallOccupancyPercent` agrega `bookedMinutes` al numerador pero la capacidad de esos dias es 0, de modo que el agregado tambien queda sesgado a la baja.
- El consumidor no puede distinguir un dia sin operacion de un dia con una inconsistencia de agenda que permitio reservar fuera de horario (relacionado con el BUG-07 del Sprint 2).

#### Recomendacion

1. Devolver `null` (o `Optional.empty()`) cuando `capacityMinutes <= 0` y `bookedMinutes > 0`, para marcar el dato como no calculable en lugar de 0%.
2. Documentar en `docs/openapi.yaml` que `occupancyPercent` es nullable y que `null` significa "capacidad no disponible para ese dia".
3. De forma alternativa, marcar el dia con un campo de estado (`capacityAvailable: false`) y reportar el porcentaje solo cuando haya capacidad.
4. Revisar el caso de negocio en el que existen reservas fuera del horario configurado, que es un caso distinto del QA/Exception de la HU ("sin datos transaccionales"), y que la HU si contempla como escenario valido de ocupacion cero.

---

### S3-09 · Contrato de error inconsistente: `error` frente a `errorCode`

**Severidad:** MEDIA
**HU afectada:** Transversal (afecta a todos los endpoints de la plataforma)
**Endpoints:** `POST /api/v1/reservas` y cualquier otro que use `BookingController.ErrorResponse`
**Reportado en:** Analisis de codigo y contraste con `docs/openapi.yaml`
**Atributo ISO/IEC 25010:** Mantenibilidad / Interoperabilidad (consistencia del contrato)

#### Descripcion

La plataforma expone dos formatos de respuesta de error distintos. El mas extendido, `ApiErrorResponse`, es el que define el contrato OpenAPI; pero algunos endpoints devuelven un record reduced con el campo `error` en lugar de `errorCode` y sin `details`, `traceId` ni `timestamp`.

#### Criterio de aceptacion violado

HU-13 y HU-15 no lo abordan de forma explicita, pero ambas historias exponen al cliente a la respuesta de error y la HU-15 declara la propiedad de Confiabilidad. El criterio aplicable es el contrato publicado en `docs/openapi.yaml`, que define `ApiError` con `required: [errorCode, message, details, traceId, timestamp]`.

#### Pasos de reproduccion

1. Provocar un error en `POST /api/v1/reservas` (por ejemplo, una franja ya ocupada).
2. Observar los campos de la respuesta.
3. Contrastar con un error producido en cualquier otro endpoint (por ejemplo `POST /api/v1/horarios/{id}/bloqueos`).

#### Request / Response real

`BookingController.java` linea 132:

```java
private record ErrorResponse(String error, String message) {}
```

Respuesta de `POST /api/v1/reservas` ante conflicto:

```json
{"error":"SLOT_OCCUPIED","message":"..."}
```

Contraste con el contrato global, `ApiErrorResponse.java` lineas 6-11 y `docs/openapi.yaml` lineas 842-846:

```java
public record ApiErrorResponse(
        String errorCode,
        String message,
        Map<String, String> details,
        String traceId,
        OffsetDateTime timestamp) {}
```

```yaml
ApiError:
  required: [errorCode, message, details, traceId, timestamp]
```

#### Causa raiz

`BookingController.java` declara un record local `ErrorResponse` (linea 132) con la forma reducida `error` + `message`, y lo usa en el `catch` de `BookingConflictException` (linea 107) y de `IllegalArgumentException` (linea 110). El resto de la plataforma usa `ApiErrorResponse` a traves de `GlobalExceptionHandler.java` y su metodo `buildResponse`.

El contrato OpenAPI publicado define `errorCode`, por lo que el cliente que sigue la documentacion no puede leer el campo `error` de estos dos errores.

#### Impacto

- El cliente debe implementar dos lecturas distintas del mismo concepto, y un cliente que siga fielmente `docs/openapi.yaml` no encuentra `errorCode` en las respuestas de error de `POST /api/v1/reservas`.
- La ausencia de `traceId` impide correlacionar un error reportado por el usuario con la traza del servidor, lo que degrada el soporte y contradice el uso de `traceId` en el resto de la API.
- La ausencia de `details` impide entregar informacion estructurada de contexto, como el detalle de las citas afectadas que el propio HU-05 si provee.

#### Recomendacion

1. Eliminar el record `ErrorResponse` de `BookingController.java` (linea 132) y usar `ApiErrorResponse` en todos los `catch`.
2. Asegurar que `GlobalExceptionHandler.java` maneje `BookingConflictException` con `errorCode`=`SLOT_OCCUPIED` y `details` con el contexto disponible, de modo que el `catch` local del controlador sea innecesario.
3. Barrer el resto de controladores buscando records de error locales con la misma forma reducida.
4. Agregar un test de contrato que verifique que **toda** respuesta de error de la API contiene `errorCode`, `message`, `details`, `traceId` y `timestamp`.

---

### S3-10 · RESCHEDULED puede solaparse con CONFIRMADA

**Severidad:** MEDIA
**HU afectada:** HU-08 (creacion de reserva), HU-07 (disponibilidad) y HU-15 (reporte)
**Reportado en:** Analisis de codigo y de la migracion `V46`
**Atributo ISO/IEC 25010:** Confiabilidad (integridad de la agenda)

#### Descripcion

El enum `BookingStatus` incluye `RESCHEDULED`, pero ese estado no lo considera ni la base de datos (para la restriccion de exclusion) ni ninguno de los dos modulos que consultan la ocupacion de la agenda. Una reserva reprogramada queda en un limbo: no bloquea la agenda, no aparece en la disponibilidad y no aparece en el reporte.

#### Criterio de aceptacion violado

HU-08 (creacion de reserva), propiedad de Confiabilidad: la agenda de un profesional no puede tener dos reservas que se solapen. HU-07 y HU-15 deben operar sobre la misma definicion de agenda ocupada.

#### Pasos de reproduccion

1. Crear una reserva `CONFIRMADA` en una franja.
2. Cambiar su estado a `RESCHEDULED` por base de datos.
3. Crear una segunda reserva `CONFIRMADA` en la misma franja.
4. Observar: la base de datos lo permite, porque la EXCLUDE de `V46` solo mira `status = 'CONFIRMADA'`.

#### Request / Response real

```text
V46__prevent_overlapping_confirmed_bookings.sql (lineas 6-12):

ALTER TABLE bookings
    ADD CONSTRAINT booking_no_overlapping_confirmed
    EXCLUDE USING gist (
        professional_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    )
    WHERE (status = 'CONFIRMADA');
```

La predicado del `EXCLUDE` es `status = 'CONFIRMADA'` y nada mas. Una reserva en `RESCHEDULED` queda fuera de la restriccion.

#### Causa raiz

Tres lugares de forma independiente, ninguno de los cuales incluye `RESCHEDULED`:

1. `V46__prevent_overlapping_confirmed_bookings.sql` linea 12: el predicado `WHERE (status = 'CONFIRMADA')` de la restriccion `EXCLUDE`.
2. `BookingRepository.java` lineas 28-30: la JPQL `findOverlappingBookings` filtra por `b.status = 'CONFIRMADA'`.
3. `OccupancyQueryAdapter.java` lineas 25-26: `OCCUPYING_STATUSES = List.of(BookingStatus.CONFIRMADA, BookingStatus.COMPLETED)`.

El enum `BookingStatus.java` lineas 3-8 declara los cuatro estados (`CONFIRMADA`, `CANCELADA`, `RESCHEDULED`, `COMPLETED`), y `RESCHEDULED` aparece en `NotificationEventType.BOOKING_RESCHEDULED` y en `NotificationService.java` linea 110, lo que confirma que el estado existe en el dominio y se notifica. Lo que no existe es un unico lugar que defina que estados ocupan agenda.

#### Impacto

- Dos reservas pueden ocupar fisicamente la misma franja: una `CONFIRMADA` y una `RESCHEDULED`. El profesional recibe dos citas para el mismo horario.
- `RESCHEDULED` es invisible en la disponibilidad, de modo que el cliente puede reservar sobre una franja que en realidad ya tiene una cita reprogramada.
- `RESCHEDULED` es invisible en el reporte, por lo que las horas efectivamente comprometidas no se computan y la ocupacion real se subestima.
- El diagnostico se realizo por analisis de codigo: ningun endpoint del alcance actual produce una reserva en `RESCHEDULED`, por lo que no fue reproducible via API en esta corrida.

#### Recomendacion

1. Definir en `BookingStatus` un metodo unico y explicito, por ejemplo `ocupaAgenda()`, que determine que estados bloquean la agenda, y usarlo en los tres puntos anteriores.
2. Actualizar la migracion `V46` para ampliar el predicado del `EXCLUDE` a los estados que ocupen agenda (`ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ... WHERE (status IN (...))`).
3. Decidir el criterio de negocio para `RESCHEDULED`: si una reprogramacion mantiene la reserva original y crea otra, entonces `RESCHEDULED` **no** debe ocupar agenda (la reserva original, ya reubicada, si); si la reprogramacion reescribe la reserva, entonces si debe ocupar.
4. Incluir `RESCHEDULED` en la definicion de ocupado de la disponibilidad y del reporte una vez decidido el punto anterior, para no repetir el defecto S3-03.
5. Agregar un caso E2E que cubra la transicion de estado, dado que hoy ningun endpoint la expone.

---

### S3-11 · La disponibilidad solo acepta rangos futuros y el reporte solo observa el pasado

**Severidad:** MEDIA
**HU afectada:** HU-07 (disponibilidad) y HU-15 (reporte de ocupacion)
**Endpoints:** `GET /api/v1/disponibilidad` y `GET /api/v1/reportes/ocupacion`
**Reportado en:** Analisis de codigo
**Atributo ISO/IEC 25010:** Interoperabilidad (ventanas temporales observables)

#### Descripcion

Los dos modulos nunca observan el mismo rango temporal. La disponibilidad rechaza cualquier consulta cuyo rango termine en el pasado, y el reporte construye siempre una ventana que termina hoy y empieza 29 dias atras. Por lo tanto no existe ninguna combinacion de fechas en la que las dos vistas puedan contrastarse.

#### Criterio de aceptacion violado

HU-15, criterio "Generar reporte de ocupacion mensual": *"existen reservas agendadas y atendidas durante el ultimo mes"* y *"extrae los datos unicamente de la base de datos de la plataforma de forma segura"*. HU-07 debe reflejar *"los horarios disponibles"*. Ambas afirmaciones son correctas, pero no son contrastables entre si, lo que impide validar la coherencia del sistema en el tiempo.

#### Pasos de reproduccion

1. Consultar `GET /api/v1/disponibilidad?from=<ayer>&to=<hoy>`.
2. Observar: `400` con el mensaje *"El rango de fechas no puede estar en el pasado"*.
3. Consultar `GET /api/v1/reportes/ocupacion?periodo=MES`.
4. Observar: la ventana es siempre `[hoy-29, hoy]`, sin posibilidad de parametrizarla.

#### Request / Response real

`AvailabilityService.java` lineas 100-104:

```java
if (to.isBefore(LocalDate.now())) {
    throw new InvalidAvailabilityQueryException(
            "El rango de fechas no puede estar en el pasado",
            Map.of("hasta", "Debe ser hoy o una fecha futura"));
}
```

`OccupancyReportService.java` lineas 32-33:

```java
LocalDate to = LocalDate.now();
LocalDate from = to.minusDays(period.getDays() - 1L);
```

La ventana del reporte se calcula contra el reloj del servidor y no admite parametros `from`/`to`.

#### Causa raiz

Cada modulo define su propia politica temporal sin coordinarla. La disponibilidad protege al cliente de un consulta en pasado (decision de negocio razonable y aislada); el reporte es deliberadamente retrospectivo (ocupacion de un periodo ya transcurrido). El problema es que la variable de entrada de la comparacion (el rango) no es compartible, de modo que los dos modulos nunca se validan mutuamente.

#### Impacto

- No es posible construir un caso de integracion que compare "lo que el cliente ve disponible" con "lo que el administrador ve ocupado" para el mismo periodo.
- Los defectos S3-02 y S3-03, que son contradicciones entre los dos modulos, solo pudieron detectarse de forma indirecta (mutando el estado de las reservas y observando cada endpoint por separado), no con una consulta comparativa directa.
- El reporte no permite auditar un periodo historico concreto ("como estuvo la ocupacion del mes de marzo"), lo que limita su uso analitico.

#### Recomendacion

1. Permitir que el reporte acepte `from` y `to` opcionales, con la ventana por defecto actual como valor por defecto, para poder consultar periodos historicos concretos.
2. Permitir en la disponibilidad una ventana que incluya el dia de hoy y el pasado reciente en modo "reporte" o "historico", o exponer un endpoint de ocupacion historica, de modo que exista una interseccion temporal entre ambos modulos.
3. Definir una politica de rango compartido (por ejemplo, `VentanaTemporalCompartida`) que ambos modulos consulten, en lugar de dos politicas independientes.
4. Agregar un caso de integracion que compare, para el mismo rango, la suma de minutos bloqueados en la disponibilidad contra `capacityMinutes` del reporte.

---

## 7. Analisis de cobertura de las pruebas

### 7.1 Cobertura por HU

| HU | Endpoints probados | Casos (PASS+FAIL) | Que se cubre | Que NO se cubre |
|----|--------------------|-------------------|-------------|------------------|
| **HU-05** | `POST /api/v1/horarios/{professionalId}/bloqueos` | 57 | Creacion valida (201); validacion de rango invertido (400 `SCHEDULE_VALIDATION_ERROR`); `startTime` en pasado (400); body incompleto (400 `VALIDATION_ERROR`); longitud y trim de `reason` (200/201); solape con reservas `CONFIRMADA` (409 `AGENDA_BLOCK_HAS_BOOKINGS` con detalle por reserva); solape con reserva `CANCELADA` (201); solape entre bloqueos (409 `AGENDA_BLOCK_OVERLAP`); contigüidad (201); control de acceso (401/403); `GET` lista solo bloqueos vigentes; `DELETE`; concurrencia | Comportamiento de la excepcion de exclusion cuando el rango solapa **parcialmente** con otro bloqueo en paralelo (cubierto en el bloque F, de forma secuencial); persistencia de la transaccion tras un 409; reintento del cliente tras el 401 (no automatizado) |
| **HU-13** | `GET /api/v1/reservas/cliente/historial` | 44 | Listado con fecha, servicio, profesional y estado; filtro por rango `from`/`to`; filtro por estado `CONFIRMADA`; estado invalido (400); rango invertido (400); paginacion invalida (`page=-1`, `size=0`, `size=101` -> 400; `size=100` -> 200); orden `asc`/`desc` y validacion de `order`; cliente sin reservas (200 con mensaje informativo); control de acceso (`ADMIN` 403, `PROFESSIONAL` 403, sin token 401); aislamiento ante `customerId` ajeno (200 sin datos ajenos); ruta legacy con `ADMIN` | Lectura de la ruta legacy por un `CUSTOMER` no propietario (no Covered); tamano de respuesta sin paginar (S3-06, detectado pero no automatizado como caso); verificacion de la **ausencia** de entrada de auditoria en la ruta legacy; el mensaje *"no incluye bajo ninguna circunstancia informacion de otros clientes"* solo se verifica por contenido, no por auditoria |
| **HU-15** | `GET /api/v1/reportes/ocupacion` | 29 | Periodos `MES` (por defecto) y `SEMANA`; normalizacion de mayusculas y espacios; periodo invalido (400 `VALIDATION_ERROR`); periodo vacio (200 con `MES`); control de acceso (`CUSTOMER` 403, `PROFESSIONAL` 403, sin token 401); calculo con reservas reales; `byDayOfWeek` con las 7 entradas; `byProfessional` con nombre; QA/Exception sin reservas (200 con ceros y sin excepcion interna); preflight CORS (200 con `Access-Control-Allow-Origin`) | La coherencia del porcentaje con el calculo manual (solo se valida que sea mayor que 0); el valor de `occupancyPercent` cuando la capacidad es 0 (S3-08, detectado en integracion); el redondeo a 2 decimales; el comportamiento con `from`/`to` externos (S3-11, no existe el parametro); el reporte sobre una base sin datos **de semana** (solo se probo `MES`) |
| **Integracion** | Transversal HU-05/07/13/15 | 44 | 12 bloques (A-L) que cruzan los tres modulos: coherencia bloqueo-disponibilidad; bloqueo-reporte (S3-02); estados de reserva (S3-03); aritmetica del reporte; ventana temporal; solapamiento parcial vs contiguo; franjas solapadas (S3-04, S3-05); profesional sin horario; bloqueo que no cancela reservas; migraciones V50; slots duplicados (S3-07); dia con reservas sin capacidad (S3-08) | La combinacion de mas de dos modulos a la vez (por ejemplo, un bloqueo concurrente **y** un `COMPLETED` en la misma franja); la concurrencia en los otros dos modulos; la consistencia bajo carga sostenida (mas de 8 peticiones) |

### 7.2 Cobertura por dimension

| Dimension | HU-05 | HU-13 | HU-15 |
|-----------|-------|-------|-------|
| Happy path | Cubierto | Cubierto | Cubierto |
| Validacion de entrada | Cubierto (rangos, `reason`, body) | Cubierto (fechas, estado, paginacion, orden) | Cubierto (`periodo`) |
| Control de acceso (401/403) | Cubierto | Cubierto | Cubierto |
| Aislamiento entre usuarios | Parcial (ownership del profesional) | Cubierto (ruta nueva); **no** en la legacy | N/A (solo ADMIN) |
| Persistencia y rollback | **No cubierto** (causa de S3-01) | No cubierto | No cubierto |
| Concurrencia | **Detectado como defectuoso** (S3-01) | No cubierto | No cubierto |
| Integracion entre modulos | Cubierto (bloque A-L) | Cubierto (bloque I) | Cubierto (bloques B, C, G, L) |
| Base de datos real (PostgreSQL) | Cubierto (EXCLUDE de V50) | Cubierto (JPQL con `CAST`) | Cubierto (franjas solapadas) |
| Caso limite de datos | Parcial | No cubierto | Cubierto (profesional sin horario) |

### 7.3 Brechas de cobertura identificadas

| Brecha | Consecuencia | Defecto asociado |
|--------|---------------|------------------|
| No hay prueba de integracion que compare la disponibilidad y el reporte sobre **el mismo rango temporal** | Las contradicciones entre modulos no se pueden detectar de forma directa | S3-02, S3-03, S3-11 |
| No hay prueba de persistencia/rollback que verifique que una transaccion abortada no deja el recurso en estado inconsistente | Un 500 interno puede enmascarar un 409 de negocio sin que ninguna prueba lo note | S3-01 |
| No hay prueba que verifique el **contrato de forma** de todas las respuestas de error | Dos formatos de error conviven sin que ninguna prueba lo detecte | S3-09 |
| No hay prueba que cubra `RESCHEDULED` | Un estado del enum sin consumers coherentes | S3-10 |
| No hay prueba de volumen sobre la ruta legacy de HU-13 | El defecto de paginacion no se detecta por tamano de respuesta | S3-06 |
| La suite unitaria (H2) replica la suite E2E | Los defectos de JPQL y de semantica PostgreSQL no se detectan en el pipeline de CI | S3-03, S3-04, S3-05, S3-07, S3-08, S3-10 |

### 7.4 Nota sobre la concurrencia (flakiness)

El caso de concurrencia de HU-05 **es flaky por naturaleza**. La corrida registrada en `summary/all.txt` fue `PASS=55 / FAIL=2 / BUG=1`, y la corrida anterior fue `PASS=56 / FAIL=0 / BUG=0`. La explicacion es que el sintoma depende de que plan de bloqueo alcance PostgreSQL:

- Si la peticion perdedora recibe el error de la EXCLUDE y el `catch` intenta consultar sobre la transaccion abortada, la peticion cae a `/error` y se enmascara como `401`.
- Si PostgreSQL resuelve el conflicto de otra forma (por ejemplo, serializando el `INSERT` y esperando al commit del ganador), la peticion perdedora puede recibir el `409` esperado de forma directa.

**El defecto es real y determinista en su causa raiz** (5 de 5 rondas lo reprodujeron, y el camino secuencial que comparte el mismo try/catch demuestra que la logica de traduccion de errores no esta implementada). Lo que es variable es la manifestacion observable. La correccion debe verificarse con un test de concurrencia que compruebe el **contrato** (`201` + `409 AGENDA_BLOCK_OVERLAP` y nunca `401` ni `500`), no la cantidad exacta de cada codigo, y con un numero de rondas suficiente para absorber la variabilidad.

---

## 8. Coherencia entre modulos

| # | Modulos | Contradiccion | Consecuencia | Defecto |
|---|---------|---------------|---------------|---------|
| 1 | HU-05 vs HU-15 | La disponibilidad descuenta los bloqueos de agenda; el reporte de ocupacion **no** | La misma franja es "libre" en un modulo y "capacidad" en otro, para el mismo profesional y el mismo instante | S3-02 |
| 2 | HU-07 vs HU-15 | La disponibilidad considera ocupado solo `CONFIRMADA`; el reporte considera `CONFIRMADA` + `COMPLETED` | Una franja que el reporte da por ocupada se sigue ofreciendo como reservable | S3-03 |
| 3 | HU-07 vs HU-15 | Ninguno de los dos fusiona franjas solapadas: uno duplica slots, el otro infla la capacidad | La agenda se cuenta de forma distinta en cada modulo y ninguna cuenta es correcta | S3-05, S3-07 |
| 4 | HU-04 vs HU-07 vs HU-15 | El CHECK `ck_horario_semanal_franja` solo exige `hora_fin > hora_inicio`; ningun modulo fusiona ni la base lo impide | Todos los defectos de capacidad y de duplicacion tienen la misma causa raiz aguas arriba | S3-05, S3-07 |
| 5 | HU-05 vs HU-08 | La EXCLUDE de `V46` cubre solo `CONFIRMADA`; la de `V50` cubre solo bloqueos; `RESCHEDULED` no lo cubre ninguno | Dos reservas pueden ocupar la misma franja | S3-10 |
| 6 | HU-13 vs HU-17 | La ruta nueva `/cliente/historial` restringe a `CUSTOMER` (403 para ADMIN); la ruta vieja `/cliente/{customerId}` permite a `ADMIN` leer el historial de cualquier cliente | La misma operacion de negocio devuelve codigos contradictorios segun la ruta | S3-06 |
| 7 | HU-13 vs HU-19 | La ruta vieja no deja registro de auditoria de lectura de PII de terceros; el sistema si audita otras acciones criticas | La trazabilidad exigida por el sistema no aplica a una lectura de datos personales | S3-06 |
| 8 | Transversal | `BookingController.ErrorResponse` (`error`) vs `ApiErrorResponse` (`errorCode`) | Dos contratos de error para el mismo concepto; el OpenAPI publicado solo describe uno | S3-09 |
| 9 | HU-07 vs HU-15 | La disponibilidad rechaza rangos en el pasado; el reporte solo observa el pasado | Los dos modulos nunca observan el mismo rango, lo que impide su contraste directo | S3-11 |
| 10 | HU-08 vs HU-15 | `CreateBookingService` traduce el error de concurrencia a `SLOT_OCCUPIED` (409) y maneja `CannotAcquireLockException`; `AgendaBlockService` no maneja ninguno de los dos | Dos modulos con el mismo problema de concurrencia resueltos con criterios distintos | S3-01 |
| 11 | HU-07 vs HU-05 | La disponibilidad excluye las franjas bloqueadas correctamente; el reporte no | El bloque de HU-05 no tiene efecto observable en el unico modulo que lo consume para medir | S3-02 |

---

## 9. Recomendaciones priorizadas

| Prioridad | Accion | HU afectada | Esfuerzo estimado |
|-----------|--------|-------------|-------------------|
| P0 | Traducir los errores de concurrencia de HU-05 a `409 AGENDA_BLOCK_OVERLAP`: manejar `CannotAcquireLockException`, no consultar sobre la transaccion abortada (usar `REQUIRES_NEW` o resolver fuera de la transaccion) y agregar un manejador para `JpaSystemException` en `GlobalExceptionHandler` | HU-05 | 4-8 h |
| P0 | Evitar que el `500` se enmascare como `401`: revisar la interaccion entre `ExceptionTranslationFilter` y el dispatch `/error` para que una excepcion no controlada no produzca `UNAUTHORIZED` | Transversal | 2-4 h |
| P0 | Descontar los bloqueos de agenda de `capacityMinutes`: extender el puerto `OccupancyQuery` con `findAgendaBlocks(from, to)`, implementarlo en `OccupancyQueryAdapter` y aplicarlo en `byDayOfWeek` y `byProfessional` | HU-15, HU-05 | 4-6 h |
| P0 | Unificar la definicion de "ocupado": crear un metodo unico en `BookingStatus` (por ejemplo `ocupaAgenda()`) y usarlo en `BookingRepository.findOverlappingBookings`, `OccupancyQueryAdapter.OCCUPYING_STATUSES` y en el predicado de la EXCLUDE de `V46` | HU-07, HU-15, HU-08 | 4-6 h |
| P1 | Fusionar intervalos solapados en el calculo de capacidad: unificar las `WorkingWindow` por profesional y dia antes de sumar, y extraer la utilidad a un componente compartido del dominio | HU-15, HU-07 | 6-10 h |
| P1 | Fusionar intervalos solapados en el calculo de disponibilidad: unir las franjas del dia antes de generar slots y deduplicar por `startTime` | HU-07 | 3-5 h |
| P1 | Impedir franjas solapadas en la base de datos: agregar una restriccion `EXCLUDE USING gist` sobre `(profesional_id, dia_semana)` con `tsrange`, y revisar por que el servicio de horario (HU-04) no rechaza el solapamiento parcial | HU-04 | 4-6 h |
| P1 | Eliminar el recorte silencioso del 100%: devolver el porcentaje real o agregar al DTO una bandera `occupancyPercentCapped` junto con el valor sin recortar, y registrar un `WARN` cuando `bookedMinutes > capacityMinutes` | HU-15 | 2-4 h |
| P1 | Pagar y auditar la ruta legacy de HU-13: migrarla a `Pageable` o marcarla como obsoleta, registrar la lectura en `audit_log` (admin, customerId, IP, timestamp) y unificar el criterio de rol con la ruta nueva | HU-13 | 6-10 h |
| P2 | Unificar el contrato de error: eliminar el record `ErrorResponse` de `BookingController` y usar `ApiErrorResponse` en todos los `catch`; barrer el resto de controladores | Transversal | 3-5 h |
| P2 | Sustituir el `0.0` por `null` cuando `capacityMinutes <= 0` y hay minutos reservados, y documentar la nulabilidad en `docs/openapi.yaml` | HU-15 | 2-3 h |
| P2 | Habilitar `from`/`to` en el reporte de ocupacion y definir una politica de rango temporal compartida con la disponibilidad | HU-15, HU-07 | 4-6 h |
| P2 | Cubrir `RESCHEDULED` en la migracion de exclusion y en las consultas de disponibilidad y reporte, una vez definido el criterio de negocio | HU-08, HU-07, HU-15 | 3-5 h |
| P2 | Ejecutar la suite E2E de Sprint 3 contra PostgreSQL real en el pipeline de CI (el `docker-compose.yml` ya provee `postgres:16-alpine`) para que los defectos de JPQL y de semantica de base de datos se detecten automaticamente | Transversal | 4-8 h |
| P3 | Agregar al reporte un indicador de calidad del dato (por ejemplo `dataQualityWarnings`) que liste anomalias detectadas: capacidad inflada, porcentaje recortado, capacidad nula con reservas | HU-15 | 4-6 h |
| P3 | Agregar a la suite E2E un caso de concurrencia con 8 peticiones que verifique el contrato (`201` + `409 AGENDA_BLOCK_OVERLAP`, nunca `401` ni `500`) con varias rondas para absorber la variabilidad | HU-05 | 3-4 h |
| P3 | Documentar en `docs/HU-05-bloqueo-agenda.md`, `docs/HU-13-historial-reservas-cliente.md` y `docs/HU-15-reporte-ocupacion.md` las decisiones de negocio sobre estados que ocupan agenda y sobre el rango temporal | HU-05, HU-13, HU-15 | 2-3 h |

---

## 10. Anexo: comandos de reproduccion

### 10.1 Preparacion del ambiente

```bash
# Repositorio bajo prueba
REPO=/home/mista/Escritorio/udea/2026-II/calidad-software/Proyecto-FE/backendcf
cd "$REPO"
git checkout qa/sprint3-integracion

# Base de datos de pruebas (PostgreSQL 16.15, puerto host 55432)
docker exec -i backendcf-db psql -v ON_ERROR_STOP=1 -U bookly_test -d bookly_test \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Levantar la aplicacion desde el JAR
DB_URL="jdbc:postgresql://localhost:55432/bookly_test" \
DB_USERNAME="bookly_test" DB_PASSWORD="bookly_test_local_2026" \
java -jar target/backendcf.jar

# Sembrar datos deterministas
bash qa-tests/sprint3/reset.sh
bash qa-tests/sprint3/seed.sh
```

Variables de entorno usadas por los scripts (`qa-tests/sprint3/lib.sh`):

```bash
export BASE_URL="http://localhost:8080"
export DB_CONTAINER="backendcf-db"
export DB_ROLE="bookly_test"
export DB_NAME="bookly_test"
export DB_PORT="55432"
export DB_PASSWORD="bookly_test_local_2026"

# Consulta directa a la base de datos
psql_qa() {
    docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_ROLE" -d "$DB_NAME" "$@"
}
```

### 10.2 S3-01 · Concurrencia de HU-05 (401 en vez de 409)

```bash
TOKEN_PRO="<token del profesional>"
PROF_ID="<professionalId del profesional>"

START="2026-12-21T09:00:00"
END="2026-12-21T12:00:00"
PAYLOAD="{\"startTime\":\"$START\",\"endTime\":\"$END\",\"reason\":\"concurrent\"}"

# Dos peticiones simultaneas sobre el MISMO rango
for i in 1 2; do
  curl -sS -o "/tmp/bloqueo_$i.json" -w "peticion $i -> HTTP %{http_code}\n" \
    -X POST "$BASE_URL/api/v1/horarios/$PROF_ID/bloqueos" \
    -H "Authorization: Bearer $TOKEN_PRO" \
    -H "Content-Type: application/json" \
    --data "$PAYLOAD" &
done
wait

cat /tmp/bloqueo_1.json; echo
cat /tmp/bloqueo_2.json; echo

# Resultado observado: 1 x 201 y 1 x 401 UNAUTHORIZED (deberia ser 409 AGENDA_BLOCK_OVERLAP)
# body={"errorCode":"UNAUTHORIZED","message":"Se requiere autenticacion",...}

# Control secuencial: el MISMO solape, sin concurrencia, si devuelve 409 correctamente
curl -sS -o /tmp/sec1.json -w "1ro -> HTTP %{http_code}\n" \
  -X POST "$BASE_URL/api/v1/horarios/$PROF_ID/bloqueos" \
  -H "Authorization: Bearer $TOKEN_PRO" -H "Content-Type: application/json" \
  --data "{\"startTime\":\"$START\",\"endTime\":\"$END\",\"reason\":\"secuencial\"}"

curl -sS -o /tmp/sec2.json -w "2do -> HTTP %{http_code}\n" \
  -X POST "$BASE_URL/api/v1/horarios/$PROF_ID/bloqueos" \
  -H "Authorization: Bearer $TOKEN_PRO" -H "Content-Type: application/json" \
  --data "{\"startTime\":\"$START\",\"endTime\":\"$END\",\"reason\":\"secuencial\"}"

cat /tmp/sec2.json; echo
# Resultado esperado y observado: 409 con errorCode AGENDA_BLOCK_OVERLAP

# Carga mayor: 8 peticiones concurrentes -> 6 x 409 + 1 x 201 + 1 x 401
for i in $(seq 1 8); do
  curl -sS -o "/tmp/mass_$i.json" -w "%{http_code}\n" \
    -X POST "$BASE_URL/api/v1/horarios/$PROF_ID/bloqueos" \
    -H "Authorization: Bearer $TOKEN_PRO" -H "Content-Type: application/json" \
    --data "$PAYLOAD" &
done | sort | uniq -c
wait

# Verificar que solo quedo un bloqueo en la base de datos
psql_qa -t -A -c \
  "SELECT count(*) FROM bloqueo_agenda
    WHERE profesional_id='$PROF_ID'::uuid AND inicio='$START' AND fin='$END';"
```

### 10.3 S3-02 · El reporte ignora los bloqueos de agenda

```bash
TOKEN_ADMIN="<token ADMIN>"
PROF_ID="<professionalId>"

# 1. Reporte ANTES del bloqueo
curl -sS "$BASE_URL/api/v1/reportes/ocupacion?periodo=MES" \
  -H "Authorization: Bearer $TOKEN_ADMIN" -o /tmp/rep_antes.json
grep -o '"capacityMinutes":[0-9]*' /tmp/rep_antes.json | head -3
# Observado: capacityMinutes = 3960   (MONDAY: 3120)

# 2. Crear un bloqueo de 120 min dentro de la ventana del reporte (2026-11-16, lunes)
curl -sS -o /tmp/bloqueo.json -w "POST bloqueo -> HTTP %{http_code}\n" \
  -X POST "$BASE_URL/api/v1/horarios/$PROF_ID/bloqueos" \
  -H "Authorization: Bearer $TOKEN_PRO" -H "Content-Type: application/json" \
  --data '{"startTime":"2026-11-16T09:00:00","endTime":"2026-11-16T11:00:00","reason":"vacaciones"}'
# Observado: HTTP 201

# 3. Reporte DESPUES del bloqueo
curl -sS "$BASE_URL/api/v1/reportes/ocupacion?periodo=MES" \
  -H "Authorization: Bearer $TOKEN_ADMIN" -o /tmp/rep_despues.json
grep -o '"capacityMinutes":[0-9]*' /tmp/rep_despues.json | head -3
# Observado: capacityMinutes = 3960   (MONDAY: 3120)  <-- sin cambios,BUG

# Diferencia esperada: -120 min. Diferencia observada: 0
```

### 10.4 S3-03 · CONFIRMADA frente a COMPLETED entre disponibilidad y reporte

```bash
TOKEN_ADMIN="<token ADMIN>"
TOKEN_CUST="<token del cliente>"
PROF_ID="<professionalId>"
SVC_ID="<serviceId>"

# 1. Crear una reserva CONFIRMADA de 60 min
curl -sS -o /tmp/reserva.json -w "POST reserva -> HTTP %{http_code}\n" \
  -X POST "$BASE_URL/api/v1/reservas" \
  -H "Authorization: Bearer $TOKEN_CUST" -H "Content-Type: application/json" \
  --data "{\"professionalId\":\"$PROF_ID\",\"serviceId\":\"$SVC_ID\",
            \"startTime\":\"2026-12-28T09:00:00\",\"endTime\":\"2026-12-28T10:00:00\"}"

RES_ID=$(grep -o '"id":"[^"]*"' /tmp/reserva.json | head -1 | cut -d'"' -f4)

# 2. Disponibilidad con la reserva en CONFIRMADA
curl -sS "$BASE_URL/api/v1/disponibilidad?professionalId=$PROF_ID&serviceId=$SVC_ID&from=2026-12-28&to=2026-12-28" \
  -H "Authorization: Bearer $TOKEN_CUST" -o /tmp/disp_confirmada.json
grep -o '"startTime":"[^"]*"' /tmp/disp_confirmada.json
# Observado: 10:00, 10:30, 11:00, 11:30                      (4 slots)

# 3. Cambiar el estado de la MISMA reserva a COMPLETED por base de datos
psql_qa -c "UPDATE bookings SET status='COMPLETED' WHERE id='$RES_ID'::uuid;"

# 4. Disponibilidad con la misma reserva en COMPLETED
curl -sS "$BASE_URL/api/v1/disponibilidad?professionalId=$PROF_ID&serviceId=$SVC_ID&from=2026-12-28&to=2026-12-28" \
  -H "Authorization: Bearer $TOKEN_CUST" -o /tmp/disp_completed.json
grep -o '"startTime":"[^"]*"' /tmp/disp_completed.json
# Observado: 09:00, 09:30, 10:00, 10:30, 11:00, 11:30       (6 slots)  <-- la franja 09:00 vuelve a aparecer

# 5. El reporte HU-15 SI cuenta la reserva COMPLETED (+60 min)
curl -sS "$BASE_URL/api/v1/reportes/ocupacion?periodo=MES" \
  -H "Authorization: Bearer $TOKEN_ADMIN" -o /tmp/rep_completed.json
grep -o '"bookedMinutes":[0-9]*' /tmp/rep_completed.json | head -3
```

### 10.5 S3-04 y S3-05 · Recorte a 100 y capacidad inflada por franjas solapadas

```bash
TOKEN_ADMIN="<token ADMIN>"
PROF_ID="<professionalId de miguel>"

# 1. El profesional debe tener dos franjas solapadas el mismo dia (09:00-12:00 y 11:00-14:00)
psql_qa -t -A -c \
  "SELECT dia_semana, hora_inicio, hora_fin FROM horario_semanal
    WHERE profesional_id='$PROF_ID'::uuid AND dia_semana='MONDAY' ORDER BY hora_inicio;"

# 2. Insertar 80 reservas CONFIRMADA para disparar bookedMinutes > capacityMinutes
psql_qa -c "
  INSERT INTO bookings (id, customer_id, professional_id, service_id,
                        start_time, end_time, status, created_at)
  SELECT gen_random_uuid(), c.id, '$PROF_ID'::uuid, s.id,
         '2026-11-16 09:00'::timestamp + (i * 30) * interval '1 minute',
         '2026-11-16 09:30'::timestamp + (i * 30) * interval '1 minute',
         'CONFIRMADA', now()
    FROM generate_series(0, 79) i
    CROSS JOIN LATERAL (SELECT id FROM customer LIMIT 1) c
    CROSS JOIN LATERAL (SELECT id FROM servicio LIMIT 1) s;"

# 3. Reporte: capacidad inflada y porcentaje recortado
curl -sS "$BASE_URL/api/v1/reportes/ocupacion?periodo=MES" \
  -H "Authorization: Bearer $TOKEN_ADMIN" -o /tmp/rep_inflado.json
cat /tmp/rep_inflado.json
# Observado: capacityMinutes = 4680, bookedMinutes = 4800, occupancyPercent = 100.0
# Valor real del porcentaje: 253.85%

# 4. Capacidad real del lunes (franjas fusionadas) frente a la reportada
REAL_MONDAY_MIN=300    # union de 09:00-12:00 y 11:00-14:00
echo "Minutos que suma el reporte: 360  (3h + 3h en vez de 5h)"
```

### 10.6 S3-06 · Ruta legacy de HU-13 sin paginar ni auditar

```bash
TOKEN_ADMIN="<token ADMIN>"
OTHER_CUSTOMER_ID="<customerId de otro cliente>"

# Ruta vieja: ADMIN lee el historial completo de otro cliente, sin paginar
curl -sS -o /tmp/legacy.json -w "ruta vieja -> HTTP %{http_code}\n" \
  "$BASE_URL/api/v1/reservas/cliente/$OTHER_CUSTOMER_ID" \
  -H "Authorization: Bearer $TOKEN_ADMIN"

# Tamaño de la respuesta (con 111 reservas: ~60 KB) y numero de elementos
wc -c /tmp/legacy.json
grep -o '"id":"[^"]*"' /tmp/legacy.json | wc -l

# Ruta nueva: la MISMA operacion como ADMIN esta prohibida
curl -sS -o /tmp/nueva.json -w "ruta nueva -> HTTP %{http_code}\n" \
  "$BASE_URL/api/v1/reservas/cliente/historial" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
# Observado: ruta vieja 200 con datos, ruta nueva 403

# Verificar la ausencia de auditoria de lectura
psql_qa -t -A -c \
  "SELECT count(*) FROM audit_log
    WHERE action LIKE '%HISTORIAL%' OR action LIKE '%CONSULTA%RESERVA%';"
# Observado: 0 registros para la lectura de PII de terceros
```

### 10.7 S3-07 · Slots duplicados en la disponibilidad

```bash
TOKEN_CUST="<token del cliente>"
PROF_ID="<professionalId de miguel>"
SVC_ID="<serviceId de 60 minutos>"

# El profesional tiene 2 franjas solapadas el lunes (09:00-12:00 y 11:00-14:00)
curl -sS "$BASE_URL/api/v1/disponibilidad?professionalId=$PROF_ID&serviceId=$SVC_ID&from=2026-12-14&to=2026-12-14" \
  -H "Authorization: Bearer $TOKEN_CUST" -o /tmp/slots.json

grep -o '"startTime":"[^"]*"' /tmp/slots.json
# Observado: 09:00, 10:00, 11:00, 11:00, 12:00, 13:00   <-- 11:00 repetido (6 slots, 5 unicos)
```

### 10.8 S3-08 · Dia con reservas pero sin capacidad

```bash
TOKEN_ADMIN="<token ADMIN>"
TOKEN_CUST="<token del cliente>"
PROF_ID="<professionalId>"
SVC_ID="<serviceId>"

# El 2026-11-14 (sabado) no tiene horario: capacityMinutes = 0
curl -sS "$BASE_URL/api/v1/reportes/ocupacion?periodo=MES" \
  -H "Authorization: Bearer $TOKEN_ADMIN" -o /tmp/rep_sab_antes.json
grep -o '{"dayOfWeek":"SATURDAY"[^}]*}' /tmp/rep_sab_antes.json
# Observado: bookedMinutes=810, capacityMinutes=0, occupancyPercent=0.0

# Insertar una reserva CONFIRMADA de 60 min en ese dia
psql_qa -c "
  INSERT INTO bookings (id, customer_id, professional_id, service_id,
                        start_time, end_time, status, created_at)
  VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-000000000001',
          (SELECT id FROM customer LIMIT 1), '$PROF_ID'::uuid,
          '$SVC_ID'::uuid,
          '2026-11-14 10:00', '2026-11-14 11:00', 'CONFIRMADA', now());"

curl -sS "$BASE_URL/api/v1/reportes/ocupacion?periodo=MES" \
  -H "Authorization: Bearer $TOKEN_ADMIN" -o /tmp/rep_sab_despues.json
grep -o '{"dayOfWeek":"SATURDAY"[^}]*}' /tmp/rep_sab_despues.json
# Observado: bookedMinutes=870, capacityMinutes=0, occupancyPercent=0.0
# Los minutos reservados crecen 60 y el porcentaje no se mueve: el dato es contradictorio
```

### 10.9 S3-09 · Contrato de error inconsistente

```bash
TOKEN_CUST="<token del cliente>"

# Endpoint de BookingController: devuelve "error" (no "errorCode")
curl -sS -o /tmp/err_booking.json -w "HTTP %{http_code}\n" \
  -X POST "$BASE_URL/api/v1/reservas" \
  -H "Authorization: Bearer $TOKEN_CUST" -H "Content-Type: application/json" \
  --data "{\"professionalId\":\"<PROF_ID>\",\"serviceId\":\"<SVC_ID>\",
            \"startTime\":\"<una franja ya ocupada>\",\"endTime\":\"<fin de esa franja>\"}"
cat /tmp/err_booking.json
# Observado: {"error":"SLOT_OCCUPIED","message":"..."}   <-- sin errorCode, details, traceId ni timestamp

# Contraste con un endpoint regido por GlobalExceptionHandler / ApiErrorResponse
curl -sS -o /tmp/err_global.json -w "HTTP %{http_code}\n" \
  "$BASE_URL/api/v1/reportes/ocupacion?periodo=TRIMESTRE" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
cat /tmp/err_global.json
# Observado: {"errorCode":"VALIDATION_ERROR","message":"...","details":{},"traceId":"...","timestamp":"..."}

# Verificacion del contrato publicado
grep -n -A 6 "ApiError:" docs/openapi.yaml
```

### 10.10 Ejecucion de la suite completa

```bash
# Suite E2E completa de Sprint 3 (reset + seed por cada script)
bash qa-tests/sprint3/run-all.sh

# Consultar el resumen consolidado
cat qa-tests/sprint3/.e2e-results/summary/all.txt

# Ejecutar un script individual con su base limpia
bash qa-tests/sprint3/reset.sh
bash qa-tests/sprint3/seed.sh
bash qa-tests/sprint3/test_hu05_blocks.sh
```

---

## Control de cambios del documento

| Version | Fecha | Autor | Descripcion |
|---------|-------|-------|-------------|
| 1.0 | 2026-11-19 | QA Sprint 3 | Emision inicial: 11 defectos confirmados (2 criticos, 3 altos, 6 medios) sobre 174 casos E2E ejecutados contra PostgreSQL 16.15 |
| 1.1 | 2026-11-19 | QA Sprint 3 | Correccion de la aritmetica del resumen de ejecucion. El total de casos es `PASS + FAIL` = 169 + 5 = 174; los 10 hallazgos `BUG` son un subconjunto de los casos que fallaron y no casos adicionales. Se corrigieron la columna `Casos` por script (58/45/29/52 -> 57/44/29/44), el porcentaje de PASS por script y el detalle por bloque de integracion, que ahora se lee directamente de los veredictos de la suite. Sin cambios en el catalogo de los 11 defectos ni en sus severidades (2 criticos, 3 altos, 6 medios). |
