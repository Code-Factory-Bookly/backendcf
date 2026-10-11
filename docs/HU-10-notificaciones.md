# HU-10: contrato de eventos para notificaciones de reserva

## Estado

Contrato actualizado con base en los cambios integrados de HU-09 en `main`. `eventId` ya está
incorporado en los eventos actuales; queda pendiente confirmar el alcance de la reprogramación.

## Propósito

El módulo `notification` reacciona a los cambios confirmados de una reserva y registra/envía
el correo correspondiente al cliente. La notificación no debe decidir si una reserva es válida;
esa responsabilidad pertenece al módulo `booking`.

## Eventos del contrato

El módulo `booking` publicará los siguientes eventos:

| Evento | Momento de publicación | Notificación esperada |
|---|---|---|
| `BookingCreatedEvent` | Después de crear y confirmar una reserva | Confirmación de reserva |
| `BookingCancelledEvent` | Después de confirmar una cancelación | Cancelación y liberación del cupo |
| `BookingRescheduledEvent` | No disponible actualmente en `main` | Pendiente de confirmar |

Los eventos deben consumirse después del commit de la transacción que modifica la reserva. En la
implementación actual, `BookingCancelledEvent` se publica dentro de la transacción para que HU-19
pueda registrar la auditoría transaccional; el consumidor de notificaciones debe reaccionar con un
listener `AFTER_COMMIT`. Un fallo del correo no debe revertir la operación de reserva.

### Contrato actualmente disponible

`BookingCreatedEvent` y `BookingCancelledEvent` ya exponen un `eventId` UUID generado al crear el
evento. `BookingCancelledEvent` también expone `bookingId`, `customerId`, `professionalId`,
`serviceId`, `startTime`, `endTime`, `cancelledAt`, `cancellationReason` y `occurredAt`. El contrato
actual no expone datos descriptivos como el correo del cliente, el nombre del servicio o el nombre
del profesional.

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

Los eventos de creación y reprogramación deben incluir `startTime` y `endTime`. El evento de
cancelación integrado en `main` ya incluye esos campos.
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

- `id` es la clave primaria y se genera como UUID.
- `booking_id` referencia `bookings(id)` y no utiliza `ON DELETE CASCADE`.
- `event_id` es único para garantizar idempotencia y evitar duplicar un correo por evento.
- `event_type` solo admite `BOOKING_CREATED`, `BOOKING_RESCHEDULED` y `BOOKING_CANCELLED`.
- `status` solo admite `PENDING`, `SENT` y `FAILED`.
- `attempts` es obligatorio y debe cumplir `attempts >= 0`.
- `sent_at` debe estar informado cuando `status = 'SENT'` y debe ser nulo en otro estado.
- `error_message` debe estar informado cuando `status = 'FAILED'`.
- Una notificación marcada como `SENT` no debe volver a procesarse.
- El formato del correo se valida en la aplicación; la base conserva el límite de 320 caracteres
  coherente con `app_user.email`.

### Restricciones físicas propuestas

La migración deberá materializar las reglas con nombres explícitos para facilitar el diagnóstico
de errores y su traducción en la aplicación:

```sql
CONSTRAINT pk_notification_delivery PRIMARY KEY (id),
CONSTRAINT uk_notification_delivery_event UNIQUE (event_id),
CONSTRAINT fk_notification_delivery_booking
    FOREIGN KEY (booking_id) REFERENCES bookings (id),
CONSTRAINT ck_notification_delivery_event_type CHECK (
    event_type IN ('BOOKING_CREATED', 'BOOKING_RESCHEDULED', 'BOOKING_CANCELLED')
),
CONSTRAINT ck_notification_delivery_status CHECK (
    status IN ('PENDING', 'SENT', 'FAILED')
),
CONSTRAINT ck_notification_delivery_attempts CHECK (attempts >= 0),
CONSTRAINT ck_notification_delivery_sent_at CHECK (
    (status = 'SENT' AND sent_at IS NOT NULL)
    OR (status <> 'SENT' AND sent_at IS NULL)
),
CONSTRAINT ck_notification_delivery_failed_error CHECK (
    status <> 'FAILED' OR error_message IS NOT NULL
)
```

