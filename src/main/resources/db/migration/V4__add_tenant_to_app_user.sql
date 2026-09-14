-- Convierte app_user en una tabla multi-tenant: cada usuario pertenece a una organizacion.
--
-- ATENCION: esta migracion elimina los usuarios preexistentes. No hay forma de inferir a que
-- organizacion pertenecen, porque hasta ahora las organizaciones no existian. Es seguro
-- unicamente porque ningun entorno tiene datos reales todavia. Si algun entorno llegara a
-- tenerlos, sustituir el DELETE por un UPDATE que los asigne a una organizacion de migracion.

ALTER TABLE app_user ADD COLUMN tenant_id UUID;

DELETE FROM app_user WHERE tenant_id IS NULL;

ALTER TABLE app_user ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE app_user ADD CONSTRAINT fk_app_user_organization
    FOREIGN KEY (tenant_id) REFERENCES organization (id);

-- El mismo correo puede repetirse en organizaciones distintas: son negocios sin relacion entre si.
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS uk_app_user_email;
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_tenant_email UNIQUE (tenant_id, email);
