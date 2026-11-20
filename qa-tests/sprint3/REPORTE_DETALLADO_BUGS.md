# Reporte Detallado de Bugs - Sprint 3

**Proyecto:** Bookly BackendCF  
**Sprint:** 3 (cierre 25/11/2026)  
**Fecha:** 2026-11-19  
**Pruebas ejecutadas:** 174 (169 PASS, 5 FAIL)  
**Bugs críticos/altos:** 11

> Cada bug está en formato listo para crear como Work Item en Azure DevOps. Los pasos de reproducción están escritos en lenguaje simple, sin comandos técnicos.

---

## Resumen

| ID | Severidad | HU | Estado | Effort |
|----|-----------|-----|--------|--------|
| S3-01 | CRÍTICO | HU-05 | Confirmado | 4-8h |
| S3-02 | CRÍTICO | HU-15 | Confirmado | 4-6h |
| S3-03 | ALTO | HU-15 | Confirmado | 4-6h |
| S3-04 | ALTO | HU-15 | Confirmado | 2-4h |
| S3-05 | ALTO | HU-15 | Confirmado | 6-10h |
| S3-06 | MEDIO | HU-13 | Confirmado | 6-10h |
| S3-07 | MEDIO | HU-07 | Confirmado | 3-5h |
| S3-08 | MEDIO | HU-15 | Confirmado | 2-3h |
| S3-09 | MEDIO | Transversal | Probable | 3-5h |
| S3-10 | MEDIO | HU-08 | Probable | 2-3h |
| S3-11 | MEDIO | Transversal | Probable | 2-4h |

**Total estimado:** 42-67 horas-persona

---

## S3-01 — La concurrencia al bloquear la agenda devuelve 401 en vez de 409

**Title (Azure):** `[HU-05] Bloquear la misma franja a la vez devuelve 401 en vez de 409 AGENDA_BLOCK_OVERLAP`

**Severity:** Critical  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-05, concurrencia, atomicidad, sprint-3

### Descripción

Cuando dos solicitudes simultáneas intentan bloquear la misma franja horaria de un profesional, la que pierde la carrera no recibe el conflicto de negocio documentado. En lugar de una respuesta 409 con el código AGENDA_BLOCK_OVERLAP, recibe una respuesta 401 de usuario no autorizado, aunque su token sea perfectamente válido. Solo el camino secuencial produce la respuesta correcta.

### ¿Por qué importa?

Es el escenario central de la historia de bloqueo de agenda. Si el profesional recibe un 401:
- El profesional recibe un 401 y cree que su sesión expiró, cuando el problema era de concurrencia.
- No puede distinguir "mi token falló" de "esa franja ya está bloqueada".
- Pierde el detalle de las citas en conflicto que el camino secuencial sí entrega.
- Un cliente que integra la API no puede reaccionar de forma programada al rechazo.

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

La solicitud perdedora debería recibir código 409 con el código de error AGENDA_BLOCK_OVERLAP, igual que ocurre en el camino secuencial, donde el primer bloqueo responde 201 y el segundo 409.

### ¿Qué pasa realmente?

