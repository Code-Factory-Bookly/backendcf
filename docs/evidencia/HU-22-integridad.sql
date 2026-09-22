-- Evidencia HU-22 · Pruebas de integridad de la tabla profesionales
--
-- Ejecutar contra la base creada por Flyway (migracion V6 aplicada):
--
--   Get-Content docs/evidencia/HU-22-integridad.sql | docker exec -i backendcf-db psql -U backendcf -d backendcf
--
-- (En Linux/macOS: docker exec -i backendcf-db psql -U backendcf -d backendcf < docs/evidencia/HU-22-integridad.sql)
--
-- Todo corre dentro de una transaccion que termina en ROLLBACK: no deja datos en la base.
-- Cada prueba imprime "OK ..." si la base se comporta como se espera. Si alguna falla, el script
-- se detiene con "FALLO ..." y el detalle de la prueba que no se cumplio.

\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
v_prof       UUID := gen_random_uuid();
    v_customer   UUID := gen_random_uuid();
    v_admin      UUID := gen_random_uuid();
    v_sin_perfil UUID := gen_random_uuid();
    v_rol        VARCHAR;
    v_total      INTEGER;
BEGIN
    -- Cuentas base: un profesional, un cliente y un administrador.
INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES
    (v_prof,     'sofia.prueba@bookly.test',   '$2a$10$hash', 'Sofia Prueba',   'PROFESSIONAL', TRUE, now(), now()),
    (v_customer, 'cliente.prueba@bookly.test', '$2a$10$hash', 'Cliente Prueba', 'CUSTOMER',     TRUE, now(), now()),
    (v_admin,    'admin.prueba@bookly.test',   '$2a$10$hash', 'Admin Prueba',   'ADMIN',        TRUE, now(), now());

-- 1. Registro exitoso. El INSERT no incluye la columna rol, igual que el que hace la aplicacion:
--    la base la completa con PROFESSIONAL.
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (v_prof, 'Ortodoncia', now(), now());
SELECT rol INTO v_rol FROM profesionales WHERE id = v_prof;
IF v_rol <> 'PROFESSIONAL' THEN
        RAISE EXCEPTION 'FALLO prueba 1: el rol del perfil deberia ser PROFESSIONAL y es %', v_rol;
END IF;
    RAISE NOTICE 'OK prueba 1: un profesional valido se registra y el perfil queda con rol PROFESSIONAL';

    -- 2. No puede existir un perfil sin cuenta.
BEGIN
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (gen_random_uuid(), 'Endodoncia', now(), now());
RAISE EXCEPTION 'FALLO prueba 2: se acepto un perfil sin cuenta';
EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 2: la FK rechaza un perfil sin cuenta';
END;

    -- 3. Un perfil solo puede pertenecer a una cuenta con rol PROFESSIONAL: una cuenta CUSTOMER se rechaza.
BEGIN
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (v_customer, 'Endodoncia', now(), now());
RAISE EXCEPTION 'FALLO prueba 3: se acepto un perfil de profesional para una cuenta CUSTOMER';
EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 3: la FK compuesta rechaza el perfil de una cuenta CUSTOMER';
END;

    -- 4. Lo mismo para una cuenta ADMIN.
BEGIN
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (v_admin, 'Endodoncia', now(), now());
RAISE EXCEPTION 'FALLO prueba 4: se acepto un perfil de profesional para una cuenta ADMIN';
EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 4: la FK compuesta rechaza el perfil de una cuenta ADMIN';
END;

    -- 5. No se puede forzar otro rol dentro del propio perfil.
BEGIN
INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at)
VALUES (v_customer, 'CUSTOMER', 'Endodoncia', now(), now());
RAISE EXCEPTION 'FALLO prueba 5: se acepto un perfil con rol distinto de PROFESSIONAL';
EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK prueba 5: el perfil solo admite el rol PROFESSIONAL';
END;

    -- 6. La especialidad es obligatoria.
