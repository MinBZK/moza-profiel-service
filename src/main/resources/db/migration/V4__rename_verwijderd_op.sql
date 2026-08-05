-- V4: te_verwijderen_op (een geplande verwijderdatum, auto-cleared bij gebruik) wordt
-- vervangen door verwijderd_op (een soft-delete-tijdstip).

-- De partial indexes op te_verwijderen_op worden niet teruggezet: RetentieScheduler is hun
-- enige gebruiker, filtert op COALESCE(last_used_at, created_at) (niet op last_used_at
-- zelf) en draait maar één keer per dag, dus een index hielp hier toch al nauwelijks.
DROP INDEX idx_voorkeur_retention;
DROP INDEX idx_contactgegeven_retention;

ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op;
ALTER TABLE voorkeur DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE voorkeur ADD COLUMN verwijderd_op timestamptz NULL;

ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op;
ALTER TABLE contactgegeven DROP COLUMN te_verwijderen_op_automatisch;
ALTER TABLE contactgegeven ADD COLUMN verwijderd_op timestamptz NULL;
