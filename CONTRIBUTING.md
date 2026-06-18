# Bijdragen aan moza-profiel-service

Bedankt voor je interesse in deze repository. Bekijk eerst de overkoepelende richtlijnen in het [MijnOverheidZakelijk](https://github.com/MinBZK/MijnOverheidZakelijk/blob/main/CONTRIBUTING.md) projectrepo; die zijn leidend.

## Lokale conventies voor deze repo

- **Branching**: feature branches off `main`, PR voor merge. Tijdens initiële buildout van een greenfield repo kan ook direct op `main` worden gewerkt.
- **Commits**: korte, beschrijvende messages in het Nederlands of Engels.
- **Tests**: `./mvnw verify` moet groen draaien. Unit + component (cucumber) + fuzz (jazzer). JaCoCo-drempel is 50% line en 50% branch.
- **API Design Rules**: nieuwe of gewijzigde endpoints moeten door de Spectral lint heen tegen de NLGov ADR ruleset (`https://static.developer.overheid.nl/adr/ruleset.yaml`). Zie [`audit/consolidated-findings.md`](../profiel-service/audit/consolidated-findings.md) voor de bekende bevindingen.
- **Schema-changes**: voeg een nieuwe Flyway migratie toe in `src/main/resources/db/migration/`. SQL moet zowel op H2 (test profile) als PostgreSQL (dev/prod) draaien.
- **Secrets**: nooit committen. Lokale dev-config in `src/main/resources/application-dev.properties` (gitignored). Productie-secrets via de deployment-repo `moz/profiel-service/config/`.

## Issues en bugs

Open issues in de [MijnOverheidZakelijk](https://github.com/MinBZK/MijnOverheidZakelijk/issues) tracker met label `profiel-service`.

## Beveiligingsmeldingen

Zie de Security Policy in `SUPPORT.md` of meld kwetsbaarheden conform `security.txt` op het MOZa-platform.
