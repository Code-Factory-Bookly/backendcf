-- HU-16: ejecutar contra PostgreSQL con datos representativos antes y después de V66.
-- Las consultas agregan en la base de datos; no cargan reservas completas en memoria.

EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM bookings
WHERE start_time >= TIMESTAMP '2026-11-01 00:00:00'
  AND start_time < TIMESTAMP '2026-12-01 00:00:00';

EXPLAIN (ANALYZE, BUFFERS)
SELECT COALESCE(SUM(COALESCE(b.price_at_booking, s.precio)), 0)
FROM bookings b
JOIN servicios s ON s.id = b.service_id
WHERE b.status IN ('CONFIRMADA', 'COMPLETED')
  AND b.start_time >= TIMESTAMP '2026-11-01 00:00:00'
  AND b.start_time < TIMESTAMP '2026-12-01 00:00:00';

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.nombre, r.cantidad_total, COUNT(DISTINCT b.id)
FROM recursos_fisicos r
LEFT JOIN servicio_recurso sr ON sr.recurso_id = r.id
LEFT JOIN bookings b
       ON b.service_id = sr.servicio_id
      AND b.status IN ('CONFIRMADA', 'COMPLETED')
      AND b.start_time >= TIMESTAMP '2026-11-01 00:00:00'
      AND b.start_time < TIMESTAMP '2026-12-01 00:00:00'
GROUP BY r.id, r.nombre, r.cantidad_total;
