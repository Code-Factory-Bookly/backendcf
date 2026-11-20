# Reporte Detallado de Bugs - Sprint 3

**Proyecto:** Bookly BackendCF  
**Sprint:** 3 (cierre 25/11/2026)  
**Fecha:** 2026-11-20  
**Pruebas ejecutadas:** 178 E2E (173 PASS, 5 FAIL) + 723 JUnit (711 PASS, 12 FAIL)  
**Bugs detectados:** 16 (4 críticos/altos, 12 medios o menores)

> Cada bug está en formato listo para crear como Work Item en Azure DevOps. Los pasos de reproducción están escritos en lenguaje simple, sin comandos técnicos.

---

## Resumen

| ID | Severidad | HU | Estado | Effort |
|----|-----------|-----|--------|--------|
| S3-01 | CRÍTICO | HU-05 | Confirmado | 3-5h |
| S3-02 | CRÍTICO | HU-15 | Confirmado | 4-6h |
| S3-04 | ALTO | HU-15 | Confirmado | 3-5h |
| S3-12 | ALTO | Transversal | Confirmado | 2-4h |
| S3-03 | MEDIA | HU-15 | Confirmado (deuda) | 2-3h |
| S3-05 | MEDIA | HU-15 | Confirmado | 6-10h |
| S3-06 | MEDIA-ALTA | HU-13 | Confirmado | 6-10h |
| S3-07 | BAJA | HU-07 | Confirmado (deuda) | 3-5h |
| S3-08 | MEDIA | HU-15 | Confirmado | 3-4h |
| S3-09 | MEDIA | Transversal | Confirmado | 3-5h |
| S3-10 | BAJA-MEDIA | HU-08 | Confirmado (deuda) | 2-3h |
| S3-11 | BAJA | Transversal | Confirmado (deuda) | 2-4h |
| S3-13 | MEDIA | HU-13 | Confirmado | 4-6h |
| S3-14 | MEDIA-ALTA | HU-05 | Confirmado | 3-5h |
| S3-15 | MEDIA | HU-08 | Confirmado | 2-3h |
| S3-16 | BAJA | HU-15 | Confirmado | 1-2h |

**Total estimado:** 49-80 horas-persona

---

## Veredictos del debugging sistemático

Cada bug fue reevaluado con el método de cuatro pasos (Hipótesis → Predicción → Experimento → Veredicto) documentado en `REPORTE_DEBUGGING_SISTEMATICO_S3.md`. Se aplicó la regla de oro: si el único camino de reproducción es SQL que la API rechaza, el bug se reformula o reclasifica.

| ID | Veredicto original | Veredicto corregido | Severidad nueva | Estado |
|----|--------------------|--------------------|-----------------|--------|
| S3-01 | Confirmado (401 vs 409) | **Confirmado** — hay dos caminos de fallo (SQLState 25P02 y 40P01) y el servicio también falla: 0 de 10 rondas producen conflicto de negocio | CRÍTICO | Confirmado |
| S3-02 | Confirmado (CRÍTICA) | **Confirmado** — delta de capacidad 0 por API, contradicción con la disponibilidad demostrada en el mismo instante | CRÍTICO | Confirmado |
| S3-03 | Confirmado (ALTA) | **Refutado** para los estados que la API produce: CONFIRMADA y CANCELADA coinciden en ambos módulos. Reclasificado como deuda técnica de estado no alcanzable | BAJA | Confirmado (deuda) |
| S3-04 | Confirmado (ALTA) | **Confirmado y más grave** — es alcanzable por API: el profesional puede encoger su horario después de vender los huecos | ALTO | Confirmado |
| S3-05 | Confirmado (ALTA) | **Confirmado como asimetría de esquema** — la API rechaza el solape, así que no hay ruta de usuario; el riesgo real es la carga por SQL | MEDIA | Confirmado |
| S3-06 | Confirmado (MEDIO) | **Confirmado** — los tres puntos verificados, y el CHECK de la base impide auditar lecturas sin migración | MEDIA-ALTA | Confirmado |
| S3-07 | Confirmado (MEDIO) | **Refutado como bug de usuario** — la duplicación exige franjas solapadas y la búsqueda exhaustiva probó que nunca hay contraejemplo con franjas disjuntas | BAJA | Confirmado (deuda) |
| S3-08 | Confirmado (MEDIO) | **Confirmado** — alcanzable por API; la cifra real es 1650 minutos, no 1590, y el global se infla 11.86 puntos | MEDIA | Confirmado |
| S3-09 | Probable (MEDIO) | **Confirmado** — contradice el contrato publicado, que exige cinco campos | MEDIA | Confirmado |
| S3-10 | Probable (MEDIO) | **Refutado como bug de usuario** — la reprogramación es una vertical cortada: hay consumidor sin productor | BAJA-MEDIA | Confirmado (deuda) |
| S3-11 | Probable (MEDIO) | **Refutado** — el rango de ayer a hoy es válido en ambos módulos; la ventana fija es deliberada y documentada | BAJA | Confirmado (deuda) |

**Cinco bugs nuevos** se incorporaron al informe: S3-12 (401 falso por excepción no modelada), S3-13 (ruta de profesional sin paginación), S3-14 (carrera en el reemplazo de horario), S3-15 (duración de reserva libre) y S3-16 (reserva que cruza la medianoche).

**Tres bugs son en realidad un solo defecto con tres síntomas:** la falta de una fuente única de verdad para (i) la definición de "ocupa agenda" (S3-03, S3-10), (ii) la fusión de intervalos (S3-05, S3-07) y (iii) la forma de los errores (S3-09, S3-12). Resolver las tres causas reduce ocho elementos de trabajo a tres.

**La semilla de pruebas se corrigió para que mida el comportamiento real.** El debugging concluyó que la semilla creaba datos que la API no puede producir: una franja solapada el lunes para el profesional Miguel y 24 reservas de fin de semana en días en los que ese profesional no atiende. Esos datos falseaban la medición de S3-04, S3-05, S3-07 y S3-08, y además dejaban la base de datos sucia para la suite JUnit. Ahora las reservas sembradas solo caen en días con horario, de modo que el escenario es reproducible por la API. Los dos fixtures que siguen siendo deliberadamente inalcanzables por la API (la franja solapada del caso S3-05 y la reserva de sábado del caso S3-08) están marcados como tales en los scripts. Como efecto directo, el recorte silencioso del porcentaje (S3-04) ya no se provoca con datos de fin de semana: se provoca por la vía que el debugging descubrió, que es 100 por ciento API: el profesional vende sus huecos y luego reduce su propio horario.

---

## S3-01 — La concurrencia al bloquear la agenda lanza una excepción técnica en vez de un conflicto de negocio

**Title (Azure):** `[HU-05] Bloquear la misma franja a la vez no devuelve el conflicto de negocio AGENDA_BLOCK_OVERLAP`

**Severity:** Critical  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-05, concurrencia, atomicidad, sprint-3

### Descripción

Cuando dos solicitudes simultáneas intentan bloquear la misma franja horaria de un profesional, la que pierde la carrera no recibe el conflicto de negocio documentado. En lugar de una respuesta 409 con el código AGENDA_BLOCK_OVERLAP, la petición termina en un error interno del servidor que el filtro de seguridad traduce a un 401 de usuario no autorizado, aunque el token sea perfectamente válido. Solo el camino secuencial produce la respuesta correcta.

El defecto real es más amplio de lo que parecía: no hay un solo camino de fallo sino dos, y ambos nacen en el mismo bloque de código.

### ¿Por qué importa?

- El profesional recibe un 401 y cree que su sesión expiró, cuando el problema era de concurrencia.
- No puede distinguir "mi token falló" de "esa franja ya está bloqueada".
- Pierde el detalle de las citas en conflicto que el camino secuencial sí entrega.
- Un cliente que integra la API no puede reaccionar de forma programada al rechazo.
- La integridad de los datos no se pierde: la restricción de exclusión de la base de datos la garantiza. El problema es que el servidor confunde un conflicto esperado con una falla del sistema.

### Pre-requisitos

- Backend corriendo
- Un profesional registrado con horario semanal configurado
- Un rango futuro sin reservas confirmadas ni bloqueos previos
- Dos sesiones activas del mismo profesional (o de un administrador)

### Pasos para reproducir

1. Iniciar sesión como el profesional dueño de la agenda.
2. Preparar una franja futura libre (por ejemplo, el próximo lunes de 9:00 a 12:00).
3. Enviar al mismo tiempo dos solicitudes de bloqueo de agenda con esa misma franja y el mismo profesional.
4. Esperar a que las dos solicitudes terminen.
5. Observar los códigos de respuesta: uno es 201, el otro no es 409.
6. Repetir el paso 3 con ocho solicitudes simultáneas sobre la misma franja.
7. Cerrar sesión y repetir los pasos 2 a 5 de forma secuencial, una solicitud después de otra.

### ¿Qué debería pasar?

