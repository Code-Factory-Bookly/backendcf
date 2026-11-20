# HU-15 - Reporte de ocupación por periodo

## Resumen

Como administrador, quiero ver reportes consolidados del porcentaje de ocupación de las agendas de los
profesionales por periodos (semana, mes), para identificar horas pico, evaluar la demanda de servicios y
optimizar la planeación de turnos. Criterio de calidad: ISO/IEC 25010, eficiencia de desempeño.

## Endpoint

```
GET /api/v1/reportes/ocupacion?periodo=MES
Authorization: Bearer <token de ADMIN>
```

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `periodo` | No (por defecto `MES`) | `SEMANA` = últimos 7 días; `MES` = últimos 30 días. Ambos terminan hoy. No distingue mayúsculas de minúsculas (`mes`, `Mes` y `MES` son equivalentes). |
## Reglas de cálculo

- Una reserva ocupa agenda si su estado es `CONFIRMADA` o `COMPLETED`. Las `CANCELADA` no cuentan.
- Capacidad = minutos de las franjas de `horario_semanal` de cada profesional en cada día del rango.
- Ocupación % = minutos reservados / capacidad × 100, con dos decimales y tope de 100.
- Si la capacidad es 0, la ocupación es 0 (no hay división por cero).
- Los datos se leen solo de la base de datos de la plataforma.

## Respuesta (200)

```json
{
  "period": "MES",
  "from": "2026-10-12",
  "to": "2026-11-10",
  "overallOccupancyPercent": 42.5,
  "byDayOfWeek": [
    { "dayOfWeek": "MONDAY", "bookedMinutes": 600, "capacityMinutes": 1200, "occupancyPercent": 50.0 }
  ],
  "byProfessional": [
    { "professionalId": "…", "professionalName": "…", "bookedMinutes": 300, "capacityMinutes": 800, "occupancyPercent": 37.5 }
  ]
}
```

Sin reservas en el periodo: 200 con todos los valores en 0 y sin excepciones internas.

## Errores

| Código | Escenario |
|---|---|
| 400 | `periodo` con un valor distinto de `SEMANA` o `MES`, sin importar mayúsculas (`VALIDATION_ERROR`) |
| 401 | No autenticado |
| 403 | Autenticado con un rol distinto de ADMIN |

## Decisiones

- No requiere tabla nueva: usa `bookings`, `horario_semanal` y `app_user`.
- El cálculo vive en una clase de dominio sin dependencias de Spring, igual que `AvailabilityCalculator`.