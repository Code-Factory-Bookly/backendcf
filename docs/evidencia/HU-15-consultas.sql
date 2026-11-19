-- HU-15: consultas de referencia del reporte de ocupación (PostgreSQL).
-- La aplicación lee reservas y franjas con JPA y calcula los porcentajes en Java;
-- estas consultas son el equivalente en SQL y se usan para validar el plan en la task de rendimiento.
-- Parámetros: :desde (inclusive) y :hasta (exclusivo), por ejemplo '2026-10-12' y '2026-11-11'.

-- 1) Minutos reservados por profesional
SELECT b.professional_id,
       SUM(EXTRACT(EPOCH FROM (b.end_time - b.start_time)) / 60) AS minutos_reservados
FROM bookings b
WHERE b.status IN ('CONFIRMADA', 'COMPLETED')
  AND b.start_time >= :desde AND b.start_time < :hasta
GROUP BY b.professional_id;

-- 2) Minutos reservados por día de la semana (1 = lunes ... 7 = domingo)
SELECT EXTRACT(ISODOW FROM b.start_time) AS dia_semana,
       SUM(EXTRACT(EPOCH FROM (b.end_time - b.start_time)) / 60) AS minutos_reservados
FROM bookings b
WHERE b.status IN ('CONFIRMADA', 'COMPLETED')
  AND b.start_time >= :desde AND b.start_time < :hasta
GROUP BY EXTRACT(ISODOW FROM b.start_time);

-- 3) Minutos de franja semanal por profesional y día (la capacidad se obtiene multiplicando
--    por cuántas veces cae ese día de la semana dentro del rango)
SELECT h.profesional_id, h.dia_semana,
       SUM(EXTRACT(EPOCH FROM (h.hora_fin - h.hora_inicio)) / 60) AS minutos_franja
FROM horario_semanal h
GROUP BY h.profesional_id, h.dia_semana;