La solicitud perdedora recibe código 401 con el mensaje "Se requiere autenticación", pese a enviar un token válido. Con ocho solicitudes simultáneas sobre la misma franja se observaron seis respuestas 409, una 201 y una 401. El defecto se reprodujo en cinco de cinco rondas. En cambio, el camino secuencial sí responde correctamente: 201 y luego 409 AGENDA_BLOCK_OVERLAP.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/schedule/application/AgendaBlockService.java:63-69` (bloque try/catch que guarda el bloqueo y consulta el solapamiento)
- `src/main/java/com/bookly/backend/cf/shared/error/GlobalExceptionHandler.java` (no maneja la excepción de sistema de persistencia)
- `src/main/java/com/bookly/backend/cf/booking/application/service/CreateBookingService.java:128` (referencia: sí traduce el conflicto de concurrencia a respuesta de negocio)

### Criterio de aceptación

- Dos solicitudes simultáneas sobre la misma franja: una recibe 201 y la otra 409 con el código AGENDA_BLOCK_OVERLAP.
- Ninguna solicitud devuelve 401 ni 500 bajo concurrencia.
- El detalle del bloqueo en conflicto se entrega igual que en el camino secuencial.
- El caso de concurrencia pasa de forma estable en varias rondas consecutivas.

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

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con horario semanal configurado
- Un rango futuro dentro de la ventana del reporte

### Pasos para reproducir

1. Iniciar sesión como administrador.
2. Consultar el reporte de ocupación del periodo mensual y anotar los minutos de capacidad del profesional y su desglose por día de la semana.
3. Crear un bloqueo de agenda de 120 minutos dentro de la ventana del reporte (por ejemplo, un lunes de 9:00 a 11:00).
4. Consultar de nuevo el reporte de ocupación del periodo mensual.
5. Comparar los minutos de capacidad antes y después.
6. Consultar la disponibilidad del mismo profesional para ese mismo día.

### ¿Qué debería pasar?

Los minutos de capacidad deberían bajar en 120, tanto en el desglose por día de la semana como en el total por profesional, de la misma forma en que la disponibilidad deja de ofrecer esas horas.

### ¿Qué pasa realmente?

La diferencia de capacidad es cero. Los minutos de capacidad del profesional siguen en 3960 antes y después del bloqueo, igual que el desglose del lunes. Los 120 minutos bloqueados no aparecen ni en el desglose por día ni en el total por profesional. En cambio, la disponibilidad sí deja de ofrecer esas horas, lo que confirma que la contradicción está en el reporte y no en el bloqueo.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/application/OccupancyQuery.java:15-24` (agregar un método que exponga los bloqueos vigentes en la ventana)
- `src/main/java/com/bookly/backend/cf/report/infrastructure/persistence/OccupancyQueryAdapter.java:25-42` (el adaptador solo consulta reservas; falta inyectar el repositorio de bloqueos)
- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:20-30` y `:33-43` (restar los minutos bloqueados de la capacidad)

### Criterio de aceptación

- Un bloqueo de N minutos reduce en N los minutos de capacidad del profesional, del día y del total.
- La capacidad del reporte y la disponibilidad coinciden para el mismo profesional en el mismo periodo.
- El desglose por día de la semana refleja los bloqueos de ese día.
- El caso de integración que cruza bloqueo y reporte pasa de forma permanente.

---

## S3-03 — Dos definiciones incompatibles de "ocupado"

**Title (Azure):** `[HU-15] La disponibilidad y el reporte no coinciden en qué reservas ocupan agenda`

**Severity:** High  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, hu-07, ocupacion, sprint-3

### Descripción

La disponibilidad y el reporte de ocupación no coinciden en qué reservas cuentan como "ocupado". La disponibilidad solo considera las reservas confirmadas. El reporte considera tanto las confirmadas como las ya atendidas. El efecto práctico es que una franja que el reporte da por ocupada se sigue ofreciendo como reservable.

### ¿Por qué importa?

- Un cliente puede reservar una franja que el reporte ya considera ocupada, generando doble conteo.
- Cualquier decisión de negocio tomada con el reporte (contratación, turnos, precios) opera con una ventana que no coincide con la ventana de lo que el cliente puede ver y reservar.
- La causa es de fondo: una reserva ya atendida es una atención que ocurrió. O la disponibilidad la considera ocupada, o el reporte no debería contarla.

### Pre-requisitos

- Backend corriendo
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Un servicio de 30 minutos de duración

### Pasos para reproducir

1. Crear una reserva confirmada de 60 minutos en un día con horario.
2. Consultar la disponibilidad del profesional y anotar las horas ofrecidas: son 4 franjas (10:00, 10:30, 11:00 y 11:30).
3. Cambiar el estado de esa misma reserva a atendida directamente en la base de datos.
4. Consultar de nuevo la disponibilidad: ahora se ofrecen 6 franjas (09:00, 09:30, 10:00, 10:30, 11:00 y 11:30).
5. Consultar el reporte de ocupación y verificar que los minutos reservados sí crecen en 60 al pasar a atendida.

### ¿Qué debería pasar?

La disponibilidad y el reporte deberían aplicar el mismo criterio de ocupación. Si una reserva atendida ocupa agenda, la franja de las 9:00 no debería volver a aparecer en la disponibilidad. Si no ocupa agenda, el reporte no debería contarla.

### ¿Qué pasa realmente?

La franja ocupada de las 9:00 vuelve a aparecer en el listado de disponibilidad: los huecos ofrecidos pasan de 4 a 6. En paralelo, el reporte de ocupación sí suma esos 60 minutos. Los dos módulos dan respuestas incompatibles sobre la misma franja y el mismo instante.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/booking/infrastructure/persistence/BookingRepository.java:28-35` (consulta usada por la disponibilidad: filtra solo por estado confirmado)
- `src/main/java/com/bookly/backend/cf/report/infrastructure/persistence/OccupancyQueryAdapter.java:25-26` (conjunto de estados que el reporte considera ocupantes)