BEGIN
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (v_customer, NULL, now(), now());
RAISE EXCEPTION 'FALLO prueba 6: se acepto una especialidad nula';
EXCEPTION WHEN not_null_violation THEN
        RAISE NOTICE 'OK prueba 6: la especialidad nula se rechaza';
END;

    -- 7. La especialidad no puede ser vacia ni solo espacios.
BEGIN
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (v_customer, '   ', now(), now());
RAISE EXCEPTION 'FALLO prueba 7: se acepto una especialidad en blanco';
EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK prueba 7: la especialidad en blanco se rechaza';
END;

    -- 8. Una cuenta no puede tener dos perfiles.
BEGIN
INSERT INTO profesionales (id, especialidad, created_at, updated_at)
VALUES (v_prof, 'Periodoncia', now(), now());
RAISE EXCEPTION 'FALLO prueba 8: se acepto un segundo perfil para la misma cuenta';
EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK prueba 8: una cuenta solo admite un perfil de profesional';
END;

    -- 9. Una cuenta con perfil no se puede eliminar (se deshabilita).
BEGIN
DELETE FROM app_user WHERE id = v_prof;
RAISE EXCEPTION 'FALLO prueba 9: se elimino una cuenta que tiene perfil';
EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 9: la cuenta con perfil no se elimina';
END;

    -- 10. Tampoco se puede cambiar el rol de una cuenta que ya tiene perfil.
BEGIN
UPDATE app_user SET role = 'CUSTOMER' WHERE id = v_prof;
RAISE EXCEPTION 'FALLO prueba 10: se cambio el rol de una cuenta que tiene perfil';
EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 10: el rol de una cuenta con perfil no se puede cambiar';
END;

    -- 11. Una cuenta sin perfil si puede cambiar de rol (la restriccion solo protege a quien tiene perfil).
UPDATE app_user SET role = 'PROFESSIONAL' WHERE id = v_customer;
UPDATE app_user SET role = 'CUSTOMER' WHERE id = v_customer;
RAISE NOTICE 'OK prueba 11: una cuenta sin perfil puede cambiar de rol';

    -- 12. El correo de un profesional es unico: sigue rigiendo uk_app_user_email.
BEGIN
INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES (gen_random_uuid(), 'sofia.prueba@bookly.test', '$2a$10$hash', 'Otra Sofia', 'PROFESSIONAL', TRUE, now(), now());
RAISE EXCEPTION 'FALLO prueba 12: se acepto un correo duplicado';
EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK prueba 12: el correo duplicado se rechaza';
END;

    -- 13. Solo existen los roles definidos (CUSTOMER, PROFESSIONAL, ADMIN).
BEGIN
INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES (gen_random_uuid(), 'rol.raro@bookly.test', '$2a$10$hash', 'Rol Raro', 'DOCTOR', TRUE, now(), now());
RAISE EXCEPTION 'FALLO prueba 13: se acepto un rol inexistente';
EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK prueba 13: un rol inexistente se rechaza';
END;

    -- 14. Consulta de consistencia (README_BASE_DE_DATOS, consulta 12): cuentas PROFESSIONAL sin perfil.
    --     La base no exige que toda cuenta PROFESSIONAL tenga perfil; la consulta debe detectarlo.
INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES (v_sin_perfil, 'sin.perfil@bookly.test', '$2a$10$hash', 'Sin Perfil', 'PROFESSIONAL', TRUE, now(), now());
SELECT COUNT(*) INTO v_total
FROM app_user u
         LEFT JOIN profesionales p ON p.id = u.id
WHERE u.role = 'PROFESSIONAL'
  AND p.id IS NULL
  AND u.id = v_sin_perfil;
IF v_total <> 1 THEN
        RAISE EXCEPTION 'FALLO prueba 14: la consulta de consistencia no detecto la cuenta sin perfil (filas: %)', v_total;
END IF;
    RAISE NOTICE 'OK prueba 14: la consulta de consistencia detecta una cuenta PROFESSIONAL sin perfil';

    RAISE NOTICE 'Todas las pruebas de integridad de HU-22 pasaron';
END
$$;

ROLLBACK;