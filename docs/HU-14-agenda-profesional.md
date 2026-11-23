# HU-14 – Historial de reservas del profesional ("Mi Agenda")

## Decisión de diseño

Se agrega la ruta `GET /api/v1/reservas/profesional/agenda`.

| Rol | Comportamiento |
|---|---|
| `PROFESSIONAL` | Consulta **siempre su propia agenda**: el identificador sale del token JWT. Un `professionalId` enviado en la petición se ignora, así nadie puede ver la agenda de otra cuenta. |
| `ADMIN` | No tiene agenda propia: debe indicar `?professionalId=<id>`. Sin él responde 400; con un profesional que no existe, 404 `PROFESSIONAL_NOT_FOUND`. |
| `CUSTOMER` / sin token | 403 / 401. |

La ruta antigua `GET /api/v1/reservas/profesional/{professionalId}` (PR #40) **se retiró**: no estaba paginada, no filtraba por fecha, devolvía también las canceladas y la agenda nueva cubre su uso (el profesional ve lo suyo; el administrador usa `?professionalId=`). Quien la consumiera recibe ahora 404 y debe migrar a `/profesional/agenda`.

## Consulta

| Parámetro | Descripción |
|---|---|
| `professionalId` | Solo `ADMIN` (obligatorio para él); el `PROFESSIONAL` lo ignora. |
| `from` | Fecha `yyyy-MM-dd`, inclusiva. Opcional; por defecto, hoy. |
| `to` | Fecha `yyyy-MM-dd`, inclusiva. Opcional; por defecto, el mismo día que `from`. |

- Sin parámetros se devuelve la agenda de **hoy**.
- "Hoy" se calcula en la zona horaria de Colombia (`America/Bogota`, GMT-5), no en la del servidor. Un servidor en UTC ya estaría en el día siguiente desde las 19:00 hora de Colombia.
- El listado **no está paginado**, por eso el rango máximo es de **31 días**. Más de 31 días, o `from` posterior a `to`, responde 400 (`VALIDATION_ERROR`).
- Una fecha con formato inválido responde 400.

## Respuesta

```json
{
  "from": "2026-11-30",
  "to": "2026-12-04",
  "content": [
    {
      "id": "…",
      "startTime": "2026-11-30T09:00:00",
      "endTime": "2026-11-30T10:00:00",
      "customerName": "Ana Cliente",
      "serviceName": "Consulta general",
      "status": "CONFIRMADA"
    }
  ],
  "message": null
}
```

- El orden es cronológico por `startTime`; el `id` desempata para que sea estable.
- Solo se exponen hora, nombre del cliente, servicio y estado. No se devuelven identificadores de cuentas ajenas ni datos de pago.
- Si no hay reservas, `content` es `[]` y `message` informa que la agenda está libre:
  - Solo hoy: `Tu agenda se encuentra libre para la jornada de hoy`.
  - Otro día o rango: `Tu agenda se encuentra libre en el periodo consultado`.

## Qué reservas se muestran

| Estado | ¿Aparece en la agenda? |
|---|---|
| `CONFIRMADA` | Sí |
| `COMPLETED` | Sí (una cita ya atendida sigue en la agenda de ese día) |
| `RESCHEDULED` | Sí (hoy ningún flujo asigna este estado) |
| `CANCELADA` | **No.** La cancelación libera la franja (HU-09); si otro cliente la reserva, esa nueva reserva sí aparece. |

## Persistencia

La consulta se ejecuta en PostgreSQL (`BookingRepository.findAgenda`) y usa el índice existente `idx_bookings_professional_start (professional_id, start_time DESC)`. **No hay migración nueva.** Conviene medir con `EXPLAIN ANALYZE` si un profesional acumula muchas reservas.

## Aviso de cancelación al profesional (HU-10)

Al cancelarse una reserva, además del correo al cliente, el profesional recibe el correo **"Cancelación de cita en tu agenda"** con cliente, servicio, fecha y motivo. Es el único cambio de notificaciones.

- El aviso **no se registra** en `notification_delivery`: el listado `GET /api/v1/reservas/{id}/notificaciones` es visible para el cliente y expondría el correo del profesional. Un fallo de este correo solo queda en el log y no afecta al correo del cliente ni a la cancelación.
- Si el profesional no tiene correo, no se envía nada.
- La creación y la reprogramación siguen notificando solo al cliente.

## Migración para el frontend

`GET /api/v1/reservas/profesional/{professionalId}` **ya no existe** y responde 404. La reemplaza `GET /api/v1/reservas/profesional/agenda`. El contrato está en [`docs/openapi.yaml`](openapi.yaml).

| | Antes | Ahora |
|---|---|---|
| Ruta | `/reservas/profesional/{professionalId}` | `/reservas/profesional/agenda` |
| Quién | El propio profesional o `ADMIN` | `PROFESSIONAL` (la suya) o `ADMIN` con `?professionalId=` |
| Respuesta | Lista plana de reservas | Objeto `{ from, to, content[], message }` |
| Canceladas | Incluidas | No se muestran |
| Orden | Sin garantía | Cronológico por `startTime` |
| Fechas | Sin filtro | Hoy por defecto; `?from=` y `?to=` (`yyyy-MM-dd`), máximo 31 días |
| Cada reserva | `id, customerId, professionalId, serviceId, customerName, professionalName, serviceName, startTime, endTime, status, createdAt, updatedAt, cancelledAt, cancellationReason` | `id, startTime, endTime, customerName, serviceName, status` |

Qué cambiar en el cliente: apuntar a la ruta nueva, leer la lista en `content` (no en la raíz), mostrar `message` cuando `content` esté vacío, y para el administrador enviar `professionalId`. Si necesita `customerId` o los datos de cancelación, hay que pedirlo: la agenda no los expone a propósito.

## Fuera de alcance (decidido)

- Consulta de cancelaciones por usuario o por hora, y cualquier HU adicional sobre cancelaciones.
- Auditoría de las consultas del administrador a la agenda de un profesional (hoy no se registran).

## Validación para Calidad

1. El profesional autenticado obtiene solo sus reservas, ordenadas cronológicamente, con hora, cliente y servicio.
2. Un profesional nunca ve reservas de otro; la ruta ignora un `professionalId` enviado por parámetro.
3. Sin parámetros se consulta hoy en hora de Colombia (verificado con un reloj fijo a las 22:30 de Colombia = 03:30 UTC del día siguiente).
4. El rango `from`–`to` es inclusivo en ambos extremos y se rechaza si está invertido o supera 31 días.
5. Las reservas canceladas no aparecen; una franja liberada y reservada de nuevo sí.
6. Sin reservas: `content: []` y mensaje informativo (distinto para hoy y para otro periodo).
7. Sin token → 401; `CUSTOMER` → 403; `ADMIN` con `professionalId` → 200, sin él → 400, inexistente → 404. La ruta antigua `/profesional/{id}` → 404.
8. Al cancelar, el cliente y el profesional reciben su correo; si falla uno, el otro se envía igual; el profesional no queda en el listado de notificaciones.
