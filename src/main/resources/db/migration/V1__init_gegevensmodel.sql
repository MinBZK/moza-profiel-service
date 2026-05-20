-- V1: Initieel gegevensmodel voor de Profiel Service.
-- Komt overeen met Docs/structurizr/profielservicedocs/08-data.md.
-- Column-level encryption van identificatie_nummer en contactgegeven.waarde,
-- en BSNk-pseudonimisering, volgen in een latere migratie.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE partij (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE identificatie (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    partij_id uuid NOT NULL REFERENCES partij(id) ON DELETE CASCADE,
    identificatie_type text NOT NULL,
    identificatie_nummer text NOT NULL,
    CONSTRAINT uk_identificatie UNIQUE (identificatie_type, identificatie_nummer)
);

CREATE TABLE contactgegeven (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    partij_id uuid NOT NULL REFERENCES partij(id) ON DELETE CASCADE,
    type text NOT NULL,
    waarde text NOT NULL,
    is_geverifieerd boolean NOT NULL DEFAULT false,
    geverifieerd_at timestamptz NULL,
    verificatie_referentie_id text NULL,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_updated timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NULL,
    CONSTRAINT uk_contactgegeven_dedup UNIQUE (partij_id, type, waarde)
);
CREATE UNIQUE INDEX contactgegeven_default_per_type
    ON contactgegeven (partij_id, type) WHERE is_default = true;

CREATE TABLE voorkeur (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    partij_id uuid NOT NULL REFERENCES partij(id) ON DELETE CASCADE,
    voorkeur_type text NOT NULL,
    waarde text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_updated timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NULL
);

CREATE TABLE dienstverlener (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    naam text NOT NULL,
    beschrijving text NULL
);
-- Case-insensitief uniek: matcht de lookup in DienstverlenerService (lower(naam) = lower(?)).
CREATE UNIQUE INDEX uk_dienstverlener_naam_ci ON dienstverlener (lower(naam));

CREATE TABLE dienst (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    naam text NOT NULL,
    beschrijving text NULL
);

CREATE TABLE dienstverlener_dienst (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    dienstverlener_id uuid NOT NULL REFERENCES dienstverlener(id) ON DELETE CASCADE,
    dienst_id uuid NULL REFERENCES dienst(id) ON DELETE SET NULL,
    CONSTRAINT uk_dienstverlener_dienst UNIQUE (dienstverlener_id, dienst_id)
);
-- Partial unique index voor DV-brede rijen (dienst_id IS NULL). De gewone UNIQUE-constraint
-- hierboven dedupliceert geen NULLs (SQL: NULL <> NULL), dus zonder deze index zou een
-- dienstverlener meerdere "scope op heel-de-DV"-koppelrijen kunnen accumuleren.
CREATE UNIQUE INDEX uk_dvdienst_dv_broad ON dienstverlener_dienst (dienstverlener_id)
    WHERE dienst_id IS NULL;

CREATE TABLE scope_contactgegeven (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contactgegeven_id uuid NOT NULL REFERENCES contactgegeven(id) ON DELETE CASCADE,
    dienstverlener_dienst_id uuid NOT NULL REFERENCES dienstverlener_dienst(id),
    CONSTRAINT uk_scope_contactgegeven UNIQUE (contactgegeven_id, dienstverlener_dienst_id)
);

CREATE TABLE scope_voorkeur (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    voorkeur_id uuid NOT NULL REFERENCES voorkeur(id) ON DELETE CASCADE,
    dienstverlener_dienst_id uuid NOT NULL REFERENCES dienstverlener_dienst(id),
    CONSTRAINT uk_scope_voorkeur UNIQUE (voorkeur_id, dienstverlener_dienst_id)
);

-- Indexes op FK-kolommen. PostgreSQL indexeert FKs niet automatisch; deze paden
-- worden hot bij elke partij-lookup en bij scope-gefilterde reads.
CREATE INDEX idx_identificatie_partij ON identificatie (partij_id);
CREATE INDEX idx_voorkeur_partij ON voorkeur (partij_id);
CREATE INDEX idx_scope_contactgegeven_contactgegeven ON scope_contactgegeven (contactgegeven_id);
CREATE INDEX idx_scope_contactgegeven_link ON scope_contactgegeven (dienstverlener_dienst_id);
CREATE INDEX idx_scope_voorkeur_voorkeur ON scope_voorkeur (voorkeur_id);
CREATE INDEX idx_scope_voorkeur_link ON scope_voorkeur (dienstverlener_dienst_id);
CREATE INDEX idx_dienstverlener_dienst_dv ON dienstverlener_dienst (dienstverlener_id);
CREATE INDEX idx_dienstverlener_dienst_dienst ON dienstverlener_dienst (dienst_id);
-- contactgegeven (partij_id) wordt al gedekt door de UNIQUE (partij_id, type, waarde) index.