### Casos adicionales que también fallan

- Una reserva atendida libera la franja para el cliente y, al mismo tiempo, el reporte la sigue contando como ocupación.
- El mismo criterio de ocupación está duplicado en el código sin una fuente común, por lo que cualquier estado nuevo reproduce la contradicción.

### Criterio de aceptación

- La disponibilidad y el reporte aplican el mismo criterio de ocupación, definido en un único lugar.
- Una reserva atendida o confirmada bloquea la franja en los dos módulos.
- Un caso de integración verifica que el número de huecos ofrecidos es coherente con los minutos reportados.

---

## S3-04 — El porcentaje de ocupación se recorta en silencio a cien

**Title (Azure):** `[HU-15] El reporte de ocupación recorta el porcentaje a 100 sin avisar`

**Severity:** High  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, ocupacion, exactitud, sprint-3

### Descripción

Cuando los minutos reservados superan los minutos de capacidad, el reporte devuelve el porcentaje 100.0 sin ninguna advertencia. El valor real era 253.85 y el consumidor nunca puede saber que el dato fue truncado. La respuesta no incluye ningún campo que indique la saturación.

### ¿Por qué importa?

- Un administrador ve "100% de ocupación" y no puede distinguir "agenda al límite" de "dato inflado por una capacidad mal calculada".
- El recorte enmascara el defecto de capacidad inflada: sin el truncamiento, el 253.85 habría apuntado directamente a la causa raíz.
- Si el valor se usa como indicador para fijar tarifas o turnos, la plataforma decide sobre un número que no refleja lo ocurrido.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con dos franjas que se solapan el mismo día
- Reservas confirmadas suficientes para superar la capacidad de ese profesional

### Pasos para reproducir

1. Crear un profesional con dos franjas que se solapan el mismo día.
2. Registrar 80 reservas confirmadas para ese profesional.
3. Consultar el reporte de ocupación del periodo mensual.
4. Anotar los minutos de capacidad, los minutos reservados y el porcentaje de ocupación devueltos.

### ¿Qué debería pasar?

El servidor debería devolver el porcentaje real de ocupación o, si el contrato obliga a un tope, debería incluir una marca explícita que indique que el valor fue saturado y el valor sin recortar.

### ¿Qué pasa realmente?

