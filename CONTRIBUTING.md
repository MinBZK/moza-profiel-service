# Bijdragen aan moza-profiel-service

Bedankt voor je interesse in deze repository. Bekijk eerst de overkoepelende richtlijnen in het [MijnOverheidZakelijk](https://github.com/MinBZK/MijnOverheidZakelijk/blob/main/CONTRIBUTING.md) projectrepo; die zijn leidend.

## Lokale conventies voor deze repo

- **Branching**: feature branches off `main`, PR voor merge. Nooit direct op `main` werken.
- **Commits**: korte, beschrijvende messages in het Nederlands of Engels.
- **Tests**: `./mvnw verify` moet groen draaien. Unit + integratie (`@QuarkusTest`) + architectuur (ArchUnit) + fuzz (jazzer) + contract (Pact en OpenAPI-schemavalidatie). JaCoCo-drempel is 85% line en 80% branch.
- **API Design Rules**: controleer een contractwijziging tegen de NLGov ADR-ruleset. Geen CI dwingt dit af, dus draai hem zelf:

  ```bash
  npx @stoplight/spectral-cli lint src/main/resources/META-INF/openapi.yaml \
    --ruleset https://static.developer.overheid.nl/adr/ruleset.yaml
  ```

  Het contract heeft nog 3 errors en 1 warning (stand 2026-08-20). Draai de lint voor en na je wijziging en voeg geen nieuwe bevindingen toe. De bekende afwijkingen: geen `servers`-array (nodig voor de ZAD-previews; `OpenApiMetadataTest.contractAdverteertGeenServers` legt dat vast), OpenAPI 3.1 waar de ruleset 3.0 verwacht, `/openapi.json` niet in het contract gedeclareerd, en `UUID` als schemanaam (volgt uit `schemaMappings`).
- **Schema-changes**: voeg een nieuwe Flyway migratie toe in `src/main/resources/db/migration/`. Tests draaien de migraties tegen een embedded PostgreSQL, dus een fout in een migratie of drift met de entities (`validate`) breekt `./mvnw verify`.
- **Secrets**: nooit committen. Lokale dev-config in `src/main/resources/application-dev.properties` (gitignored). Productie-secrets via de deployment-repo `moz/profiel-service/config/`.

## Issues en bugs

Open issues in de [MijnOverheidZakelijk](https://github.com/MinBZK/MijnOverheidZakelijk/issues) tracker met label `profiel-service`.

## Beveiligingsmeldingen

Zie het beveiligingsbeleid in [`SECURITY.md`](SECURITY.md) voor het verantwoord melden van kwetsbaarheden.
