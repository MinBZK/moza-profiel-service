-- V4: vervang te_verwijderen_op (een geplande verwijderdatum, auto-cleared bij gebruik) door
-- verwijderd_op (een soft-delete-tijdstip).
--
-- Bestaande waarden gaan zonder backfill verloren: een reeds verstreken geplande verwijdering
-- wordt weer actief, en een lopende afteltermijn vervalt. Dat heeft nu geen praktisch effect
-- (nog geen productiedata), maar is een bewuste eenmalige keuze voor dit veld, geen precedent
-- voor toekomstige migraties — normaal hoort een kolomwijziging die data raakt een backfill te
-- hebben.

DROP INDEX idx_voorkeur_retention;
DROP INDEX idx_contactgegeven_retention;

ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op;
ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE voorkeur ADD COLUMN verwijderd_op timestamptz NULL;

ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op;
ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE contactgegeven ADD COLUMN verwijderd_op timestamptz NULL;

-- Retentiescheduler filtert op COALESCE(last_used_at, created_at); de index moet dus op die
-- uitdrukking staan, niet los op last_used_at (dat hielp de query niet).
CREATE INDEX idx_voorkeur_retentie ON voorkeur (COALESCE(last_used_at, created_at))
    WHERE verwijderd_op IS NULL;
CREATE INDEX idx_contactgegeven_retentie ON contactgegeven (COALESCE(last_used_at, created_at))
    WHERE verwijderd_op IS NULL;

-- Partieel maken is noodzakelijk voor PartijService.addContactgegeven/addVoorkeur: bij een
-- herhaalde toevoeging moet een nieuwe rij aangemaakt kunnen worden in plaats van de
-- zachtverwijderde rij te moeten hergebruiken (die zou anders de sleutel nog bezet houden).
-- Deze koppeling tussen migratie en servicecode is alleen hier gedocumenteerd; wijzig ze samen.
ALTER TABLE contactgegeven DROP CONSTRAINT uk_contactgegeven_dedup;
CREATE UNIQUE INDEX uk_contactgegeven_dedup ON contactgegeven (partij_id, type, waarde)
    WHERE verwijderd_op IS NULL;

DROP INDEX contactgegeven_default_per_type;
CREATE UNIQUE INDEX contactgegeven_default_per_type ON contactgegeven (partij_id, type)
    WHERE is_default = true AND verwijderd_op IS NULL;