La solicitud perdedora debería recibir código 409 con el código de error AGENDA_BLOCK_OVERLAP, igual que ocurre en el camino secuencial, donde el primer bloqueo responde 201 y el segundo 409. El servicio debería traducir el conflicto a un error de negocio, no propagar una excepción técnica.

### ¿Qué pasa realmente?

En el camino concurrente la distribución observada en veinte peticiones fue: diez respuestas 201, ocho respuestas 401 y solo dos respuestas 409. Es decir, el 80 por ciento de las veces el perdedor recibe un 401 en lugar del 409 documentado, y solo dos veces de diez se obtiene el contrato esperado.

El registro del servidor muestra dos causas distintas. La primera es que la restricción de exclusión deja la transacción abortada y, dentro del bloque de captura, se consulta el solapamiento sobre esa transacción ya muerta: la base responde que la transacción está abortada y Hibernate lanza una excepción de sistema de persistencia. La segunda es un interbloqueo real de la base de datos, que llega como excepción de no se puede adquirir el bloqueo. El código actual solo captura la violación de integridad, y esa excepción de interbloqueo pertenece a otra jerarquía, por lo que escapa sin ser traducida.

La medición a nivel de servicio lo confirma: en diez rondas de carrera con barrera de arranque, el total fue de diez bloqueos creados, cero conflictos de negocio, seis interbloqueos y cuatro errores de persistencia. El servicio nunca produjo el código de negocio esperado bajo concurrencia, ni una sola vez.

El camino secuencial sí funciona: responde 201 y luego 409 AGENDA_BLOCK_OVERLAP con el detalle de las citas en conflicto.

### Archivos a investigar

- `src/main/java/com/bookly/backendcf/schedule/application/AgendaBlockService.java:63-69` (bloque try/catch que guarda el bloqueo y consulta el solapamiento dentro de la transacción ya abortada)
- `src/main/java/com/bookly/backendcf/shared/error/GlobalExceptionHandler.java` (no maneja la excepción de no se puede adquirir el bloqueo ni la de sistema de persistencia)
- `src/main/java/com/bookly/backendcf/booking/application/service/CreateBookingService.java:128` (referencia: sí traduce el conflicto de concurrencia a respuesta de negocio)

### Criterio de aceptación

- Dos solicitudes simultáneas sobre la misma franja: una recibe 201 y la otra 409 con el código AGENDA_BLOCK_OVERLAP.
- Ninguna solicitud devuelve 401 ni 500 bajo concurrencia.
- El detalle del bloqueo en conflicto se entrega igual que en el camino secuencial.
- El caso de concurrencia pasa de forma estable en varias rondas consecutivas.
- La captura de la violación de integridad relee el solapamiento en una transacción nueva, no en la que ya abortó.

---

## S3-02 — El reporte de ocupación ignora los bloqueos de agenda

**Title (Azure):** `[HU-15] El reporte de ocupación no descuenta los bloqueos de agenda de la capacidad`

**Severity:** Critical  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, hu-05, ocupacion, sprint-3

### Descripción

El reporte de ocupación calcula la capacidad de cada profesional únicamente a partir de su horario semanal. Los bloqueos temporales de agenda nunca se consultan, por lo que un profesional con horas bloqueadas sigue reportando exactamente la misma capacidad que antes del bloqueo. El dato que ve el administrador es incorrecto.

### ¿Por qué importa?

- El administrador que decide contratación o carga trabaja con una capacidad inflada de forma sistemática.
- El porcentaje de ocupación de los días con bloqueo sale demasiado bajo, porque el denominador no refleja la indisponibilidad real.
- La misma franja es "libre" en el módulo de disponibilidad y "capacidad" en el módulo de reporte, para el mismo profesional y el mismo instante.
- El defecto no es visible sin cruzar dos historias: el puerto de lectura del reporte ni siquiera expone el dato.
- El administrador no puede explicar por qué una franja que el sistema declara ocupada sigue contando como capacidad disponible.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con horario semanal configurado y sin reservas
- Un rango dentro de la ventana del reporte

### Pasos para reproducir

1. Iniciar sesión como administrador.
2. Consultar el reporte de ocupación del periodo mensual y anotar los minutos de capacidad del profesional y su desglose por día de la semana.
3. Crear un bloqueo de agenda de 240 minutos dentro de la ventana del reporte (por ejemplo, el viernes de 14:00 a 18:00).
4. Consultar de nuevo el reporte de ocupación del periodo mensual.
5. Comparar los minutos de capacidad antes y después.
6. Consultar la disponibilidad del mismo profesional para ese mismo día.

### ¿Qué debería pasar?

Los minutos de capacidad deberían bajar en 240, tanto en el desglose por día de la semana como en el total por profesional, de la misma forma en que la disponibilidad deja de ofrecer esas horas.

### ¿Qué pasa realmente?

La diferencia de capacidad es cero. Los minutos de capacidad del profesional siguen en 5280 antes y después del bloqueo, igual que el desglose del viernes con 3000 minutos. Los 240 minutos bloqueados no aparecen ni en el desglose por día ni en el total por profesional.

La contradicción se demuestra en el mismo instante: la disponibilidad de ese profesional para ese mismo día responde que no tiene horarios libres y devuelve la lista de días vacía, mientras el reporte afirma que tiene 3000 minutos de capacidad. Dos módulos distintos describen el mismo intervalo de forma incompatible.

La causa está en el código: el puerto de lectura del reporte expone únicamente los identificadores de profesionales, las ventanas semanales, las reservas que ocupan y los nombres. No expone ninguna forma de consultar los bloqueos, y el adaptador nunca inyecta el repositorio de bloqueos. En contraste, el adaptador de disponibilidad sí incluye los bloqueos como periodos ocupados.

Una sutileza operativa: la API rechaza los bloqueos en el pasado, así que el bloqueo debe ser de hoy o futuro. Eso no impide la reproducción, porque hoy pertenece a la ventana del reporte.

### Archivos a modificar

- `src/main/java/com/bookly/backendcf/report/application/OccupancyQuery.java:12-25` (agregar un método que exponga los bloqueos vigentes en la ventana)
- `src/main/java/com/bookly/backendcf/report/infrastructure/persistence/OccupancyQueryAdapter.java` (el adaptador solo consulta reservas; falta inyectar el repositorio de bloqueos)
- `src/main/java/com/bookly/backendcf/report/domain/model/OccupancyCalculator.java:20-30` y `:33-43` (restar los minutos bloqueados de la capacidad)
- `docs/HU-15-reporte-ocupacion.md` (definir si los bloqueos ya consumidos en el pasado se imputan o se ignoran)

### Criterio de aceptación

- Un bloqueo de N minutos reduce en N los minutos de capacidad del profesional, del día y del total.
- La capacidad del reporte y la disponibilidad coinciden para el mismo profesional en el mismo periodo.
- El desglose por día de la semana refleja los bloqueos de ese día.
- El caso de integración que cruza bloqueo y reporte pasa de forma permanente.

---

## S3-03 — Dos listas de estados que ocupan la agenda, duplicadas y sin fuente común

**Title (Azure):** `[HU-15] La disponibilidad y el reporte no comparten una única definición de qué reserva ocupa agenda`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, hu-07, ocupacion, sprint-3

### Descripción

La disponibilidad y el reporte de ocupación aplican criterios distintos de ocupación, y cada módulo define su propio conjunto de estados sin una fuente común. La disponibilidad consulta las reservas solapadas filtrando solo por el estado confirmado. El reporte usa un conjunto propio que incluye también el estado de reserva ya atendida.

La diferencia es real en el código, pero no es alcanzable por el usuario hoy: la API solo produce reservas confirmadas y canceladas, y en esos dos estados los dos módulos coinciden.

### ¿Por qué importa?

- Cualquier decisión de negocio tomada con el reporte opera con una ventana que no coincide con la ventana de lo que el cliente puede ver y reservar.
- El día que exista un endpoint de cierre de atención, una reserva atendida liberará la franja para el cliente mientras el reporte seguirá contándola como ocupada: exactamente el defecto descrito, pero hoy latente.
- El estado de reserva reprogramada está en el limbo en los tres puntos de control de ocupación, con lo que el mismo problema se repite por tercera vía.
- Dos listas duplicadas en dos módulos son una bomba de relojería: el próximo estado nuevo las desincroniza otra vez.

### Pre-requisitos

- Backend corriendo
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Un servicio de 30 minutos de duración
- Acceso a la base de datos para cambiar el estado de una reserva

### Pasos para reproducir

1. Crear una reserva confirmada de 60 minutos en un día con horario, dentro de la ventana del reporte.
2. Consultar la disponibilidad del profesional y anotar las horas ofrecidas.
3. Consultar el reporte de ocupación y anotar los minutos reservados del profesional.
4. Consultar el reporte de ocupación de nuevo y confirmar que los minutos reservados son los mismos que con la reserva confirmada.
5. Cambiar el estado de esa misma reserva a atendida directamente en la base de datos.
6. Consultar de nuevo la disponibilidad: ahora la franja vuelve a ofrecerse.
7. Consultar de nuevo el reporte de ocupación y comparar los minutos reservados.
8. Repetir los pasos 1 a 7 con una reserva que se cancela mediante la API, en lugar de cambiar su estado a mano.

