# HU-16 — Reporte general de uso de la plataforma

## Contrato

`GET /api/v1/reportes/general?periodo=SEMANA|MES`

El periodo por defecto es `MES` y termina en el día actual, igual que HU-15. La ventana es inclusiva en
la fecha inicial y exclusiva al comenzar el día siguiente a `to`.

La respuesta contiene:

- `totalReservations`: todas las reservas cuyo inicio está en el periodo.
- `cancelledReservations`: reservas con estado `CANCELADA`.
- `averageCancellationsPerDay`: cancelaciones divididas por la cantidad de días del periodo.
- `recognizedRevenue`: suma de precios de reservas `CONFIRMADA` y `COMPLETED`.
- `resourceUsage`: cantidad de reservas confirmadas/completadas que utilizaron cada recurso físico.

Cuando no existen reservas, las métricas numéricas devuelven cero, `resourceUsage` devuelve los recursos
registrados con cero usos y `message` informa que no hay datos.

## Decisión de ingresos

Se conserva el precio histórico en `bookings.price_at_booking`. La creación de una reserva toma el precio
vigente del servicio y lo guarda en el snapshot. La migración `V65` rellena reservas existentes desde
`servicios.precio`; la consulta usa ese snapshot y solo aplica el precio actual como compatibilidad si una
reserva antigua todavía no tiene valor.

## Recursos

HU-16 consume el modelo entregado por HU-11/HU-12:

- `recursos_fisicos(id, nombre, cantidad_total)`.
- `servicio_recurso(servicio_id, recurso_id)`.
- Reservas `CONFIRMADA`/`COMPLETED` enlazadas por servicio.

Las agregaciones se ejecutan en PostgreSQL. Las migraciones `V65` y `V66` agregan el snapshot de precio y
los índices de reporte.

## Seguridad, sesión y auditoría

El endpoint exige `ADMIN` y un JWT emitido después de MFA (`REPORT_MFA_VERIFIED`). La sesión del reporte
expira después de 15 minutos sin consultar el reporte. Cada consulta registra `REPORT_VIEWED` en `audit_log`
con actor, IP, periodo y rango consultado. La tabla de auditoría continúa siendo append-only.

## Pruebas para Calidad

Calidad debe validar, sobre esta rama:

1. Métricas con reservas confirmadas, completadas, canceladas y reprogramadas.
2. Periodos `SEMANA`, `MES`, valor omitido y valor inválido.
3. Cero reservas sin `null`, `NaN` ni errores.
4. Uso de recursos con servicios asociados y recursos sin uso.
5. Ingresos estables después de cambiar el precio del servicio.
6. `401` sin autenticación y `403` para CUSTOMER/PROFESSIONAL o ADMIN sin MFA.
7. Expiración después de 15 minutos de inactividad.
8. Registro de auditoría con actor, IP, rango y acción `REPORT_VIEWED`.
9. Inmutabilidad de `audit_log` y planes `EXPLAIN ANALYZE` de las consultas.
