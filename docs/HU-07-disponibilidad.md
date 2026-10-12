# HU-07 - Consulta de disponibilidad de agenda

## Resumen

Como cliente, quiero consultar los horarios disponibles de un profesional para un servicio específico,
para elegir el bloque de tiempo que más me convenga. Criterio de calidad: ISO/IEC 25010, usabilidad.

## Endpoint

```
GET /api/v1/disponibilidad?profesionalId={uuid}&servicioId={uuid}&desde=2026-10-12&hasta=2026-10-18
Authorization: Bearer <token>
```

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `profesionalId` | Sí | Profesional cuya agenda se consulta |
| `servicioId` | Sí | Servicio ACTIVO; su duración define el tamaño del bloque |
| `desde` | Sí | Primer día del rango (inclusivo), `yyyy-MM-dd` |
| `hasta` | Sí | Último día del rango (inclusivo), máximo 31 días desde `desde` |

### Respuesta con bloques libres (200)

```json
{
  "professionalId": "…",
  "serviceId": "…",
  "durationMinutes": 60,
  "from": "2026-10-12",
  "to": "2026-10-18",
  "available": true,
  "message": null,
  "days": [
    {
      "date": "2026-10-12",
      "slots": [
        { "startTime": "2026-10-12T08:00:00", "endTime": "2026-10-12T09:00:00" },
        { "startTime": "2026-10-12T10:00:00", "endTime": "2026-10-12T11:00:00" }
      ]
    }
  ]
}
```

Cada bloque tiene el mismo formato que el cuerpo de `POST /api/v1/reservas` (`startTime`/`endTime`).

### Sin bloques libres (200)

Responde `available: false`, `days: []` y un `message` que informa que no hay horarios libres en el rango
y sugiere ampliar el filtro de fechas o elegir otro profesional.

### Errores

| Código | Escenario |
|--------|-----------|
| 400 | Parámetros faltantes, fecha con formato inválido, `hasta` anterior a `desde`, rango mayor a 31 días o completamente en el pasado (`VALIDATION_ERROR`) |
| 401 | No autenticado |
| 404 | Profesional inexistente (`PROFESSIONAL_NOT_FOUND`) o servicio inexistente/inactivo (`SERVICE_NOT_FOUND`) |

## Reglas de cálculo

1. Se toman las franjas semanales del profesional (HU-04) que coinciden con el día de la semana de cada fecha del rango.
2. Dentro de cada franja se generan bloques consecutivos con la duración del servicio (HU-06), empezando en la hora inicial de la franja. Un bloque que no cabe completo al final de la franja se descarta.
3. Se excluyen los bloques que ya pasaron y los que se solapan con reservas `CONFIRMADA` del profesional (HU-08). Una reserva contigua no bloquea el bloque siguiente.

## Dependencias y limitaciones

- **Bloqueos manuales de agenda (vacaciones, ausencias, descansos):** el modelo actual no los contempla;
  no existe tabla, endpoint ni HU que los registre. El criterio "no mostrar bloques bloqueados" de HU-07
  se cumple hoy únicamente respecto de las reservas `CONFIRMADA`. Es una limitación conocida y está fuera
  del alcance de esta HU; depende de una HU futura de bloqueos de agenda.
- **Cómo se integrará:** `AvailabilityCalculator` trata cualquier `BusyPeriod` como tiempo ocupado.
  Cuando exista la HU de bloqueos bastará con que `AvailabilityQuery.findBusyPeriods` y su adaptador
  incluyan también los periodos bloqueados; el cálculo no cambia.
- **Estados de reserva:** solo `CONFIRMADA` ocupa un bloque. Una reserva `CANCELADA`, `RESCHEDULED` o
  `COMPLETED` no lo ocupa (`BookingRepository.findOverlappingBookings`).
- La consulta es informativa. Dos clientes pueden ver el mismo bloque libre a la vez; la reserva
  simultánea la resuelve `POST /api/v1/reservas` (ADR-006).
- No requiere migraciones: usa las tablas `horario_semanal`, `servicios` y `bookings`.

## Autorización

Solo el rol `CUSTOMER` puede consultar la disponibilidad: la HU describe la consulta como una acción del cliente y el flujo continúa con `POST /api/v1/reservas`. `SecurityConfiguration` aplica `hasRole("CUSTOMER")` sobre `GET /api/v1/disponibilidad`; PROFESSIONAL y ADMIN reciben 403 `ACCESS_DENIED` y sin token se recibe 401. Cubierto por `AvailabilityControllerSecurityTest`.

## Arquitectura

Módulo `availability` con capas `presentation`, `application`, `domain` e `infrastructure`; ver
[README_ARQUITECTURA.md](../README_ARQUITECTURA.md).