### ¿Qué debería pasar?

La disponibilidad y el reporte deberían aplicar el mismo criterio de ocupación, definido en un único lugar. Si una reserva atendida ocupa agenda, la franja no debería volver a aparecer en la disponibilidad. Si no ocupa agenda, el reporte no debería contarla. Y el estado reprogramado debería estar incluido de forma explícita o excluida de forma explícita.

### ¿Qué pasa realmente?

En el estado confirmado, los dos módulos coinciden: la disponibilidad pierde la hora de la reserva y el reporte suma los minutos reservados. En el estado cancelado, los dos módulos coinciden también: la disponibilidad recupera la hora y el reporte descuenta los minutos.

Solo el estado de reserva atendida produce la discrepancia, y para alcanzarla hay que cambiar el estado directamente en la base de datos, porque ningún endpoint del alcance actual la produce. En ese caso la disponibilidad vuelve a ofrecer la franja y el reporte la sigue contando, y el reporte incluye el estado atendido entre los que considera ocupantes.

El defecto real que queda documentado es la duplicación: dos listas de estados, en dos módulos, sin fuente común. El estado reprogramada está ausente en los tres puntos de control: la restricción de exclusión de la base, la consulta de solapamientos de la disponibilidad y el conjunto de estados del reporte.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/booking/infrastructure/persistence/BookingRepository.java:29` (consulta usada por la disponibilidad: filtra solo por estado confirmado)
- `src/main/java/com/bookly/backend/cf/report/infrastructure/persistence/OccupancyQueryAdapter.java:25-26` (conjunto de estados que el reporte considera ocupantes)
- `src/main/java/com/bookly/backend/cf/booking/domain/model/BookingStatus.java` (candidato natural para un método único que defina si un estado ocupa agenda)

### Casos adicionales que también fallan

- El estado de reserva atendida solo puede existir por acceso directo a la base de datos, de modo que hoy no está cubierto por ninguna prueba de la API.
- El estado reprogramada está ausente en los tres puntos de control de ocupación.

### Criterio de aceptación

- La disponibilidad y el reporte aplican el mismo criterio de ocupación, definido en un único lugar del código.
- Existe una política explícita y documentada para el estado atendido y para el estado reprogramada.
- Un caso de integración verifica que el número de huecos ofrecidos es coherente con los minutos reportados.

---

## S3-04 — El porcentaje de ocupación se recorta en silencio a cien

**Title (Azure):** `[HU-15] El reporte de ocupación recorta el porcentaje a 100 sin avisar`

**Severity:** High  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, ocupacion, exactitud, sprint-3

### Descripción

Cuando los minutos reservados superan los minutos de capacidad, el reporte devuelve el porcentaje 100.0 sin ninguna advertencia. El valor real era 140 y el consumidor nunca puede saber que el dato fue truncado. La respuesta no incluye ningún campo que indique la saturación.

El defecto no necesita datos anómalos: se alcanza por la API normal, porque el profesional puede reducir su propio horario después de vender los huecos.

### ¿Por qué importa?

- Un administrador ve "100% de ocupación" y no puede distinguir "agenda al límite" de "dato inflado por una capacidad mal calculada".
- El recorte enmascara el defecto de capacidad inflada: sin el truncamiento, el valor real habría apuntado directamente a la causa raíz.
- Si el valor se usa como indicador para fijar tarifas o turnos, la plataforma decide sobre un número que no refleja lo ocurrido.
- El profesional puede provocar el estado por una vía legítima y corriente, lo que significa que no es un caso límite que se puede ignorar.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Un servicio de 30 minutos de duración

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Reservar siete huecos consecutivos de 30 minutos un día con horario, en una fecha de esta semana.
3. Iniciar sesión como administrador y consultar el reporte de ocupación del periodo mensual.
4. Anotar los minutos de capacidad y los minutos reservados del profesional.
5. Entrar de nuevo como el profesional y reducir su horario de ese día a una sola franja de 30 minutos.
6. Consultar de nuevo el reporte de ocupación del periodo mensual.
7. Anotar de nuevo los minutos de capacidad, los minutos reservados y el porcentaje de ocupación.

### ¿Qué debería pasar?

El servidor debería devolver el porcentaje real de ocupación o, si el contrato obliga a un tope, debería incluir una marca explícita que indique que el valor fue saturado y el valor sin recortar. Además, debería quedar definido si el sistema permite reducir un horario que deja reservas confirmadas fuera de su franja.

### ¿Qué pasa realmente?

Con siete reservas de 30 minutos, los minutos reservados son 210. Tras reducir el horario del día a 30 minutos, la capacidad de la ventana baja a 150. El reporte devuelve un porcentaje de ocupación de 100.0 cuando el valor real es 140. La reproducción es determinista: se repitió tres veces y dio el mismo resultado.

La causa raíz es doble. La primera es que el cálculo aplica un tope de cien de forma incondicional, sin ninguna marca de saturación, y el modelo de respuesta no tiene ningún campo que indique que el valor fue recortado ni cuál era el valor sin recortar. La segunda, y más importante, es que la API permite reducir el horario de un profesional en cualquier momento, incluso con reservas confirmadas vigentes, sin ninguna restricción.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:63-70` (el tope se aplica de forma incondicional)
- `src/main/java/com/bookly/backend/cf/report/presentation/dto/OccupancyReportResponse.java` (agregar una marca de saturación y el valor sin recortar)
- `src/main/java/com/bookly/backend/cf/schedule/application/WeeklyScheduleService.java` (decidir si el reemplazo de horario debe rechazar franjas que dejen reservas confirmadas fuera)
- `docs/openapi.yaml` (el campo de porcentaje global declara un máximo de 100; documentar el comportamiento)

### Criterio de aceptación

- El porcentaje devuelto coincide con el cálculo real, o bien la respuesta indica de forma explícita que fue saturado.
- Cuando los minutos reservados superan a la capacidad, queda una señal visible en la respuesta o en el registro de auditoría.
- La documentación de la API describe el comportamiento del valor saturado.
- Está definido y documentado si se puede reducir un horario con reservas confirmadas vigentes, y el comportamiento implementado es el acordado.

---

## S3-05 — La capacidad se infla cuando el horario tiene franjas solapadas

**Title (Azure):** `[HU-15] La capacidad reportada se infla cuando el horario tiene franjas solapadas`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, hu-04, capacidad, sprint-3

### Descripción

Cuando un profesional tiene dos franjas que se solapan el mismo día, el reporte las suma como minutos independientes en lugar de fusionarlas. La capacidad resultante es mayor que el tiempo real en que el profesional está disponible. En el caso medido, dos franjas que cubren 300 minutos reales se reportan como 360.

La API nunca permite crear esta situación: rechaza el solape antes de llegar a la base de datos. El defecto real es una asimetría de defences en el esquema, no un fallo del servicio de cara al usuario.

### ¿Por qué importa?

- La capacidad se infla de forma proporcional al grado de solapamiento: en el caso medido, un 20 por ciento.
- Como la capacidad es el denominador del porcentaje, todos los porcentajes de ocupación del sprint están subestimados.
- Cualquier carga de datos que no pase por la API (semillas, migraciones, scripts de carga, procesos masivos, consola) introduce franjas solapadas que inflan la capacidad sin que ningún control del sistema lo detecte.
- Es la contraparte del defecto S3-07: allí la falta de fusión duplica la oferta, aquí infla la capacidad.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con dos franjas solapadas el mismo día (por ejemplo, de 9:00 a 12:00 y de 11:00 a 14:00)
- Acceso a la base de datos para insertar la segunda franja

### Pasos para reproducir

1. Intentar configurar para un profesional dos franjas el mismo día, de 9:00 a 12:00 y de 11:00 a 14:00, usando el servicio de configuración de horario.
2. Observar que la operación se rechaza con un conflicto de horario y que no se guarda nada.
3. Insertar directamente en la base de datos la segunda franja solapada.
4. Consultar el reporte de ocupación del periodo mensual.
5. Anotar los minutos de capacidad de ese profesional y los del día lunes.
6. Calcular a mano la cobertura real: las dos franjas fusionadas cubren 300 minutos.

### ¿Qué debería pasar?

El servidor debería reportar 300 minutos de capacidad para ese lunes, que es el tiempo real en que el profesional está disponible, fusionando las franjas solapadas antes de sumarlas. Además, la base de datos debería impedir el solape, como ya hace con las reservas y con los bloqueos de agenda.

### ¿Qué pasa realmente?

El servicio rechaza correctamente el solape: la API responde con un conflicto de horario y un detalle que señala qué franja se superpone con cuál. El control contiguo, con dos franjas que se tocan pero no se solapan, se acepta sin problema.

