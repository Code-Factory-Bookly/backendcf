# HU-05: Bloqueo temporal de agenda

> Como profesional, quiero bloquear una franja específica de mi agenda (vacaciones, imprevistos), para
> evitar que se generen reservas en momentos en los que estaré ausente.

Este documento describe el contrato y las decisiones de diseño, para que el equipo de Calidad escriba
las pruebas y los casos de prueba. La rama `feature/hu-05-bloqueo-agenda` **no incluye pruebas
automatizadas**.

## Endpoints

Todos bajo `/api/v1/horarios/{professionalId}/bloqueos`, con la misma seguridad que el horario semanal
(HU-04): rol `PROFESSIONAL` o `ADMIN`, y un profesional solo puede gestionar su propia agenda
(`OwnershipGuard`, HU-21).

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/bloqueos` | 200 con los bloqueos que aún no terminaron, ordenados por inicio |
| `POST` | `/bloqueos` | 201 con el bloqueo creado |
| `DELETE` | `/bloqueos/{blockId}` | 204; el rango vuelve a estar disponible |

Cuerpo del `POST`:

```json
{ "startTime": "2026-12-21T08:00:00", "endTime": "2026-12-26T20:00:00", "reason": "Vacaciones" }
```

`reason` es opcional (máximo 200 caracteres). El rango es `[startTime, endTime)`: un bloqueo que termina
a las 12:00 no impide una reserva que empieza a las 12:00.

## Reglas y errores

| Caso | Respuesta |
|---|---|
| Sin token | 401 `UNAUTHORIZED` |
| Profesional que intenta bloquear la agenda de otro | 403 `ACCESS_DENIED` |
| Rol `CUSTOMER` | 403 `ACCESS_DENIED` |
| Profesional inexistente | 404 `PROFESSIONAL_NOT_FOUND` |
| Falta `startTime` o `endTime` | 400 `VALIDATION_ERROR` |
| `endTime` igual o anterior a `startTime` | 400 `SCHEDULE_VALIDATION_ERROR` (detalle en `endTime`) |
| `startTime` en el pasado | 400 `SCHEDULE_VALIDATION_ERROR` (detalle en `startTime`) |
| Hay reservas `CONFIRMADA` en el rango | 409 `AGENDA_BLOCK_HAS_BOOKINGS`: `details` trae una entrada por cita afectada (id de la reserva → fecha, hora, cliente y servicio). No se crea el bloqueo |
| Se cruza con otro bloqueo del mismo profesional | 409 `AGENDA_BLOCK_OVERLAP` con los bloqueos en conflicto |
| Borrar un bloqueo que no existe o es de otro profesional | 404 `AGENDA_BLOCK_NOT_FOUND` |

Las reservas canceladas no impiden bloquear.

## Efecto en el resto del sistema

- **Disponibilidad (HU-07):** `GET /api/v1/disponibilidad` deja de ofrecer los bloques que caen en un
  bloqueo, igual que si hubiera una reserva. El bloqueo se trata como un periodo ocupado.
- **Reservas (HU-08):** `POST /api/v1/reservas` en un rango bloqueado responde 409 con
  `error: SLOT_BLOCKED`. Cubre el caso de un cliente que consultó la disponibilidad antes del bloqueo.

## Decisiones de diseño

- **Rechazar en vez de cancelar automáticamente.** El criterio de aceptación pide que el sistema "rechace
  el bloqueo automático" y muestre las citas afectadas: el profesional decide si las cancela o
  reprograma. El sistema nunca cancela reservas por su cuenta.
- **Integridad en la base (ADR-006).** `V50__create_agenda_blocks.sql` impide rangos invertidos
  (`CHECK fin > inicio`) y dos bloqueos solapados del mismo profesional (`EXCLUDE USING gist`, como la
  restricción de reservas de V46). Si dos bloqueos solapados llegan a la vez, el segundo recibe 409
  `AGENDA_BLOCK_OVERLAP`.
- **Puertos entre módulos.** Disponibilidad y reservas consultan los bloqueos a través del puerto
  `AgendaBlocks` (módulo `schedule`), y el módulo `schedule` consulta las reservas afectadas a través de
  `ConfirmedBookingsLookup`. Ningún módulo usa directamente el repositorio de otro para esta HU.
- **Compatibilidad con las pruebas existentes.** `CreateBookingService` y `AvailabilityQueryAdapter`
  conservan su constructor anterior (sin bloqueos) para no romper las pruebas que los construyen a mano;
  Spring usa el constructor nuevo. Las pruebas de estas clases deberán pasar el puerto para cubrir los
  bloqueos.
- **Brecha conocida (condición de carrera).** Si un cliente reserva y el profesional bloquea el mismo rango
  en el mismo instante, ambas operaciones pueden completarse. La base no cruza las dos tablas; cerrar ese
  caso requiere un bloqueo de fila por profesional y queda fuera del alcance de esta HU.
- **Sin auditoría.** Los bloqueos no se registran en la bitácora de HU-19; el criterio de aceptación no
  lo pide.

## Casos sugeridos para Calidad

1. Bloquear un rango sin reservas: 201 y la disponibilidad ya no ofrece esas horas.
2. Bloquear un rango con reservas confirmadas: 409 con cada cita en `details`, y no se crea el bloqueo.
3. Bloquear con una reserva cancelada en el rango: 201.
4. Rango invertido o en el pasado: 400.
5. Bloqueo solapado con otro: 409; bloqueo contiguo (fin = inicio del otro): 201.
6. Reservar dentro de un rango bloqueado: 409 `SLOT_BLOCKED`.
7. Borrar el bloqueo: 204 y la disponibilidad vuelve a ofrecer las horas.
8. Profesional que bloquea o lista la agenda de otro: 403. `CUSTOMER`: 403. Sin token: 401.