El servidor devuelve 4800 minutos reservados contra 4680 minutos de capacidad y un porcentaje de ocupación de 100.0. El valor real era 253.85 y no hay ningún aviso, ni en la respuesta ni en los detalles del reporte.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:63-70` (el tope se aplica de forma incondicional)
- DTO de respuesta del reporte de ocupación (agregar una marca de saturación y el valor sin recortar)

### Criterio de aceptación

- El porcentaje devuelto coincide con el cálculo real, o bien la respuesta indica de forma explícita que fue saturado.
- Cuando los minutos reservados superan a la capacidad, queda una señal visible en la respuesta o en el registro de auditoría.
- La documentación de la API describe el comportamiento del valor saturado.

---

## S3-05 — La capacidad se infla cuando el horario tiene franjas solapadas

**Title (Azure):** `[HU-15] La capacidad reportada se infla cuando el horario tiene franjas solapadas`

**Severity:** High  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-15, hu-04, capacidad, sprint-3

### Descripción

Cuando un profesional tiene dos franjas que se solapan el mismo día, el reporte las suma como minutos independientes en lugar de fusionarlas. La capacidad resultante es mayor que el tiempo real en que el profesional está disponible. En el caso medido, dos franjas que cubren 300 minutos reales se reportan como 360.

### ¿Por qué importa?

- La capacidad se infla de forma proporcional al grado de solapamiento: en el caso medido, un 20%.
- Como la capacidad es el denominador del porcentaje, todos los porcentajes de ocupación del sprint están subestimados.
- El defecto es bidireccional: es la causa que permite que los minutos reservados superen a la capacidad (defecto S3-04) y distorsiona el mismo cálculo que alimenta la decisión de disponibilidad (defecto S3-07).
- El mismo profesional, con el mismo horario, reporta capacidades distintas según cómo se haya escrito su agenda a lo largo del tiempo.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un profesional con dos franjas solapadas el mismo día (por ejemplo, de 9:00 a 12:00 y de 11:00 a 14:00)
- La base de datos admite las dos franjas: la restricción solo exige que la hora final sea posterior a la inicial

### Pasos para reproducir

1. Configurar para un profesional dos franjas el mismo día: de 9:00 a 12:00 y de 11:00 a 14:00.
2. Verificar que el sistema guarda las dos franjas sin rechazar el solapamiento.
3. Consultar el reporte de ocupación del periodo mensual.
4. Anotar los minutos de capacidad del día lunes para ese profesional.
5. Calcular a mano la cobertura real: las dos franjas fusionadas cubren 300 minutos.

### ¿Qué debería pasar?

El servidor debería reportar 300 minutos de capacidad para ese lunes, que es el tiempo real en que el profesional está disponible, fusionando las franjas solapadas antes de sumarlas.

### ¿Qué pasa realmente?

El servidor reporta 360 minutos, es decir 3 horas más 3 horas en vez de 5 horas. La capacidad del lunes queda inflada en un 20% respecto de la cobertura real. Además, el sistema permite guardar las franjas solapadas: la base de datos no lo impide y la validación del servicio no lo rechaza en este camino.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:20-30` y `:33-43` (las franjas se suman sin fusionar los intervalos solapados)
- Migración que crea el horario semanal (reforzar la restricción para impedir franjas solapadas)

### Casos adicionales que también fallan

- La capacidad por día de la semana y la capacidad por profesional calculan de forma independiente, por lo que pueden divergir.
- El cálculo de disponibilidad arrastra la misma falta de fusión y produce el síntoma espejo en el módulo de disponibilidad (defecto S3-07).

### Criterio de aceptación

- Dos franjas solapadas del mismo día reportan la cobertura real de la unión de los intervalos.
- El cálculo por día de la semana y el cálculo por profesional devuelven la misma suma.
- La base de datos o el servicio impiden guardar franjas parcialmente solapadas.

---

## S3-06 — La ruta antigua de historial no pagina ni deja rastro en auditoría

**Title (Azure):** `[HU-13] La ruta de historial por identificador de cliente no pagina ni audita la lectura`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-13, auditoria, paginacion, sprint-3

### Descripción

Conviven dos rutas para consultar el historial de reservas de un cliente, con contratos incompatibles. La ruta nueva restringe el acceso al rol de cliente y pagina sus resultados. La ruta antigua permite que un administrador lea el historial completo de cualquier cliente, sin paginar y sin dejar registro de auditoría de la lectura. La ruta antigua no deja ninguna traza de quién consultó los datos personales de un tercero.

### ¿Por qué importa?

- Riesgo de denegación de servicio: un cliente con historial largo genera respuestas sin límite, con consumo de memoria del servidor y del cliente.
- Sin auditoría no hay forma de demostrar quién consultó los datos personales de un tercero, lo que incumple la trazabilidad que el propio sistema exige en otros módulos.
- Inconsistencia visible: la misma operación de negocio responde 403 por una ruta y 200 por la otra, con el mismo rol y el mismo recurso.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un cliente con historial largo (en la prueba, 111 reservas)