Cuando la franja se inserta por la vía directa en la base de datos, el efecto sí aparece: la capacidad del lunes sube de 1200 a 1920 minutos, es decir 720 minutos de más, que corresponde a 180 minutos extra por cada uno de los cuatro lunes de la ventana. La cobertura real de la unión de intervalos es 300 minutos por lunes, no 360.

La asimetría es verificable en el esquema: hay tres tablas con rango temporal, y dos de las tres tienen una restricción de exclusión que impide el solape. La tabla del horario semanal es la única sin ella, y su única defensa es el servicio, que la aplica por encima de la capa de aplicación.

En el caso de la semilla de pruebas, la inflación medida sobre el profesional con franjas solapadas es de 240 minutos, un 5.41 por ciento.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:20-30` y `:33-43` (las franjas se suman sin fusionar los intervalos solapados)
- `src/main/java/com/bookly/backend/cf/availability/domain/model/AvailabilityCalculator.java` (fusionar las ventanas del día, resuelve también el defecto S3-07)
- Migración nueva que añade a la tabla del horario semanal una restricción de exclusión que impida franjas solapadas, en línea con las que ya tienen las reservas y los bloqueos de agenda
- `qa-tests/sprint3/seed.sh` (corregir la franja solapada de la semilla, que falsea la medición de todo el sprint)

### Casos adicionales que también fallan

- La capacidad por día de la semana y la capacidad por profesional calculan de forma independiente, por lo que pueden divergir.
- El cálculo de disponibilidad arrastra la misma falta de fusión y produce el síntoma espejo en el módulo de disponibilidad (defecto S3-07).

### Criterio de aceptación

- Dos franjas solapadas del mismo día reportan la cobertura real de la unión de los intervalos.
- El cálculo por día de la semana y el cálculo por profesional devuelven la misma suma.
- La base de datos impide guardar franjas parcialmente solapadas, con el mismo nivel de defensa que las reservas y los bloqueos de agenda.
- La semilla de pruebas deja de producir franjas solapadas que la API no permite crear.

---

## S3-06 — La ruta antigua de historial no pagina ni deja rastro en auditoría

**Title (Azure):** `[HU-13] La ruta de historial por identificador de cliente no pagina ni audita la lectura`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-13, auditoria, paginacion, sprint-3

### Descripción

Conviven dos rutas para consultar el historial de reservas de un cliente, con contratos incompatibles. La ruta nueva restringe el acceso al rol de cliente y pagina sus resultados. La ruta antigua permite que un administrador lea el historial completo de cualquier cliente, sin paginar y sin dejar registro de auditoría de la lectura. La ruta antigua no deja ninguna traza de quién consultó los datos personales de un tercero.

### ¿Por qué importa?

- Riesgo de denegación de servicio: un cliente con historial largo genera respuestas sin límite, con consumo de memoria del servidor y del cliente. En la medición, 90 reservas ocupan 48 KB sin paginar.
- Sin auditoría no hay forma de demostrar quién consultó los datos personales de un tercero, lo que incumple la trazabilidad que el propio sistema exige en otros módulos.
- Inconsistencia visible: la misma operación de negocio responde 403 por una ruta y 200 por la otra, con el mismo rol y el mismo recurso.
- El bypass del administrador es deliberado y está documentado por compatibilidad, pero esa decisión agrava el defecto: el acceso sin rastro es intencional, y por eso hay que instrumentarlo.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un cliente con historial largo (en la prueba, 90 reservas)

### Pasos para reproducir

1. Iniciar sesión como administrador.
2. Consultar el historial de reservas del cliente usando la ruta que incluye el identificador del cliente.
3. Observar que la respuesta llega completa, sin paginación, con un tamaño cercano a 50 KB.
4. Revisar el registro de auditoría del sistema en busca de una entrada que registre esa lectura.
5. Cerrar sesión, iniciar sesión como el propio cliente y consultar la ruta nueva de historial.
6. Consultar la ruta nueva de historial con el rol de administrador.

### ¿Qué debería pasar?

La ruta debería aplicar paginación como la ruta nueva y dejar una entrada de auditoría con el administrador que consultó, el cliente consultado, la dirección de origen y la fecha. El criterio de rol debería ser el mismo en las dos rutas.

### ¿Qué pasa realmente?

El administrador recibe las 90 reservas en una sola respuesta de 48 KB sin paginar y no queda ningún registro de la lectura: el conteo de la tabla de auditoría no cambia. La ruta nueva sí pagina, pero restringe el acceso al rol de cliente: el administrador recibe 403 y, por lo tanto, no puede ni siquiera comparar el comportamiento de las dos rutas con el mismo rol.

El detalle importante es que la ausencia de auditoría no es un olvido del código sino una restricción del esquema: la tabla de auditoría tiene una restricción de comprobación que solo admite tres tipos de acción, y las tres son de escritura. No existe ninguna acción de lectura, y añadirla requiere una migración.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/booking/presentation/controller/BookingController.java:67-71` (la ruta antigua devuelve la lista completa sin paginación)
- `src/main/java/com/bookly/backend/cf/shared/security/OwnershipGuard.java:24-26` (el acceso concedido por administrador no deja rastro en auditoría)
- `src/main/java/com/bookly/backend/cf/audit/domain/model/AuditActionType.java` (añadir el tipo de acción de lectura)
- Migración nueva que amplíe la restricción de tipos de acción de la tabla de auditoría
- `src/main/java/com/bookly/backend/cf/shared/configuration/SecurityConfiguration.java:126-128` (unificar el criterio de rol entre las dos rutas)

### Casos adicionales que también fallan

- La ruta antigua no filtra por estado de reserva, a diferencia de la ruta nueva.
- Dos rutas con reglas de rol opuestas para la misma operación de negocio.
- La ruta equivalente de historial por profesional tiene el mismo defecto (ver el bug S3-13).

### Criterio de aceptación

- La ruta antigua devuelve el historial paginado, con el mismo formato de página que la ruta nueva.
- Toda lectura del historial de un cliente por parte de un administrador queda registrada en la auditoría.
- El criterio de rol es idéntico en las dos rutas y está documentado.
- La ruta antigua queda marcada como obsoleta o se elimina en la versión mayor de la API.

---

## S3-07 — La disponibilidad no fusiona las franjas del día antes de generar los huecos

**Title (Azure):** `[HU-07] La disponibilidad no fusiona las franjas solapadas antes de generar los huecos`

**Severity:** Low  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-07, disponibilidad, robustez, sprint-3

### Descripción

El cálculo de disponibilidad genera las franjas hora por hora, sin fusionar las franjas del horario que se solapan ni eliminar las horas repetidas del resultado. El cliente recibiría la misma hora de inicio más de una vez.

La duplicación exige franjas solapadas, y la API las rechaza con un conflicto de horario. Una búsqueda exhaustiva de todas las combinaciones de dos y tres franjas disjuntas, en una rejilla de 30 minutos entre las 8:00 y las 18:00 y para duraciones de 30, 45, 60 y 90 minutos, no encontró ningún contraejemplo. El defecto es real en el código, pero es latente.

### ¿Por qué importa?

- Si la validación del servicio se relaja, o si entra una carga de datos por la vía directa a la base, el cliente ve la misma hora ofrecida más de una vez.
- Un cliente que pulse dos veces sobre la misma hora puede disparar dos intentos de reserva sobre el mismo rango.
- El número de huecos libres que se le muestra no corresponde con los huecos reales, por lo que un contador de "horarios disponibles" no es fiable.
- Es el mismo defecto conceptual de S3-05, y se corrige con la misma solución: fusionar los intervalos.

### Pre-requisitos

- Backend corriendo
- Un profesional con dos franjas que se solapan el mismo día (de 9:00 a 12:00 y de 11:00 a 14:00)
- Un servicio de 60 minutos de duración
- Acceso a la base de datos para insertar la franja solapada

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Intentar configurar para un profesional dos franjas solapadas el mismo día usando el servicio de horario.
3. Observar que la operación se rechaza con un conflicto de horario.
4. Insertar directamente en la base de datos la segunda franja solapada.
5. Consultar la disponibilidad del profesional para ese día.
6. Anotar todas las horas de inicio devueltas y contar cuántas se repiten.

### ¿Qué debería pasar?

El servidor debería devolver cada hora disponible una sola vez: cinco horas únicas para el servicio de 60 minutos sobre una cobertura real de 9:00 a 14:00. Las franjas del día deberían fusionarse antes de generar los huecos.

### ¿Qué pasa realmente?

La API rechaza el solape, así que el caso solo es alcanzable insertando la franja por la vía directa a la base de datos. En ese escenario la disponibilidad devuelve seis franjas de las que solo cinco son únicas: la hora 11:00 aparece dos veces. El listado completo es 9:00, 10:00, 11:00, 11:00, 12:00 y 13:00.

