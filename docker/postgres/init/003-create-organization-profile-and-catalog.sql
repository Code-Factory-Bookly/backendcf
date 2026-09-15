-- HU-02: perfil de la organizacion y catalogo de especialidades y servicios.
-- Idempotente, siguiendo la convencion de 001 y 002.

CREATE TABLE IF NOT EXISTS organization_profile (
    id UUID NOT NULL,
    commercial_name VARCHAR(200) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    address VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_organization_profile PRIMARY KEY (id)
);

-- La plataforma atiende a una sola organizacion: el perfil admite exactamente una fila.
CREATE UNIQUE INDEX IF NOT EXISTS uk_organization_profile_singleton
    ON organization_profile ((true));

CREATE TABLE IF NOT EXISTS specialty (
    id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_specialty PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_specialty_name ON specialty (lower(name));

CREATE TABLE IF NOT EXISTS service_offering (
    id UUID NOT NULL,
    specialty_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    duration_minutes INTEGER NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_service_offering PRIMARY KEY (id),
    CONSTRAINT fk_service_offering_specialty
        FOREIGN KEY (specialty_id) REFERENCES specialty (id),
    CONSTRAINT ck_service_offering_duration CHECK (duration_minutes BETWEEN 5 AND 480),
    CONSTRAINT ck_service_offering_price CHECK (price >= 0)
);

-- Nombre unico dentro de cada especialidad. Al empezar por specialty_id, este indice tambien
-- sirve para filtrar el catalogo por especialidad, asi que no se crea uno aparte.
CREATE UNIQUE INDEX IF NOT EXISTS uk_service_offering_specialty_name
    ON service_offering (specialty_id, lower(name));