### Pasos para reproducir

1. Iniciar sesión como administrador.
2. Consultar el historial de reservas del cliente usando la ruta que incluye el identificador del cliente.
3. Observar que la respuesta llega completa, sin paginación, con un tamaño cercano a 60 KB.
4. Revisar el registro de auditoría del sistema en busca de una entrada que registre esa lectura.
5. Cerrar sesión, iniciar sesión como el propio cliente y consultar la ruta nueva de historial.
6. Consultar la ruta nueva de historial con el rol de administrador.

### ¿Qué debería pasar?

La ruta debería aplicar paginación como la ruta nueva y dejar una entrada de auditoría con el administrador que consultó, el cliente consultado, la dirección de origen y la fecha. El criterio de rol debería ser el mismo en las dos rutas.

### ¿Qué pasa realmente?

El administrador recibe las 111 reservas en una sola respuesta de 60 KB sin paginar y no queda ningún registro de la lectura. La ruta nueva sí pagina, pero restringe el acceso al rol de cliente: el administrador recibe 403 y, por lo tanto, no puede ni siquiera comparar el comportamiento de las dos rutas con el mismo rol.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/booking/presentation/controller/BookingController.java:67-71` (la ruta antigua devuelve la lista completa sin paginación)
- `src/main/java/com/bookly/backend/cf/shared/security/OwnershipGuard.java:23-26` (el acceso concedido por administrador no deja rastro en auditoría)

### Casos adicionales que también fallan

- La ruta antigua no filtra por estado de reserva, a diferencia de la ruta nueva.
- Dos rutas con reglas de rol opuestas para la misma operación de negocio.

### Criterio de aceptación

- La ruta antigua devuelve el historial paginado, con el mismo formato de página que la ruta nueva.
- Toda lectura del historial de un cliente por parte de un administrador queda registrada en la auditoría.
- El criterio de rol es idéntico en las dos rutas y está documentado.
- La ruta antigua queda marcada como obsoleta o se elimina en la versión mayor de la API.

---

## S3-07 — La disponibilidad devuelve horas repetidas cuando el horario se solapa

**Title (Azure):** `[HU-07] La disponibilidad ofrece la misma hora repetida cuando el horario se solapa`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-07, disponibilidad, sprint-3

### Descripción

El cálculo de disponibilidad genera las franjas hora por hora, sin fusionar las franjas del horario que se solapan ni eliminar las horas repetidas del resultado. El cliente recibe la misma hora de inicio más de una vez.

### ¿Por qué importa?

- El cliente ve la misma hora ofrecida más de una vez, lo que se percibe como un error de la aplicación.
- El número de huecos libres que se le muestra no corresponde con los huecos reales, por lo que un contador de "horarios disponibles" no es fiable.
- Un cliente que pulse dos veces sobre la misma hora puede disparar dos intentos de reserva sobre el mismo rango.
- Es el síntoma espejo del defecto S3-05: allí la falta de fusión infla la capacidad, aquí duplica la oferta.

### Pre-requisitos

- Backend corriendo
- Un profesional con dos franjas que se solapan el mismo día (de 9:00 a 12:00 y de 11:00 a 14:00)
- Un servicio de 60 minutos de duración

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Consultar la disponibilidad del profesional para el día con las dos franjas solapadas.
3. Anotar todas las horas de inicio devueltas.
4. Contar cuántas horas se repiten.

### ¿Qué debería pasar?

El servidor debería devolver cada hora disponible una sola vez: nueve horas únicas para el servicio de 60 minutos sobre una cobertura real de 9:00 a 14:00.

### ¿Qué pasa realmente?

El servidor devuelve seis franjas de las que solo cinco son únicas: la hora 11:00 aparece dos veces. El listado completo es 9:00, 10:00, 11:00, 11:00, 12:00 y 13:00. El cliente ve la misma hora ofrecida más de una vez.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/availability/domain/model/AvailabilityCalculator.java:31-42` (las franjas del día se recorren una por una sin fusionarlas)

### Criterio de aceptación