La aplicación debe traducir una violación de `uk_notification_delivery_event` como un evento ya
procesado, no como un error genérico del servidor. Las restricciones de dominio y de relación
permanecen en PostgreSQL como autoridad final, de acuerdo con ADR-006.

### Índices

| Índice | Propósito |
|---|---|
| Único sobre `event_id` | Evitar envíos duplicados |
| `(status, last_attempt_at)` | Buscar pendientes y fallidas reintentables |
| `(booking_id, created_at DESC)` | Consultar el historial de una reserva |
| `(event_type, created_at DESC)` | Consultar envíos por tipo de evento |

### Definición física y consultas objetivo

La migración deberá crear los índices con nombres explícitos:

```sql
CREATE INDEX ix_notification_delivery_status_attempt
    ON notification_delivery (status, last_attempt_at);

CREATE INDEX ix_notification_delivery_booking_created
    ON notification_delivery (booking_id, created_at DESC);

CREATE INDEX ix_notification_delivery_type_created
    ON notification_delivery (event_type, created_at DESC);
```

La restricción única `uk_notification_delivery_event` crea el índice único que soporta la
idempotencia; no se agregará otro índice redundante sobre `event_id`.

Consultas principales que justifican los índices:

```sql
-- Lote de notificaciones pendientes o fallidas para reintento
SELECT id, event_id, booking_id, recipient_email, attempts
FROM notification_delivery
WHERE status IN ('PENDING', 'FAILED')
ORDER BY last_attempt_at NULLS FIRST
LIMIT 100;

-- Historial de entregas de una reserva
SELECT event_type, status, attempts, created_at, sent_at, error_message
FROM notification_delivery
WHERE booking_id = :booking_id
ORDER BY created_at DESC;

-- Seguimiento de entregas por tipo de evento
SELECT event_type, status, created_at, sent_at
FROM notification_delivery
WHERE event_type = :event_type
ORDER BY created_at DESC;
```

El índice de reintentos se justifica por la consulta operativa de procesamiento; el índice por
reserva permite trazabilidad determinista; y el índice por tipo facilita métricas y diagnóstico.
La paginación por `LIMIT` evita procesar un volumen ilimitado en una sola operación.

### Flujo de estados

```text
PENDING ── envío exitoso ──> SENT
PENDING ── error temporal ──> PENDING (nuevo intento)
PENDING ── error permanente ──> FAILED
FAILED  ── reintento autorizado ──> PENDING
```

La tabla se creará mediante la migración `V43__create_notification_delivery.sql`, dentro del rango
asignado a HU-10. Se eligió `V43` porque el repositorio ya contiene las migraciones `V40`, `V41` y
`V42`.

## Impacto en la API REST

HU-10 no agrega un endpoint público nuevo. La creación, reprogramación y cancelación continúan
siendo operaciones del recurso `/api/v1/reservas`; el módulo `notification` reacciona internamente
a los eventos producidos por esas operaciones.

Por lo tanto, `docs/openapi.yaml` se actualiza para:

- incluir HU-10 dentro del alcance documentado;
- indicar explícitamente que no existe una ruta REST propia de notificaciones;
- enlazar este documento como referencia del contrato interno de eventos.

No se exponen endpoints para forzar envíos, consultar reintentos o modificar estados técnicos,
porque esas operaciones no forman parte del alcance funcional actual de HU-10.

## Decisiones pendientes con Miguel

- Confirmar si `BookingRescheduledEvent` entra en el alcance de HU-10 o queda para una evolución
  posterior.
- Confirmar que el listener de notificaciones se ejecutará después del commit; la publicación actual
  ocurre dentro de la transacción por la necesidad de auditoría de HU-19.
- Confirmar la forma de transportar los datos descriptivos del correo.
- Confirmar el contenido final y los datos obligatorios de cada plantilla.

## Trazabilidad

- HU relacionada: HU-10.
- Dependencias: HU-08 y HU-09.
- Consumidores: módulo `notification`.
- Eventos relacionados: `BookingCreatedEvent`, `BookingCancelledEvent` y, como pendiente,
  `BookingRescheduledEvent`.
- Decisión arquitectónica: [ADR-011: Notificaciones de reserva mediante eventos](ADR-011-notificaciones-por-eventos.md).
