-- HU-22: registro de profesionales.
-- Un profesional es una cuenta de app_user con rol PROFESSIONAL mas un perfil con su especialidad.
-- El perfil comparte la clave primaria con la cuenta (relacion 1 a 1): el id del profesional es el
-- mismo UUID que el token JWT lleva en "sub", que es el valor que OwnershipGuard compara al
-- proteger los recursos propios (por ejemplo, la agenda).
CREATE TABLE profesionales (
    id UUID NOT NULL,
    especialidad VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_profesionales PRIMARY KEY (id),
    -- Sin ON DELETE CASCADE: una cuenta con perfil no se elimina, se deshabilita (enabled = FALSE).
    CONSTRAINT fk_profesionales_app_user FOREIGN KEY (id) REFERENCES app_user (id),
    CONSTRAINT ck_profesionales_especialidad CHECK (length(btrim(especialidad)) > 0)
);