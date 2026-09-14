CREATE TABLE organization (
    id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    tax_id VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT uk_organization_tax_id UNIQUE (tax_id),
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- Unicidad de razon social insensible a mayusculas. El servicio normaliza ademas
-- los espacios antes de persistir, de modo que el indice compara valores ya limpios.
CREATE UNIQUE INDEX uk_organization_name ON organization (lower(name));
