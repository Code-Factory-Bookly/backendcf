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