El análisis exacto del algoritmo confirma que la duplicación solo puede producirse si dos ventanas comparten intervalo, es decir, si se solapan. Con franjas disjuntas el resultado nunca se repite. La carrera entre dos reconfiguraciones simultáneas del horario tampoco produce franjas mixtas: siempre queda completo uno de los dos horarios solicitados, nunca una mezcla.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/availability/domain/model/AvailabilityCalculator.java:26-44` (las franjas del día se recorren una por una sin fusionarlas)

### Criterio de aceptación

- Cada hora de inicio aparece una sola vez en la respuesta de disponibilidad.
- Las franjas solapadas del día se fusionan antes de generar los huecos.
- El número de huecos ofrecidos coincide con la cobertura real del horario.
- Una prueba de regresión verifica el invariante con dos ventanas solapadas, aunque la API hoy no permita crearlas.

---

## S3-08 — Un día con reservas pero sin horario se reporta como cero por ciento

**Title (Azure):** `[HU-15] El reporte muestra 0% de ocupación en días con reservas y sin horario`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, ocupacion, datos, sprint-3

### Descripción

Cuando un día no tiene horario configurado, su capacidad es cero, pero el reporte devuelve un porcentaje de ocupación de 0.0. El día aparece simultáneamente con minutos reservados y con ocupación nula, lo que es internamente contradictorio y se presenta como si el día estuviera libre.

### ¿Por qué importa?

- Un día con mucha actividad se muestra como si estuviera libre.
- El porcentaje global también queda sesgado: esos minutos reservados suman al numerador, pero su capacidad es cero, así que inflan el resultado global sin que nada los compense.
- El consumidor no puede distinguir un día sin operación de un día con una inconsistencia de agenda que permitió reservar fuera de horario.
- El modelo de respuesta usa un número decimal primitivo, así que ni siquiera puede expresar un valor nulo que distinga los dos casos.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Un servicio de 60 minutos de duración

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Reservar una hora un día con horario, en una fecha de esta semana.
3. Iniciar sesión como administrador y consultar el reporte de ocupación del periodo mensual.
4. Anotar los minutos reservados y el porcentaje de ocupación del profesional.
5. Entrar de nuevo como el profesional y borrar su horario semanal completo.
6. Consultar de nuevo el reporte de ocupación del periodo mensual.
7. Anotar de nuevo los minutos reservados, los minutos de capacidad y el porcentaje de ocupación.
8. Consultar el reporte y anotar el porcentaje global de ocupación.

### ¿Qué debería pasar?

El servidor debería marcar el día como no calculable, o devolver un valor nulo o un estado explícito, en lugar de presentar un 0% como si fuera un dato de ocupación válido. Y el porcentaje global no debería incorporar minutos reservados de días sin capacidad.

### ¿Qué pasa realmente?

Con el horario borrado, el profesional conserva 60 minutos reservados, su capacidad baja a cero y el porcentaje de ocupación se devuelve como 0.0. El día se muestra con actividad y con ocupación nula al mismo tiempo.

La medición del conjunto de datos de la semilla da un cuadro más amplio: el sábado reporta 870 minutos reservados con capacidad cero y un 0 por ciento, y el domingo 780 minutos reservados con capacidad cero y un 0 por ciento. En total son 1650 minutos de actividad real reportados como 0 por ciento. El informe original de este sprint subestimaba la cifra en 60 minutos.

El sesgo del global es medible: con los minutos de los días sin capacidad incluidos, el porcentaje global es 38.15. Excluyéndolos, es 26.29. El dato global queda inflado en 11.86 puntos porcentuales, porque el cálculo suma todos los minutos reservados y solo los minutos de capacidad de los días con horario.

El caso de la semilla y el caso de la API son el mismo defecto: en los dos, la raíz está en que un profesional puede quedar sin horario mientras conserva reservas confirmadas.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:65-67` (la guarda de capacidad cero devuelve 0.0 en lugar de un valor no calculable)
- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java` (excluir del numerador global los minutos de días con capacidad cero, o publicar el desglose)
- `src/main/java/com/bookly/backend/cf/report/presentation/dto/OccupancyReportResponse.java` (cambiar el tipo del porcentaje a nullable o añadir un indicador de calculabilidad)
- `docs/openapi.yaml` y `docs/HU-15-reporte-ocupacion.md` (documentar el valor nulo y qué significa)

### Casos adicionales que también fallan

- El porcentaje global de ocupación queda sesgado por la misma razón.
- El reporte no distingue el caso "no hay capacidad porque no se trabaja ese día" del caso "no hay datos".

### Criterio de aceptación

- Un día con minutos reservados y sin capacidad no se reporta como 0% de ocupación.
- La documentación de la API indica que ese valor puede ser nulo y qué significa.
- El porcentaje global no incorpora minutos reservados de días sin capacidad.
- Está definido y documentado si se puede borrar el horario de un profesional con reservas confirmadas vigentes.

---

## S3-09 — La API tiene dos contratos de error distintos

**Title (Azure):** `[Transversal] La API devuelve dos formatos de error incompatibles`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** transversal, api, contrato, sprint-3

### Descripción

La plataforma expone dos formatos de respuesta de error distintos. El más extendido es el que define el contrato publicado de la API, con el campo errorCode junto con message, details, traceId y timestamp. Pero el controlador de reservas devuelve una respuesta reducida, con el campo error y sin details, traceId ni timestamp. El contrato publicado describe únicamente el primer formato, y define la respuesta de creación de reservas como si cumpliera el formato completo.

### ¿Por qué importa?

- El cliente debe implementar dos lecturas distintas del mismo concepto, y un cliente que siga fielmente la documentación no encuentra el campo errorCode en las respuestas de error de la creación de reservas.
- La ausencia de traceId impide correlacionar un error reportado por el usuario con la traza del servidor, lo que degrada el soporte.
- La ausencia de details impide entregar información estructurada de contexto, como el detalle de las citas afectadas que el propio módulo de bloqueo de agenda sí provee.
- Agrava el defecto S3-01: el 401 que recibe el perdedor de la carrera tampoco lleva código de negocio ni detalle.

### Pre-requisitos

- Backend corriendo
- Un cliente registrado con su acceso configurado
- Una franja horaria ya ocupada, para provocar un conflicto de reserva

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Provocar un conflicto al crear una reserva en una franja ya ocupada.
3. Observar los campos que devuelve la respuesta de error.
4. Provocar un error de validación en esa misma creación de reserva, por ejemplo sin indicar el servicio.
5. Provocar un error en cualquier otro módulo, por ejemplo al crear un bloqueo de agenda que se solape con otro bloqueo.
6. Comparar los campos de las dos respuestas de error.

### ¿Qué debería pasar?

Todas las respuestas de error de la API deberían tener la misma forma, con errorCode, message, details, traceId y timestamp, tal como define el contrato publicado.

### ¿Qué pasa realmente?

Los conflictos de reserva devuelven error y message, con dos campos. La validación de la misma ruta también devuelve error y message. El resto de la API devuelve errorCode, message, details, traceId y timestamp, con cinco campos. El contrato publicado define el esquema completo con esos cinco campos obligatorios y declara que la respuesta de creación de reservas usa ese esquema, por lo que la implementación no cumple lo que la documentación promete en dos de los códigos de error de esa ruta.

En particular, los conflictos de reserva no llevan identificador de traza, con lo que se pierde la trazabilidad justo en los casos de conflicto, que son los más frecuentes de reportar.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/booking/presentation/controller/BookingController.java:132` (definición local de la respuesta de error reducida, con el campo error)
- `src/main/java/com/bookly/backend/cf/booking/presentation/controller/BookingController.java:105-111` (captura local de las excepciones de conflicto y de argumento inválido)
- `src/main/java/com/bookly/backend/cf/shared/error/GlobalExceptionHandler.java` (debe construir la respuesta estándar para los conflictos de reserva)
- `docs/openapi.yaml` (definición del esquema de error y de la respuesta de creación de reservas)

### Criterio de aceptación

- Todas las respuestas de error de la API contienen errorCode, message, details, traceId y timestamp.
- El contrato público y la implementación coinciden.
- Una prueba de contrato verifica la forma de todas las respuestas de error de cada endpoint.

---

## S3-10 — La reprogramación es una vertical cortada y el estado queda fuera de todo control de ocupación

**Title (Azure):** `[HU-08] La reprogramación de reservas tiene consumidor sin productor y su estado queda fuera de los controles de ocupación`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-08, hu-07, hu-15, sprint-3

### Descripción

El sistema reconoce un estado de reserva "reprogramada" y tiene preparada toda la infraestructura para gestionarlo: el tipo de evento, el tipo de notificación, la plantilla de correo y el manejador que la envía. Pero no existe ni el endpoint ni el servicio que produzca ese estado. El evento de reprogramación es un contrato muerto: hay consumidor, no hay publicador.

En esa misma situación, el estado reprogramada está ausente de los tres puntos de control de ocupación de la agenda.

