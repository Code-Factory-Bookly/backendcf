-- HU-02: catalogo de servicios (sin perfil de organizacion ni tabla de especialidades separada,
-- por decision de alcance de Arquitectura-BD). Idempotente, siguiendo la convencion de 001 y 002.

CREATE TABLE IF NOT EXISTS servicios (
    id UUID NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    descripcion VARCHAR(500),
    categoria VARCHAR(100) NOT NULL,
    duracion_minutos INTEGER NOT NULL,
    precio NUMERIC(12, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_servicios PRIMARY KEY (id),
    CONSTRAINT ck_servicios_estado CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_servicios_duracion CHECK (duracion_minutos > 0 AND duracion_minutos <= 480),
    CONSTRAINT ck_servicios_precio CHECK (precio > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_servicios_nombre ON servicios (lower(nombre));
