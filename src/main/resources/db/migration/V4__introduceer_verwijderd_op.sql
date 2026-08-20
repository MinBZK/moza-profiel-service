-- Introduceert verwijderd_op (een soft-delete-tijdstip) door het hele domeinmodel: vervangt
-- te_verwijderen_op (een geplande verwijderdatum, auto-cleared bij gebruik) op Voorkeur en
-- Contactgegeven, en voegt de kolom nieuw toe aan Partij en Identificatie. Wanneer een Partij
-- geen enkele actieve Voorkeur/Contactgegeven meer heeft, wordt de Partij zelf ook soft-deleted
-- (zie PartijService.deleteLegePartij) en dus niet meer opvraagbaar.

DROP INDEX idx_voorkeur_retention;
DROP INDEX idx_contactgegeven_retention;

ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op;
ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE voorkeur ADD COLUMN verwijderd_op timestamptz NULL;

ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op;
ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE contactgegeven ADD COLUMN verwijderd_op timestamptz NULL;

-- Retentiescheduler filtert op COALESCE(last_used_at, created_at); de index moet dus op die
-- uitdrukking staan, niet los op last_used_at.
CREATE INDEX idx_voorkeur_retentie ON voorkeur (COALESCE(last_used_at, created_at))
    WHERE verwijderd_op IS NULL;
CREATE INDEX idx_contactgegeven_retentie ON contactgegeven (COALESCE(last_used_at, created_at))
    WHERE verwijderd_op IS NULL;

-- Partieel maken is noodzakelijk voor PartijService.addContactgegeven: bij een herhaalde
-- toevoeging moet een nieuwe rij aangemaakt kunnen worden in plaats van de rij met de soft
-- delete te moeten hergebruiken (die zou anders de sleutel nog bezet houden).
-- Deze koppeling tussen migratie en servicecode is alleen hier gedocumenteerd; wijzig ze samen.
ALTER TABLE contactgegeven DROP CONSTRAINT uk_contactgegeven_dedup;
CREATE UNIQUE INDEX uk_contactgegeven_dedup ON contactgegeven (partij_id, type, waarde)
    WHERE verwijderd_op IS NULL;

DROP INDEX contactgegeven_default_per_type;
CREATE UNIQUE INDEX contactgegeven_default_per_type ON contactgegeven (partij_id, type)
    WHERE is_default = true AND verwijderd_op IS NULL;

ALTER TABLE partij ADD COLUMN verwijderd_op timestamptz NULL;

-- Identificatie wordt met haar Partij mee soft-deletable (deleteLegePartij zet verwijderd_op ook
-- op alle identificaties van een partij die wordt gecascadet). uk_identificatie wordt daarom
-- partieel, net als uk_contactgegeven_dedup hierboven: een latere toevoeging voor een (type,
-- nummer) waarvan de vorige Partij inmiddels leeg is, moet een geheel nieuwe Partij +
-- Identificatie kunnen aanmaken zonder tegen de oude, soft-deleted rij te botsen.
ALTER TABLE identificatie ADD COLUMN verwijderd_op timestamptz NULL;
ALTER TABLE identificatie DROP CONSTRAINT uk_identificatie;
CREATE UNIQUE INDEX uk_identificatie ON identificatie (identificatie_type, identificatie_nummer)
    WHERE verwijderd_op IS NULL;

-- Aanvullende constraint: voorkomt dat één Partij twee actieve identificaties van hetzelfde type
-- heeft (bv. twee actieve BSN's). Veilig te combineren met "geen resurrection" (findOrCreatePartij):
-- een nieuwe Partij krijgt altijd een nieuw UUID, dus deze index kan een legitieme nieuwe Partij
-- nooit blokkeren.
CREATE UNIQUE INDEX uk_identificatie_per_partij ON identificatie (partij_id, identificatie_type)
    WHERE verwijderd_op IS NULL;