### ¿Por qué importa?

- El día que se construya el endpoint, una reserva reprogramada podrá solaparse con una confirmada, porque la restricción de exclusión solo contempla el estado confirmado.
- Será invisible en la disponibilidad, de modo que el cliente podrá reservar sobre una franja que ya tiene una cita reprogramada.
- No computará en el reporte, por lo que las horas efectivamente comprometidas no se computan y la ocupación real se subestima.
- Es el mismo defecto conceptual de S3-03, aplicado a un tercer estado, y ambas cosas se resuelven con la misma fuente única de verdad.

### Pre-requisitos

- Backend corriendo
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Acceso a la base de datos para observar el estado de la reserva

### Pasos para reproducir

1. Buscar en el código un endpoint que ponga una reserva en estado reprogramada.
2. Buscar en el código un servicio que publique el evento de reserva reprogramada.
3. Comprobar qué hace el servicio de notificaciones cuando recibe ese evento.
4. Crear una reserva confirmada en una franja concreta.
5. Cambiar el estado de esa reserva a reprogramada directamente en la base de datos.
6. Crear una segunda reserva confirmada en esa misma franja.
7. Consultar la disponibilidad de esa franja: aparece como libre.
8. Consultar el reporte de ocupación: esa franja no computa como ocupada.

### ¿Qué debería pasar?

El sistema debería impedir que una reserva reprogramada y una confirmada ocupen la misma franja, y la reserva reprogramada debería ser visible tanto en la disponibilidad como en el reporte, o quedar excluida de forma explícita de los dos.

### ¿Qué pasa realmente?

No hay ningún endpoint ni servicio que produzca el estado reprogramada: la búsqueda en todo el código de los servicios de reserva solo encuentra dos asignaciones de estado, la de la reserva confirmada y la de la reserva cancelada. En cambio, el evento de reprogramación tiene tres referencias: la definición del evento, el propio evento y el servicio de notificaciones, que envía el correo de reprogramación. Esa rama del manejador nunca se ejecuta.

También quedan artefactos huérfanos: el objeto de petición de reprogramación, con su validación de fecha futura, no lo referencia nadie; el tipo de notificación está definido; la plantilla de correo existe; y el renderizador de plantillas tiene implementado el método correspondiente.

El estado reprogramada está ausente en los tres puntos de control de ocupación: la restricción de exclusión de la base (que solo contempla el estado confirmado), la consulta de solapamientos que usa la disponibilidad y el conjunto de estados que el reporte considera ocupantes.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/booking/domain/events/BookingRescheduledEvent.java` (el evento que nadie publica)
- `src/main/java/com/bookly/backend/cf/notification/application/NotificationService.java:106` (el consumidor vivo del evento muerto)
- `src/main/java/com/bookly/backend/cf/booking/domain/model/BookingStatus.java` (definir si el estado ocupa agenda)
- `src/main/java/com/bookly/backend/cf/booking/infrastructure/persistence/BookingRepository.java:29` (consulta de solapamientos usada por la disponibilidad)
- `src/main/java/com/bookly/backend/cf/report/infrastructure/persistence/OccupancyQueryAdapter.java:25-26` (conjunto de estados que el reporte considera ocupantes)

### Casos adicionales que también fallan

- Ningún endpoint del alcance actual produce una reserva reprogramada, por lo que el estado no está cubierto por pruebas.
- El estado reprogramada sí genera notificaciones, lo que confirma que existe en el dominio pero no está integrado en la agenda.

### Criterio de aceptación

- Está definido y documentado si la reserva reprogramada ocupa agenda.
- La base de datos impide el solapamiento entre los estados que ocupan agenda.
- La disponibilidad y el reporte incluyen la reserva reprogramada según la definición acordada.
- El endpoint de reprogramación no se construye hasta que la decisión esté tomada, o los artefactos huérfanos se eliminan.

---

## S3-11 — El reporte de ocupación no es consultable para un periodo arbitrario

**Title (Azure):** `[Transversal] El reporte de ocupación solo admite la ventana anclada a hoy y no documenta esa limitación`

**Severity:** Low  
**Area Path:** CodeFactory EAV10  
**Tags:** transversal, hu-07, hu-15, sprint-3

### Descripción

El reporte de ocupación construye siempre una ventana que termina hoy: los últimos siete días o los últimos treinta días. No admite fechas de inicio y de fin. La disponibilidad, en cambio, sí acepta un rango arbitrario de hasta 31 días.

La afirmación original de que los dos módulos nunca observan el mismo periodo era falsa: el rango que va de ayer a hoy es válido en ambos. La causa es una decisión de diseño documentada, no un descuido.

### ¿Por qué importa?

- No se puede auditar un periodo histórico concreto, que es el caso de uso analítico principal de un reporte de ocupación.
- El reporte no se puede comparar con un rango de disponibilidad fuera de la ventana actual.
- El contrato publicado de la API no expone los parámetros de rango, así que un consumidor no puede saber de antemano que solo puede pedir dos ventanas fijas.
- El criterio de aceptación original ("un caso de integración compara, para el mismo rango, los minutos bloqueados en la disponibilidad contra los minutos de capacidad del reporte") sí es ejecutable usando hoy como extremo final en la disponibilidad, de modo que no bloquea la entrega.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un cliente registrado con su acceso configurado

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Consultar la disponibilidad pidiendo un rango que va de ayer a hoy.
3. Observar que la respuesta es válida y devuelve los huecos del día de hoy.
4. Consultar la disponibilidad pidiendo un rango que termina ayer.
5. Iniciar sesión como administrador.
6. Consultar el reporte de ocupación del periodo semanal y del mensual.
7. Intentar consultar el reporte pidiendo un rango de fechas anterior al mes en curso.

### ¿Qué debería pasar?

Debería existir la posibilidad de consultar el reporte para un periodo arbitrario, o al menos que la limitación esté declarada de forma explícita en la documentación de la API y en la respuesta, para que el consumidor sepa qué ventana cubre el dato que recibe.

### ¿Qué pasa realmente?

El rango que va de ayer a hoy es válido en la disponibilidad y devuelve los huecos de hoy. El reporte ignora los parámetros de rango que se le envían y responde siempre con su ventana fija: el periodo mensual va del día 29 anterior a hoy, y el semanal del día 6 anterior a hoy. Como hoy pertenece a las dos ventanas, existe un rango contrastable.

La ventana fija es deliberada: la documentación de la historia de usuario lo dice de forma expresa ("ambos terminan hoy") y el contrato publicado solo expone el parámetro de periodo con dos valores, sin parámetros de rango.

Lo que falta es la declaración de esa limitación y, sobre todo, que la respuesta exponga con claridad qué ventana cubre. La respuesta ya incluye las fechas de inicio y fin, pero el contrato no lo anuncia como parte del contrato.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/report/application/OccupancyReportService.java:32-33` (ventana fija calculada contra el reloj del servidor)
- `src/main/java/com/bookly/backend/cf/availability/application/AvailabilityService.java:100-104` (validación del rango de la disponibilidad)
- `docs/HU-15-reporte-ocupacion.md` (descripción del parámetro de periodo)
- `docs/openapi.yaml:638-643` (parámetros publicados del endpoint)

### Criterio de aceptación

- La limitación del reporte está documentada de forma explícita en el contrato de la API y en la documentación de la historia de usuario.
- La respuesta expone con claridad la ventana que cubre.
- El reporte acepta fechas de inicio y fin opcionales, con la ventana actual como valor por defecto, o la decisión de no hacerlo queda documentada con su justificación.
- Existe un caso de integración que compara, para el mismo rango, los minutos bloqueados en la disponibilidad contra los minutos de capacidad del reporte.

---

## S3-12 — Cualquier excepción no modelada se convierte en un 401 falso

**Title (Azure):** `[Transversal] Una excepción no modelada se traduce en un 401 de usuario no autorizado en lugar de un 500`

**Severity:** High  
**Area Path:** CodeFactory EAV10  
**Tags:** transversal, errores, seguridad, sprint-3

### Descripción

El manejador global de excepciones no tiene ningún manejador genérico, y ninguna de las excepciones que escapan de la aplicación está en su lista. Cuando una excepción no modelada llega hasta el despachador de la aplicación, el filtro de traducción de excepciones del framework de seguridad la intercepta y responde con el punto de entrada de autenticación: un 401 que dice "Se requiere autenticación".

El efecto es que un fallo interno del servidor se presenta al cliente como un problema con su token. Es la causa raíz del 401 que se观测a en el defecto S3-01.

### ¿Por qué importa?

- El cliente cree que su token expiró cuando el problema era del servidor: pierde tiempo, reintenta y, si es un cliente integrado, su lógica de renovación de credenciales se dispara en cascada.
- Afecta a toda clase de error no modelada: interbloqueos, fallos de serialización, errores del pool de conexiones, referencias nulas.
- Un 404 de enrutamiento también llega como 401, lo que confirma que no es un caso aislado sino el mecanismo general de la clase de errores.
- El código de negocio que el cliente debería recibir se pierde: el detalle de las citas en conflicto, el identificador de traza, la marca de tiempo.

