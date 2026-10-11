# HU-10: contrato de eventos para notificaciones de reserva

## Estado

Propuesta inicial para validación con el responsable de `booking` (HU-08/HU-09).

## Propósito

El módulo `notification` reacciona a los cambios confirmados de una reserva y registra/envía
el correo correspondiente al cliente. La notificación no debe decidir si una reserva es válida;
esa responsabilidad pertenece al módulo `booking`.

## Eventos del contrato

El módulo `booking` publicará los siguientes eventos:

| Evento | Momento de publicación | Notificación esperada |
|---|---|---|
| `BookingCreated` | Después de crear y confirmar una reserva | Confirmación de reserva |
| `BookingRescheduled` | Después de confirmar una reprogramación | Nueva fecha y hora de la reserva |
| `BookingCancelled` | Después de confirmar una cancelación | Cancelación y liberación del cupo |

Los eventos deben procesarse después del commit de la transacción que modifica la reserva.
Un fallo del correo no debe revertir la creación, reprogramación o cancelación de la reserva.

## Envolvente común

Cada evento debe exponer, como mínimo:

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `eventId` | UUID | Sí | Identificador único del evento para idempotencia |
| `eventType` | texto | Sí | Tipo concreto del evento |
| `bookingId` | UUID | Sí | Reserva afectada |
| `occurredAt` | fecha-hora | Sí | Momento en que ocurrió el cambio |
| `customerId` | UUID | Sí | Cliente destinatario |
| `professionalId` | UUID | Sí | Profesional asociado |
| `serviceId` | UUID | Sí | Servicio asociado |

## Datos de la reserva

Los eventos de creación y reprogramación deben incluir `startTime` y `endTime`.
El evento de cancelación debe incluir también `cancelledAt` y, si existe, `cancellationReason`.

Para construir el correo, el consumidor necesita además el correo y nombre del cliente,
el nombre del servicio y el nombre del profesional. La decisión pendiente es si estos datos:

1. viajan como una instantánea en el evento; o
2. se consultan mediante puertos de lectura al procesar la notificación.

Se recomienda transportar el correo del cliente y los nombres mostrados en el mensaje como
instantánea, para que el contenido corresponda al estado de la reserva cuando ocurrió el evento
y para evitar que `notification` dependa directamente de las entidades JPA de otros módulos.

## Reglas del consumidor `notification`

- Solo procesa eventos con una reserva confirmada en la transacción de origen.
- Registra cada intento de envío antes de llamar al proveedor externo.
- No envía dos veces el mismo `eventId`.
- Un error temporal deja el registro en estado reintentable.
- Un error permanente deja el registro en estado `FAILED` y conserva el motivo técnico sin guardar
  secretos.
- El envío se realiza mediante un puerto de aplicación; SMTP o el proveedor externo queda aislado
  en infraestructura.

## Diseño de persistencia de notificaciones

### Tabla `notification_delivery`

La tabla registra cada notificación asociada a un evento de reserva. La reserva conserva el estado
de negocio; esta tabla conserva el estado técnico del intento de entrega del correo.

| Columna | Tipo propuesto | Nulable | Regla |
|---|---|---:|---|
| `id` | UUID | No | Clave primaria |
| `event_id` | UUID | No | Identificador único del evento procesado |
| `booking_id` | UUID | No | FK a `bookings(id)` |
| `event_type` | VARCHAR(40) | No | `BOOKING_CREATED`, `BOOKING_RESCHEDULED` o `BOOKING_CANCELLED` |
| `recipient_email` | VARCHAR(320) | No | Destinatario normalizado |
| `subject` | VARCHAR(200) | No | Asunto utilizado para el envío |
| `status` | VARCHAR(20) | No | `PENDING`, `SENT` o `FAILED` |
| `attempts` | INTEGER | No | Inicia en cero; no puede ser negativo |
| `created_at` | TIMESTAMPTZ | No | Fecha de creación del registro |
| `sent_at` | TIMESTAMPTZ | Sí | Fecha del envío exitoso |
| `last_attempt_at` | TIMESTAMPTZ | Sí | Fecha del último intento |
| `error_message` | TEXT | Sí | Error técnico resumido, sin secretos |

### Restricciones

- `id` es la clave primaria.
- `booking_id` referencia `bookings(id)` y no utiliza `ON DELETE CASCADE`.
- `event_id` es único para garantizar idempotencia y evitar duplicar un correo por evento.
- `event_type` solo admite los tres tipos definidos por el contrato.
- `status` solo admite `PENDING`, `SENT` y `FAILED`.
- `attempts >= 0`.
- `sent_at` debe estar informado cuando `status = 'SENT'`.
- Una notificación marcada como `SENT` no debe volver a procesarse.

### Índices

| Índice | Propósito |
|---|---|
| Único sobre `event_id` | Evitar envíos duplicados |
| `(status, last_attempt_at)` | Buscar pendientes y fallidas reintentables |
| `(booking_id, created_at DESC)` | Consultar el historial de una reserva |
| `(event_type, created_at DESC)` | Consultar envíos por tipo de evento |

### Flujo de estados

```text
PENDING ── envío exitoso ──> SENT
PENDING ── error temporal ──> PENDING (nuevo intento)
PENDING ── error permanente ──> FAILED
FAILED  ── reintento autorizado ──> PENDING
```

La tabla debe crearse mediante una migración Flyway dentro del rango asignado a HU-10. El número
definitivo de la migración queda pendiente de confirmar porque el repositorio ya contiene las
migraciones `V40`, `V41` y `V42`.

## Decisiones pendientes con Miguel

- Confirmar los nombres definitivos de `BookingRescheduled` y `BookingCancelled`.
- Confirmar si los eventos se publicarán como eventos de Spring después del commit o mediante una
  tabla outbox.
- Confirmar la forma de transportar los datos descriptivos del correo.
- Confirmar el contenido final y los datos obligatorios de cada plantilla.

## Trazabilidad

- HU relacionada: HU-10.
- Dependencias: HU-08 y HU-09.
- Consumidores: módulo `notification`.
- Eventos relacionados: `BookingCreated`, `BookingRescheduled`, `BookingCancelled`.
- Decisión arquitectónica: [ADR-011: Notificaciones de reserva mediante eventos](ADR-011-notificaciones-por-eventos.md).