- Cada hora de inicio aparece una sola vez en la respuesta de disponibilidad.
- Las franjas solapadas del día se fusionan antes de generar los huecos.
- El número de huecos ofrecidos coincide con la cobertura real del horario.

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
- El porcentaje global también queda sesgado a la baja, porque suma esos minutos reservados al numerador pero su capacidad es cero.
- El consumidor no puede distinguir un día sin operación de un día con una inconsistencia de agenda que permitió reservar fuera de horario.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un día sin horario semanal configurado, por ejemplo un sábado o un domingo
- Un cliente registrado con su acceso configurado

### Pasos para reproducir

1. Identificar un día de fin de semana sin horario semanal configurado.
2. Consultar el reporte de ocupación del periodo mensual y anotar los minutos reservados y el porcentaje de ese día.
3. Crear una reserva confirmada de 60 minutos en ese mismo día.
4. Consultar de nuevo el reporte de ocupación.

### ¿Qué debería pasar?

El servidor debería marcar el día como no calculable, o devolver un valor nulo o un estado explícito, en lugar de presentar un 0% como si fuera un dato de ocupación válido.

### ¿Qué pasa realmente?

Los minutos reservados del sábado crecen de 810 a 870 y el porcentaje de ocupación se mantiene en 0.0. El día se muestra con actividad y con ocupación nula al mismo tiempo. En el acumulado de la prueba, 1590 minutos reservados entre sábado y domingo quedan reportados como 0%.

### Archivos a modificar

- `src/main/java/com/bookly/backend/cf/report/domain/model/OccupancyCalculator.java:65-67` (la guarda de capacidad cero devuelve 0.0 en lugar de un valor no calculable)

### Casos adicionales que también fallan

- El porcentaje global de ocupación queda sesgado a la baja por la misma razón.
- El reporte no distingue el caso "no hay capacidad porque no se trabaja ese día" del caso "no hay datos".

### Criterio de aceptación

- Un día con minutos reservados y sin capacidad no se reporta como 0% de ocupación.
- La documentación de la API indica que ese valor puede ser nulo y qué significa.
- El porcentaje global no incorpora minutos reservados de días sin capacidad.

---

## S3-09 — La API tiene dos contratos de error distintos

**Title (Azure):** `[Transversal] La API devuelve dos formatos de error incompatibles`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** transversal, api, contrato, sprint-3

### Descripción

La plataforma expone dos formatos de respuesta de error distintos. El más extendido es el que define el contrato publicado de la API, con el campo errorCode junto con message, details, traceId y timestamp. Pero algunos endpoints devuelven una respuesta reducida, con el campo error y sin details, traceId ni timestamp. El contrato publicado describe únicamente el primer formato.

### ¿Por qué importa?

- El cliente debe implementar dos lecturas distintas del mismo concepto, y un cliente que siga fielmente la documentación no encuentra el campo errorCode en las respuestas de error de la creación de reservas.
- La ausencia de traceId impide correlacionar un error reportado por el usuario con la traza del servidor, lo que degrada el soporte.
- La ausencia de details impide entregar información estructurada de contexto, como el detalle de las citas afectadas que el propio módulo de bloqueo de agenda sí provee.

### Pre-requisitos

- Backend corriendo
- Un cliente registrado con su acceso configurado
- Una franja horaria ya ocupada, para provocar un conflicto de reserva

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Provocar un conflicto al crear una reserva en una franja ya ocupada.
3. Observar los campos que devuelve la respuesta de error.
4. Provocar un error en cualquier otro módulo, por ejemplo al crear un bloqueo de agenda que se solape con otro bloqueo.
5. Comparar los campos de las dos respuestas de error.

### ¿Qué debería pasar?

Todas las respuestas de error de la API deberían tener la misma forma, con errorCode, message, details, traceId y timestamp, tal como define el contrato publicado.

### ¿Qué pasa realmente?

