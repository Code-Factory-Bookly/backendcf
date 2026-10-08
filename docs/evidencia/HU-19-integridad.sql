-- Evidencia HU-19 · Pruebas de integridad e inmutabilidad de audit_log
--
-- Ejecutar contra la base creada por Flyway con V40 aplicada:
--
--   docker exec -i backendcf-db psql -U backendcf -d backendcf < docs/evidencia/HU-19-integridad.sql
--
-- Todo corre dentro de una transaccion que termina en ROLLBACK: no deja datos de prueba.
-- Cada caso imprime "OK ..." si la base se comporta como se espera. Si alguna prueba falla,
-- el script se detiene con "FALLO ...".

\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    v_actor       UUID := gen_random_uuid();
    v_resource    UUID := gen_random_uuid();
    v_audit       UUID := gen_random_uuid();
    v_metadata    JSONB;
    v_total       INTEGER;
BEGIN
    INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
    VALUES (v_actor, 'audit.actor@bookly.test', '$2a$10$hash', 'Audit Actor', 'ADMIN', TRUE, now(), now());

    -- 1. Registro valido y metadata por defecto.
    INSERT INTO audit_log (
        id, actor_user_id, action_type, resource_type, resource_id, occurred_at
    )
    VALUES (
        v_audit, v_actor, 'ROLE_CHANGED', 'USER', v_resource, now()
    );

    SELECT metadata INTO v_metadata FROM audit_log WHERE id = v_audit;
    IF v_metadata <> '{}'::jsonb THEN
        RAISE EXCEPTION 'FALLO prueba 1: metadata por defecto inesperada: %', v_metadata;
    END IF;
    RAISE NOTICE 'OK prueba 1: se registra una accion valida con metadata vacia';

    -- 2. El actor es obligatorio.
    BEGIN
        INSERT INTO audit_log (id, actor_user_id, action_type, resource_type, occurred_at)
        VALUES (gen_random_uuid(), NULL, 'ROLE_CHANGED', 'USER', now());
        RAISE EXCEPTION 'FALLO prueba 2: se acepto actor nulo';
    EXCEPTION WHEN not_null_violation THEN
        RAISE NOTICE 'OK prueba 2: actor_user_id es obligatorio';
    END;

    -- 3. La accion debe pertenecer al contrato de HU-19.
    BEGIN
        INSERT INTO audit_log (id, actor_user_id, action_type, resource_type, occurred_at)
        VALUES (gen_random_uuid(), v_actor, 'PASSWORD_EXPORTED', 'USER', now());
        RAISE EXCEPTION 'FALLO prueba 3: se acepto una accion fuera del contrato';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK prueba 3: action_type invalido se rechaza';
    END;

    -- 4. El tipo de recurso debe pertenecer al contrato de HU-19.
    BEGIN
        INSERT INTO audit_log (id, actor_user_id, action_type, resource_type, resource_id, occurred_at)
        VALUES (gen_random_uuid(), v_actor, 'ROLE_CHANGED', 'SERVICE', v_resource, now());
        RAISE EXCEPTION 'FALLO prueba 4: se acepto un resource_type fuera del contrato';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK prueba 4: resource_type invalido se rechaza';
    END;

    -- 5. El actor debe existir en app_user.
    BEGIN
        INSERT INTO audit_log (id, actor_user_id, action_type, resource_type, resource_id, occurred_at)
        VALUES (gen_random_uuid(), gen_random_uuid(), 'ROLE_CHANGED', 'USER', v_resource, now());
        RAISE EXCEPTION 'FALLO prueba 5: se acepto un actor inexistente';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 5: la FK rechaza un actor inexistente';
    END;

    -- 6. La bitacora no se puede modificar.
    BEGIN
        UPDATE audit_log SET action_type = 'BOOKING_CREATED' WHERE id = v_audit;
        RAISE EXCEPTION 'FALLO prueba 6: se modifico una entrada de auditoria';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK prueba 6: UPDATE es rechazado por el trigger de inmutabilidad';
    END;

    -- 7. La bitacora no se puede eliminar.
    BEGIN
        DELETE FROM audit_log WHERE id = v_audit;
        RAISE EXCEPTION 'FALLO prueba 7: se elimino una entrada de auditoria';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK prueba 7: DELETE es rechazado por el trigger de inmutabilidad';
    END;

    -- 8. La bitacora no se puede truncar.
    BEGIN
        TRUNCATE TABLE audit_log;
        RAISE EXCEPTION 'FALLO prueba 8: se trunco la bitacora';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK prueba 8: TRUNCATE es rechazado por el trigger de inmutabilidad';
    END;

    -- 9. La FK conserva la evidencia: no se puede eliminar al actor auditado.
    BEGIN
        DELETE FROM app_user WHERE id = v_actor;
        RAISE EXCEPTION 'FALLO prueba 9: se elimino el actor auditado';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK prueba 9: el actor auditado no se elimina en cascada';
    END;

    -- 10. Existen los dos indices definidos para las consultas de HU-19.
    SELECT COUNT(*) INTO v_total
    FROM pg_indexes
    WHERE schemaname = current_schema()
      AND tablename = 'audit_log'
      AND indexname IN ('ix_audit_log_occurred_at_id', 'ix_audit_log_actor_occurred_at');

    IF v_total <> 2 THEN
        RAISE EXCEPTION 'FALLO prueba 10: se esperaban 2 indices y se encontraron %', v_total;
    END IF;
    RAISE NOTICE 'OK prueba 10: indices de fecha y actor existen';

    RAISE NOTICE 'Todas las pruebas de integridad de HU-19 pasaron';
END
$$;

ROLLBACK;
