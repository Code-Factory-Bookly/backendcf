-- HU-15: validacion de rendimiento de la consulta del reporte de ocupacion.
-- Carga 200.000 reservas de prueba dentro de una transaccion y la deshace al final (ROLLBACK),
-- por lo que no deja datos en la base. Ejecutar sobre la base local con las migraciones aplicadas.

BEGIN;

-- 20 profesionales y 1 cliente de prueba
INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
SELECT gen_random_uuid(), 'perfpro' || g || '@perf.local', 'x', 'Perf Profesional ' || g,
       'PROFESSIONAL', TRUE, now(), now()
FROM generate_series(1, 20) g;

INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES (gen_random_uuid(), 'perfcustomer@perf.local', 'x', 'Perf Cliente', 'CUSTOMER', TRUE, now(), now());

INSERT INTO profesionales (id, especialidad, created_at, updated_at)
SELECT id, 'Perf', now(), now()
FROM app_user
WHERE email LIKE 'perfpro%@perf.local';

INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, created_at, updated_at)
VALUES (gen_random_uuid(), 'Perf servicio', 'Perf', 60, 1, 'ACTIVO', now(), now());

-- 10.000 reservas por profesional, una por hora, sin solapamientos (respeta la restriccion de V46).
-- Cubren desde hace 300 dias hasta unos 116 dias en el futuro; 80% CONFIRMADA, 10% COMPLETED, 10% CANCELADA.
INSERT INTO bookings (customer_id, professional_id, service_id, start_time, end_time, status)
SELECT c.id, p.id, s.id,
       date_trunc('day', now()) - interval '300 days' + n * interval '1 hour',
       date_trunc('day', now()) - interval '300 days' + n * interval '1 hour' + interval '1 hour',
       CASE WHEN n % 10 = 0 THEN 'CANCELADA'
            WHEN n % 10 IN (1, 2) THEN 'COMPLETED'
            ELSE 'CONFIRMADA' END
FROM generate_series(0, 9999) n
CROSS JOIN (SELECT id FROM app_user WHERE email = 'perfcustomer@perf.local') c
CROSS JOIN (SELECT id FROM app_user WHERE email LIKE 'perfpro%@perf.local') p
CROSS JOIN (SELECT id FROM servicios WHERE nombre = 'Perf servicio') s;

ANALYZE bookings;

-- Consulta equivalente a BookingRepository.findByStatusesInRange para el periodo MES (ultimos 30 dias)
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM bookings
WHERE status IN ('CONFIRMADA', 'COMPLETED')
  AND start_time >= current_date - 29
  AND start_time < current_date + 1;

ROLLBACK;