Los conflictos de reserva devuelven error y message. El resto de la API devuelve errorCode, message, details, traceId y timestamp. El contrato publicado define errorCode, por lo que el cliente que sigue la documentación no puede leer el campo error de esos dos errores de reserva. En particular, los conflictos de reserva no llevan identificador de traza, con lo que se pierde la trazabilidad justo en los casos de conflicto, que son los más frecuentes de reportar.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/booking/presentation/controller/BookingController.java:132` (definición local de la respuesta de error reducida, con el campo error)
- Manejador global de excepciones (debe construir la respuesta estándar para los conflictos de reserva)
- Contrato público de la API (definición del esquema de error)

### Criterio de aceptación

- Todas las respuestas de error de la API contienen errorCode, message, details, traceId y timestamp.
- El contrato público y la implementación coinciden.
- Una prueba de contrato verifica la forma de todas las respuestas de error.

---

## S3-10 — Una reserva reprogramada puede solaparse con una confirmada

**Title (Azure):** `[HU-08] Una reserva reprogramada puede ocupar la misma franja que una confirmada`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** hu-08, hu-07, hu-15, sprint-3

### Descripción

El sistema reconoce un estado de reserva "reprogramada", pero ni la base de datos ni ninguno de los dos módulos que consultan la ocupación de la agenda lo consideran. Una reserva reprogramada queda en un limbo: no bloquea la agenda, no aparece en la disponibilidad y no aparece en el reporte.

### ¿Por qué importa?

- Dos reservas pueden ocupar físicamente la misma franja: una confirmada y una reprogramada. El profesional recibe dos citas para el mismo horario.
- La reserva reprogramada es invisible en la disponibilidad, de modo que el cliente puede reservar sobre una franja que en realidad ya tiene una cita reprogramada.
- La reserva reprogramada es invisible en el reporte, por lo que las horas efectivamente comprometidas no se computan y la ocupación real se subestima.
- Es el mismo defecto conceptual de S3-03, pero aplicado a un tercer estado.

### Pre-requisitos

- Backend corriendo
- Un profesional con horario semanal configurado
- Un cliente registrado con su acceso configurado
- Acceso a la base de datos para observar el estado de la reserva

### Pasos para reproducir

1. Crear una reserva confirmada en una franja concreta.
2. Cambiar el estado de esa reserva a reprogramada directamente en la base de datos.
3. Crear una segunda reserva confirmada en esa misma franja.
4. Consultar la disponibilidad de esa franja: aparece como libre.
5. Consultar el reporte de ocupación: esa franja no computa como ocupada.

### ¿Qué debería pasar?

El sistema debería impedir que una reserva reprogramada y una confirmada ocupen la misma franja, y la reserva reprogramada debería ser visible tanto en la disponibilidad como en el reporte, o quedar excluida de forma explícita de los dos.

### ¿Qué pasa realmente?

La base de datos lo permite: la restricción de exclusión solo cubre las reservas confirmadas, así que una reserva reprogramada y una confirmada pueden ocupar la misma franja. Además, la reserva reprogramada es invisible en la disponibilidad y en el reporte de ocupación.

### Archivos a investigar

- Migración que evita reservas confirmadas solapadas (el predicado de la restricción solo contempla el estado confirmado)
- `src/main/java/com/bookly/backend/cf/booking/infrastructure/persistence/BookingRepository.java:28-35` (consulta de solapamientos usada por la disponibilidad)
- `src/main/java/com/bookly/backend/cf/report/infrastructure/persistence/OccupancyQueryAdapter.java:25-26` (conjunto de estados que el reporte considera ocupantes)

### Casos adicionales que también fallan

- Ningún endpoint del alcance actual produce una reserva reprogramada, por lo que el estado no está cubierto por pruebas.
- El estado reprogramada sí genera notificaciones, lo que confirma que existe en el dominio pero no está integrado en la agenda.

### Criterio de aceptación

- Está definido y documentado si la reserva reprogramada ocupa agenda.
- La base de datos impide el solapamiento entre los estados que ocupan agenda.
- La disponibilidad y el reporte incluyen la reserva reprogramada según la definición acordada.

---

## S3-11 — La disponibilidad y el reporte nunca observan el mismo periodo

**Title (Azure):** `[Transversal] La disponibilidad y el reporte de ocupación nunca observan el mismo periodo`

**Severity:** Medium  
**Area Path:** CodeFactory EAV10  
**Tags:** transversal, hu-07, hu-15, sprint-3

### Descripción

Los dos módulos nunca observan el mismo rango temporal. La disponibilidad rechaza cualquier consulta cuyo rango termine en el pasado, y el reporte construye siempre una ventana que termina hoy y empieza 29 días atrás. Por lo tanto no existe ninguna combinación de fechas en la que las dos vistas puedan contrastarse.

### ¿Por qué importa?

- No es posible construir un caso de integración que compare "lo que el cliente ve disponible" con "lo que el administrador ve ocupado" para el mismo periodo.
- Los defectos S3-02 y S3-03, que son contradicciones entre los dos módulos, solo pudieron detectarse de forma indirecta, no con una consulta comparativa directa.
- El reporte no permite auditar un periodo histórico concreto, lo que limita su uso analítico.

### Pre-requisitos

- Backend corriendo
- Un administrador con su acceso configurado
- Un cliente registrado con su acceso configurado

### Pasos para reproducir

1. Iniciar sesión como cliente.
2. Consultar la disponibilidad pidiendo un rango que va de ayer a hoy.
3. Observar la respuesta: el servidor rechaza el rango porque termina en el pasado.
4. Iniciar sesión como administrador.
5. Consultar el reporte de ocupación del periodo mensual.
6. Intentar consultar el reporte pidiendo un rango anterior al mes en curso.

### ¿Qué debería pasar?

Debería existir una combinación de fechas en la que las dos consultas se puedan contrastar, de modo que el sistema permita verificar que la disponibilidad y el reporte describen la misma agenda.

### ¿Qué pasa realmente?

La disponibilidad rechaza con código 400 el rango que termina en el pasado, con el mensaje de que el rango de fechas no puede estar en el pasado. El reporte, por su parte, usa siempre una ventana fija de los últimos treinta días y no admite fechas de inicio y fin. Los dos módulos nunca pueden observarse sobre el mismo periodo.

### Archivos a investigar

- `src/main/java/com/bookly/backend/cf/availability/application/AvailabilityService.java:100-104` (rechazo de cualquier rango que termine en el pasado)
- `src/main/java/com/bookly/backend/cf/report/application/OccupancyReportService.java:32-33` (ventana fija de treinta días calculada contra el reloj del servidor)

### Criterio de aceptación

- El reporte acepta fechas de inicio y fin opcionales, con la ventana actual como valor por defecto.
- Existe al menos un rango temporal consultable en los dos módulos.
- Un caso de integración compara, para el mismo rango, los minutos bloqueados en la disponibilidad contra los minutos de capacidad del reporte.

---

## Plan de remediación

### Sprint 3 (antes del 25/11/2026)

| # | Bug | Esfuerzo |
|---|-----|----------|
| 1 | S3-01 Concurrencia de bloqueo de agenda | 4-8h |
| 2 | S3-02 Bloqueos fuera de la capacidad del reporte | 4-6h |
| 3 | S3-03 Definición única de "ocupado" | 4-6h |
| 4 | S3-06 Paginación y auditoría del historial | 6-10h |
| 5 | S3-09 Contrato de error unificado | 3-5h |

**Subtotal:** 21-35h

### Sprint 4

| # | Bug | Esfuerzo |
|---|-----|----------|
| 1 | S3-05 Capacidad sin fusionar franjas solapadas | 6-10h |
| 2 | S3-07 Disponibilidad con horas repetidas | 3-5h |
| 3 | S3-04 Porcentaje sin recorte silencioso | 2-4h |
| 4 | S3-08 Días con reservas y sin capacidad | 2-3h |
| 5 | S3-10 Reserva reprogramada en la agenda | 2-3h |
| 6 | S3-11 Ventana temporal compartida | 2-4h |

**Subtotal:** 17-29h

**Total:** 42-67 horas-persona

---

## Cómo ejecutar las pruebas

```bash
cd qa-tests/sprint3
./run-all.sh
```

Logs detallados en `.e2e-results/summary/`.
