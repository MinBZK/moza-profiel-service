-- V4: teVerwijderenOp wordt verwijderdOp en krijgt de betekenis van een soft-delete
-- markering (altijd het moment van verwijdering, nooit meer een datum in de toekomst).
-- De automatisch-vlag vervalt: er is geen respijtperiode meer die teruggedraaid kan worden.

ALTER TABLE voorkeur RENAME COLUMN te_verwijderen_op TO verwijderd_op;
ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op_automatisch;

ALTER TABLE contactgegeven RENAME COLUMN te_verwijderen_op TO verwijderd_op;
ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op_automatisch;

-- Onder de oude betekenis was een gezette waarde altijd een datum in de toekomst
-- (een geplande verwijdering, nooit een reeds voltrokken verwijdering) — de applicatie
-- stond nooit een verleden datum toe. Onder verwijderdOp betekent een gezette waarde nu
-- direct "verborgen voor GET". Zonder deze stap zou elke rij met een nog geplande
-- verwijdering bij deploy meteen (jaren te vroeg) onzichtbaar worden.
UPDATE voorkeur SET verwijderd_op = NULL WHERE verwijderd_op IS NOT NULL;
UPDATE contactgegeven SET verwijderd_op = NULL WHERE verwijderd_op IS NOT NULL;
