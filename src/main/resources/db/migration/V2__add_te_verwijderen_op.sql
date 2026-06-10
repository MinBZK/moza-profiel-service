-- V2: Voeg te_verwijderen_op toe aan voorkeur en contactgegeven.
-- Dit veld bepaalt wanneer een record verwijderd mag worden (maximaal 7 jaar na laatste gebruik).

ALTER TABLE voorkeur
    ADD COLUMN te_verwijderen_op timestamptz NULL,
    ADD COLUMN te_verwijderen_op_automatisch BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE contactgegeven
    ADD COLUMN te_verwijderen_op timestamptz NULL,
    ADD COLUMN te_verwijderen_op_automatisch BOOLEAN NOT NULL DEFAULT FALSE;

-- Partiële index voor de retentiescheduler: zoekt records zonder te_verwijderen_op
-- die al lang ongebruikt zijn. De WHERE-conditie sluit al gezette records uit van de index.
CREATE INDEX idx_voorkeur_retention ON voorkeur (last_used_at) WHERE te_verwijderen_op IS NULL;
CREATE INDEX idx_contactgegeven_retention ON contactgegeven (last_used_at) WHERE te_verwijderen_op IS NULL;
