-- Maakt Partij soft-deletable. Wanneer een Partij geen enkele actieve Voorkeur/Contactgegeven
-- meer heeft, wordt de Partij zelf ook soft-deleted (zie PartijService.deleteLegePartij) en
-- dus niet meer opvraagbaar.
--
-- Geen backfill: een partij die al vóór deze migratie geen actieve voorkeuren/contactgegevens
-- meer had, wordt niet met terugwerkende kracht soft-deleted (de cascade in deleteLegePartij
-- draait alleen op een verwijder-gebeurtenis, en zo'n partij heeft er geen meer). Zulke partijen
-- blijven dus 200 met lege arrays teruggeven, terwijl een partij die na deze migratie leegraakt
-- 404 wordt. Net als bij V4 (verwijderd_op invoeren voor Voorkeur/Contactgegeven) is dat nu geen
-- praktisch probleem (nog geen productiedata) en een bewuste eenmalige keuze, geen precedent voor
-- toekomstige migraties — normaal hoort een kolomwijziging die data raakt een backfill te hebben.
ALTER TABLE partij ADD COLUMN verwijderd_op timestamptz NULL;

-- Identificatie wordt met haar Partij mee soft-deletable (deleteLegePartij zet verwijerd_op ook
-- op alle identificaties van een partij die wordt gecascadet). uk_identificatie wordt daarom
-- partieel, net als uk_contactgegeven_dedup (zie V4): een latere toevoeging voor een (type,
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
