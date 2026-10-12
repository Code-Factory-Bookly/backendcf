# ADR-011: Notificaciones de reserva mediante eventos

## Estado

Implementada en HU-10; pendiente de validación del contrato de reprogramación con el responsable
del módulo `booking`.

## Fecha

2026-10-10

## Contexto

HU-10 debe enviar correos cuando una reserva se crea, se reprograma o se cancela.
La reserva es responsabilidad del módulo `booking`, mientras que el envío y el registro técnico
de los correos pertenecen al módulo `notification`.

El envío no debe acoplar la lógica de reservas a un proveedor SMTP ni hacer que una falla del
proveedor revierta una operación de negocio confirmada. Además, un mismo evento no debe producir
dos correos por reintentos o reprocesamientos.

## Decisión propuesta

Se utilizará comunicación basada en eventos dentro del monolito modular existente:

1. `booking` publicará `BookingCreated`, `BookingRescheduled` y `BookingCancelled`.
2. `notification` consumirá esos eventos mediante un listener de aplicación.
3. El consumo se ejecutará después del commit de la transacción de reserva.
4. El correo se enviará a través de `EmailNotificationPort`; SMTP o cualquier proveedor externo
   quedará aislado en un adaptador de infraestructura.
5. Cada evento tendrá un `eventId` único y se registrará en `notification_delivery` antes del envío.
6. La restricción única sobre `event_id` garantizará idempotencia.
7. Los errores temporales podrán reintentarse y los errores permanentes quedarán registrados como
   `FAILED`, sin exponer secretos en la base de datos ni en los logs.

Para el alcance actual se propone usar eventos de Spring con un listener en fase
`AFTER_COMMIT`. Si el equipo determina que se requiere durabilidad frente a la caída del proceso
entre el commit y la ejecución del listener, se evolucionará a una tabla outbox mediante un ADR o
una modificación de esta decisión.

## Alternativas consideradas

### Envío directo desde `booking`

Se descarta porque mezcla reglas de reserva con integración externa, aumenta el acoplamiento y
puede hacer fallar la reserva por un problema del correo.

### Llamada REST síncrona a un módulo de notificaciones

Se descarta porque el sistema actual es un monolito modular, no requiere despliegues independientes
y agregaría complejidad de red sin una necesidad demostrada.

### Tabla outbox desde el inicio

Es una alternativa válida para garantizar durabilidad y reintentos, pero requiere un flujo de
procesamiento adicional. Se mantiene como evolución si el análisis de operación o las pruebas del
Sprint 2 demuestran que `AFTER_COMMIT` no es suficiente.

## Consecuencias

### Positivas

- `booking` no conoce el proveedor de correo.
- Una falla del correo no revierte una reserva confirmada.
- La tabla de entregas permite consultar estados, errores y reintentos.
- La restricción única evita duplicados bajo reprocesamiento.
- El adaptador de prueba permite validar HU-10 sin depender de un SMTP externo.

### Negativas

- El envío es eventual y no necesariamente termina antes de responder la operación de reserva.
- Se necesita una transacción independiente para registrar el resultado después del commit.
- `AFTER_COMMIT` puede perder el evento si el proceso termina antes de ejecutarlo; este riesgo queda
  documentado para evaluar una futura outbox.

## Requisitos de seguridad y datos

- El destinatario debe normalizarse y validarse antes de persistirlo.
- No se almacenarán contraseñas, tokens ni credenciales del proveedor.
- El mensaje de error debe excluir datos sensibles.
- El proveedor y sus credenciales se configurarán mediante variables de entorno o secretos.
- El módulo conservará únicamente los datos necesarios para entregar y auditar la notificación.

## Dependencias y pendientes

- Confirmar con Miguel los nombres y campos definitivos de los eventos.
- Publicar `BookingRescheduledEvent` desde `booking` cuando exista la operación de reprogramación.
- Definir la política de retención de registros de correo.

## Trazabilidad

- HU: HU-10.
- Dependencias: HU-08 y HU-09.
- Persistencia: `notification_delivery`.
- Documento relacionado: [Contrato y diseño de HU-10](HU-10-notificaciones.md).
