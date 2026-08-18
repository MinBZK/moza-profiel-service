-- Maakt Partij soft-deletable. Wanneer een Partij geen enkele actieve Voorkeur/Contactgegeven
-- meer heeft, wordt de Partij zelf ook soft-deleted (zie PartijService.deleteLegePartij) en
-- dus niet meer opvraagbaar.
--
-- uk_identificatie vervalt: een latere toevoeging voor een (type, nummer) waarvan de vorige
-- Partij soft-deleted is, moet een geheel nieuwe Partij + Identificatie kunnen aanmaken
-- (nieuwe UUID), maar Postgres kan een partial index niet conditioneren op een kolom uit een
-- andere tabel (partij.verwijderd_op) zonder verwijderd_op ook op identificatie te dupliceren.
-- In plaats daarvan wordt de uniciteit voortaan uitsluitend in applicatiecode afgedwongen
-- (findOrCreatePartij, via het al gefilterde Partij.findByIdentificatie) — hetzelfde
-- geaccepteerde patroon als de Voorkeur-invariant in PartijService.addVoorkeur, die ook geen
-- DB-index heeft. De kolommen die de constraint impliciet indexeerde blijven een gewone
-- (niet-unieke) index nodig, want findByIdentificatie filtert hier op elke aanroep op.
--
-- Geen backfill: een partij die al vóór deze migratie geen actieve voorkeuren/contactgegevens
-- meer had, wordt niet met terugwerkende kracht soft-deleted (de cascade in deleteLegePartij
-- draait alleen op een verwijder-gebeurtenis, en zo'n partij heeft er geen meer). Zulke partijen
-- blijven dus 200 met lege arrays teruggeven, terwijl een partij die na deze migratie leegraakt
-- 404 wordt. Net als bij V4 (verwijderd_op invoeren voor Voorkeur/Contactgegeven) is dat nu geen
-- praktisch probleem (nog geen productiedata) en een bewuste eenmalige keuze, geen precedent voor
-- toekomstige migraties — normaal hoort een kolomwijziging die data raakt een backfill te hebben.

ALTER TABLE partij ADD COLUMN verwijderd_op timestamptz NULL;

ALTER TABLE identificatie DROP CONSTRAINT uk_identificatie;
CREATE INDEX idx_identificatie_type_nummer ON identificatie (identificatie_type, identificatie_nummer);
