-- V4: te_verwijderen_op (een geplande verwijderdatum, auto-cleared bij gebruik) wordt
-- vervangen door verwijderd_op (een soft-delete-tijdstip).

DROP INDEX idx_voorkeur_retention;
DROP INDEX idx_contactgegeven_retention;

ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op;
ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE voorkeur ADD COLUMN verwijderd_op timestamptz NULL;

ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op;
ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE contactgegeven ADD COLUMN verwijderd_op timestamptz NULL;