### Pre-requisitos

- Backend corriendo
- Cualquier usuario con un token válido
- Un endpoint protegido que provoque una excepción no modelada

### Pasos para reproducir

1. Iniciar sesión como cualquier usuario con un token válido.
2. Consultar una dirección de la API que no tenga manejador, pero que esté bajo una ruta protegida (por ejemplo, una ruta de reservas con un identificador inexistente).
3. Observar el código de respuesta y el cuerpo de la respuesta.
4. Provocar un interbloqueo concurrente al crear dos bloqueos de agenda sobre la misma franja.
5. Observar el código de respuesta y el cuerpo de la respuesta.
6. Revisar el registro del servidor en busca de la excepción técnica que se produjo.

### ¿Qué debería pasar?

Una excepción no modelada debería traducirse en un error 500 con el formato de error estándar de la plataforma, con su código, su mensaje, su detalle, su identificador de traza y su marca de tiempo. Nunca debería responderse 401 a un error del servidor.

### ¿Qué pasa realmente?

La consulta a una dirección sin manejador devuelve 401 con el mensaje "Se requiere autenticación" y un identificador de traza, pese a que el token es válido y a que el problema real es que la dirección no existe. La revisión del manejador global de excepciones confirma que no existe ningún manejador genérico, y que las dos excepciones que escapan en la carrera de bloqueos no están en su lista.

El registro del servidor confirma la cadena completa: la excepción escapa del despachador, llega al filtro de traducción de excepciones y se convierte en la respuesta 401.

Corregir este defecto por sí solo cambia el 401 por un 500, que ya es honesto. No resuelve el defecto de negocio de S3-01, que sigue necesitando su propio arreglo, pero elimina toda una clase de confusiones para el cliente.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/shared/error/GlobalExceptionHandler.java` (añadir un manejador genérico que nunca responda 401)
- `src/main/java/com/bookly/backend/cf/shared/error/ApiErrorResponse.java` (formato estándar de error)
- `src/main/java/com/bookly/backend/cf/shared/configuration/SecurityConfiguration.java` (configuración del filtro de traducción de excepciones)

### Criterio de aceptación

- Cualquier excepción no modelada devuelve 500 con el formato de error estándar, con identificador de traza y marca de tiempo.
- Ninguna respuesta 401 se genera como consecuencia de una excepción del servidor.
- Una prueba verifica que una ruta protegida sin manejador devuelve 404 y no 401.
- El defectuoso S3-01 deja de manifestarse como 401 una vez corregido este punto.

---

## S3-13 — La ruta de historial por profesional tampoco pagina ni audita

**Title (Azure):** `[HU-13] La ruta de historial por identificador de profesional no pagina ni audita la lectura`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-13, auditoria, paginacion, sprint-3

### Descripción

La ruta que devuelve el historial de reservas de un profesional tiene exactamente el mismo problema que la ruta de historial por cliente: devuelve un listado plano sin paginar y no deja rastro de la lectura en la tabla de auditoría, aunque un administrador pueda consultar el historial de un profesional sin dejar marca.

No estaba incluida en el informe original de este sprint; se descubrió al extender el análisis del defecto S3-06.

### ¿Por qué importa?

- Una respuesta sin techo: el historial de un profesional con muchas reservas se devuelve completo, con el mismo riesgo de denegación de servicio que el caso de cliente.
- Se leen datos personales de terceros (las reservas incluyen datos del cliente que las contrató) sin dejar registro de quién los consultó.
- El criterio de rol es incoherente entre las rutas equivalentes: la de cliente restringe, la de profesional no.
- Dos rutas con el mismo defecto y ningún control es la señal de que el patrón está en el guardián de acceso, no en un endpoint suelto.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con historial largo (en la prueba, 80 reservas)

### Pasos para reproducir

1. Iniciar sesión como administrador.
2. Consultar el historial de reservas de un profesional usando la ruta que incluye el identificador del profesional.
3. Observar que la respuesta llega completa, sin paginación, con un tamaño cercano a 43 KB.
4. Revisar el registro de auditoría del sistema en busca de una entrada que registre esa lectura.

### ¿Qué debería pasar?

La ruta debería aplicar paginación y dejar una entrada de auditoría con el actor, el recurso consultado, la dirección de origen y la fecha, igual que se decide para la ruta de historial por cliente.

### ¿Qué pasa realmente?

El administrador recibe las 80 reservas del profesional en una sola respuesta de 43 KB, sin metadatos de paginación, y no queda ningún registro de la lectura. El mismo guardián de acceso que concede el acceso al administrador sin dejar rastro, y sin explicar por qué lo concede, es el que opera en esta ruta.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/booking/presentation/controller/BookingController.java` (la ruta por profesional devuelve la lista completa sin paginación)
- `src/main/java/com/bookly/backend/cf/shared/security/OwnershipGuard.java:24-26` (el acceso concedido por administrador no deja rastro en auditoría)
- `src/main/java/com/bookly/backend/cf/booking/application/service/ProfessionalBookingHistoryService.java` (servicio paginado equivalente disponible para delegar)
- `src/main/java/com/bookly/backend/cf/shared/configuration/SecurityConfiguration.java` (unificar el criterio de rol con la ruta de cliente)

### Criterio de aceptación

- La ruta por profesional devuelve el historial paginado, con el mismo formato de página que la ruta nueva de cliente.
- Toda lectura del historial de un profesional por parte de un administrador queda registrada en la auditoría.
- El criterio de rol es idéntico en las dos rutas por profesional y por cliente, y está documentado.
- La ruta queda marcada como obsoleta o se elimina en la versión mayor de la API.

---

## S3-14 — La carrera al reemplazar el horario responde 401 y descarta silenciosamente el horario del perdedor

**Title (Azure):** `[HU-05] Dos reemplazos concurrentes del horario producen un 401 y pierden en silencio la configuración del perdedor`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-05, hu-04, concurrencia, sprint-3

### Descripción

Cuando dos solicitudes simultáneas reemplazan el horario semanal de un profesional, una de ellas recibe 401 y la otra 200. El detalle grave es que el 200 no significa que el horario del perdedor se haya guardado: su configuración desaparece sin que nadie se entere.

Se descubrió al estudiar la validación de franjas solapadas en el experimento de concurrencia de S3-07.

### ¿Por qué importa?

- El profesional cree que tiene su horario configurado y en realidad tiene otro: el cliente ve el horario guardado y coincide con lo que envió, mientras el profesional ve en el panel un horario que nunca ordenó. Creencias divergentes entre las dos vistas.
- Un 401 que en realidad es un conflicto de escritura es el mismo mecanismo de S3-12: el cliente cree que su sesión expiró.
- El fallo es silencioso: el sistema devuelve un 200 a la petición que ganó, así que ninguna de las dos partes ve un error que explique la pérdida de datos.
- Se pierden datos de configuración de agenda, que es la información que sostiene todo el resto del sistema.

### Pre-requisitos

- Backend corriendo
- Un profesional registrado con horario semanal configurado
- Dos sesiones activas del mismo profesional (o de un administrador)

### Pasos para reproducir

1. Iniciar sesión como el profesional dueño de la agenda.
2. Preparar dos horarios distintos para el mismo profesional, por ejemplo uno de lunes a miércoles y otro de lunes y miércoles.
3. Enviar al mismo tiempo las dos solicitudes de reemplazo del horario.
4. Esperar a que las dos solicitudes terminen.
5. Observar los códigos de respuesta: uno es 401 y el otro 200.
6. Consultar el horario guardado del profesional y compararlo con los dos horarios enviados.
7. Repetir el paso 3 varias veces y observar que el resultado es siempre el mismo patrón.

### ¿Qué debería pasar?

Las dos solicitudes deberían competir de forma explícita: una gana con 200 y la otra recibe 409 con un mensaje que explique que hay un conflicto de horario y que vuelva a intentarlo. En ningún caso debería perderse la configuración de una de las dos peticiones.

### ¿Qué pasa realmente?

En las seis rondas de la carrera, siempre queda uno de los dos horarios completos y nunca una mezcla ni un solape: la operación de borrar, confirmar y guardar es atómica por transacción, así que no genera datos corruptos. Pero una de las dos peticiones siempre recibe 401, y el horario del perdedor desaparece.

El registro del servidor muestra la causa: al borrar las filas del horario, la otra transacción ya las había borrado, así que no hay ninguna fila que eliminar y la operación lanza un error de bloqueo optimista. Como ese error no tiene manejador, acaba en un 401 por el mismo mecanismo de S3-12.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/schedule/application/WeeklyScheduleService.java` (el método de reemplazo hace borrar, confirmar y guardar sin control de concurrencia)
- `src/main/java/com/bookly/backend/cf/shared/error/GlobalExceptionHandler.java` (no maneja el error de bloqueo optimista, ver S3-12)
- `src/main/java/com/bookly/backend/cf/professional/infrastructure/persistence/ProfessionalRepository.java` (candidato a un bloqueo pesimista por profesional)

### Criterio de aceptación

- Dos reemplazos concurrentes del horario producen un 200 y un 409 legible, nunca un 401.
- El horario guardado corresponde exactamente a una de las dos peticiones, y el cliente puede saber cuál.
- Ninguna petición pierde su configuración de agenda en silencio.
- El error de bloqueo optimista se traduce a un código de negocio coherente con el resto de la API.

---

## S3-15 — La duración de la reserva no tiene que coincidir con la del servicio

**Title (Azure):** `[HU-08] Se puede crear una reserva cuya duración no coincide con la del servicio contratado`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-08, sprint-3

### Descripción

El servicio de creación de reservas valida el rango de tiempo, que sea futuro, que caiga dentro del horario del profesional, que no haya bloqueos y que no se solape con otra reserva. Pero no valida que la duración de la reserva sea la del servicio contratado. Es posible crear una reserva de 15 minutos de un servicio de 60 minutos.

Se descubrió en la caza de bugs nuevos, y no estaba incluido en el informe original de este sprint.

### ¿Por qué importa?

- El reporte de ocupación subestima lo realmente prestado: un servicio de 60 minutos vendido cuenta como 15 minutos de ocupación, y el administrador ve una agenda menos ocupada de lo que está.
- El cliente puede recortar una reserva de 60 minutos a 15 minutos y liberar el resto de la franja, que la disponibilidad volverá a ofrecer: la franja queda disponible para otra reserva aunque el servicio siga pendiente.
- Es una inconsistencia entre dos modelos: la disponibilidad ofrece huecos de duración fija igual a la del servicio, pero la reserva acepta cualquier rango.
- El profesional recibe una cita de 15 minutos por un servicio de 60, con el tiempo real de atención pactado sin reflejo en la agenda.

### Pre-requisitos

- Backend corriendo
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Un servicio de 60 minutos de duración

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Consultar la disponibilidad del profesional y anotar los huecos ofrecidos para un servicio de 60 minutos.
3. Crear una reserva en un hueco de la disponibilidad pero con una hora de fin 15 minutos después del inicio.
4. Observar que la reserva se crea correctamente y con estado confirmado.
5. Consultar el reporte de ocupación y anotar los minutos que esa reserva aporta al profesional.
6. Consultar de nuevo la disponibilidad y comprobar si el resto de la franja vuelve a ofrecerse.

### ¿Qué debería pasar?

El servidor debería rechazar una reserva cuya duración no coincida con la del servicio contratado, o al menos exigir que no sea menor. Si el negocio admite duraciones variables, entonces la disponibilidad debería ofrecer huecos de duración variable.

### ¿Qué pasa realmente?

La reserva de 15 minutos para un servicio de 60 se crea con éxito y devuelve estado confirmado. En el reporte de ocupación, el profesional acumula 15 minutos de ocupación por un servicio de 60 minutos vendido, con lo que la ocupación real queda subestimada frente a lo prestado. Y como la franja solo está ocupada 15 minutos, el resto vuelve a ofrecerse en la disponibilidad.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/booking/application/service/CreateBookingService.java` (las validaciones del rango de tiempo y del horario, donde falta la comprobación de la duración)
- `src/main/java/com/bookly/backend/cf/availability/domain/model/AvailabilityCalculator.java` (calcula los huecos con la duración fija del servicio)
- `src/main/java/com/bookly/backend/cf/catalog/domain/model/Service.java` (la duración del servicio contratado)

### Criterio de aceptación

- Una reserva con duración distinta de la del servicio se rechaza con un error de negocio claro.
- El reporte de ocupación y la disponibilidad usan la misma noción de duración.
- Está definido y documentado si el negocio admite duraciones variables, y el comportamiento implementado es el acordado.

---

## S3-16 — La reserva que cruza la medianoche se imputa entera a un solo día

**Title (Azure):** `[HU-15] Una reserva que cruza la medianoche se imputa completa al día en que empieza`

**Severity:** Low  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, ocupacion, datos, sprint-3

### Descripción

El cálculo de ocupación por día de la semana toma el día de la reserva a partir de la fecha de inicio, y le imputa toda la duración a ese único día, aunque la reserva termine al día siguiente.

La API lo impide: una reserva con inicio y fin en días distintos se rechaza con un conflicto de franja fuera del horario. Así que hoy solo es alcanzable por la vía directa a la base de datos.

### ¿Por qué importa?

- Una reserva de 23:00 a 01:00 cuenta 120 minutos íntegros el viernes, aunque la franja de 23:00 a 01:00 cruza al sábado, que puede tener capacidad cero.
- Refuerza el defecto S3-08: el caso de la reserva que cruza la medianoche es un caso más de minutos contados en un día que no puede tenerlos.
- Es deuda del mismo tipo que S3-03: la regla de imputación está implícita en el código y no documentada.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Acceso a la base de datos para observar una reserva que cruza la medianoche

### Pasos para reproducir

1. Consultar el reporte de ocupación del periodo mensual y anotar los minutos reservados por día de la semana.
2. Intentar crear por la API una reserva que empiece hoy a las 23:00 y termine mañana a la 01:00.
3. Observar que la creación se rechaza con un conflicto de franja fuera del horario.
4. Insertar directamente en la base de datos una reserva que cruce la medianoche.
5. Consultar de nuevo el reporte de ocupación y observar en qué día se imputan sus minutos.

### ¿Qué debería pasar?

El sistema debería repartir la duración de una reserva por los días que cruza, o al menos documentar de forma explícita que la imputación se hace por el día de inicio.

### ¿Qué pasa realmente?

La API rechaza la reserva que cruza la medianoche, de modo que el caso solo se alcanza insertando el registro por la vía directa a la base de datos. En ese caso, los 120 minutos de una reserva de 23:00 a 01:00 se imputan íntegros al día de inicio, y la capacidad del día que recibe el Crossing no existe.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java` (el cálculo de minutos reservados por día toma el día de la fecha de inicio)
- `src/main/java/com/bookly/backend/cf/booking/application/service/CreateBookingService.java` (la validación que exige que inicio y fin estén en el mismo día)
- `docs/HU-15-reporte-ocupacion.md` (documentar la regla de imputación)

### Criterio de aceptación

- La regla de imputación por día está documentada en la historia de usuario.
- Una reserva que cruza la medianoche reparte su duración entre los días que cruza, si la regla de negocio lo exige.

---

## Plan de remediación

### Sprint 3 (antes del 25/11/2026)

| # | Bug | Esfuerzo |
|---|-----|----------|
| 1 | S3-12 Excepciones no modeladas devuelven 401 falso | 2-4h |
| 2 | S3-02 Bloqueos fuera de la capacidad del reporte | 4-6h |
| 3 | S3-01 Concurrencia de bloqueo de agenda | 3-5h |
| 4 | S3-09 Contrato de error unificado | 3-5h |
| 5 | S3-14 Carrera al reemplazar el horario | 3-5h |
| 6 | S3-06 Paginación y auditoría del historial de cliente | 6-10h |

**Subtotal:** 21-35h

### Sprint 4

| # | Bug | Esfuerzo |
|---|-----|----------|
| 1 | S3-04 Porcentaje sin recorte silencioso | 3-5h |
| 2 | S3-05 Capacidad sin fusionar franjas solapadas | 6-10h |
| 3 | S3-08 Días con reservas y sin capacidad | 3-4h |
| 4 | S3-13 Paginación y auditoría del historial de profesional | 4-6h |
| 5 | S3-15 Duración de la reserva libre | 2-3h |
| 6 | S3-07 Disponibilidad sin fusionar franjas | 3-5h |
| 7 | S3-10 Vertical de reprogramación cortada | 2-3h |
| 8 | S3-03 Política única de estados ocupantes | 2-3h |
| 9 | S3-11 Ventana del reporte no arbitraria | 2-4h |
| 10 | S3-16 Imputación de la reserva que cruza la medianoche | 1-2h |

**Subtotal:** 28-45h

**Total:** 49-80 horas-persona

### Decisiones de negocio pendientes

Estas tres decisiones condicionan la implementación y no son defectos de código:

| # | Decisión | Bugs afectados |
|---|----------|-----------------|
| 1 | ¿Puede un profesional reducir o borrar su horario con reservas confirmadas vigentes? | S3-04, S3-08 |
| 2 | ¿Una reserva atendida o reprogramada ocupa agenda? | S3-03, S3-10 |
| 3 | ¿El reporte de ocupación debe admitir un rango arbitrario de fechas? | S3-11 |

---

## Cómo ejecutar las pruebas

```bash
cd qa-tests/sprint3
./run-all.sh
```

Logs detallados en `.e2e-results/summary/`.
