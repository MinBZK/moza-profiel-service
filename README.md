# Profiel Service
![Project Development Status](https://img.shields.io/badge/life_cycle-development-yellow)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/MinBZK/moza-profiel-service/badge)](https://scorecard.dev/viewer/?uri=github.com/MinBZK/moza-profiel-service)

De Profiel Service stelt burgers en ondernemers in staat om op één vertrouwde plek hun contactgegevens en communicatievoorkeuren te beheren, en biedt overheidsinstanties via federatieve koppelingen veilige, actuele en herbruikbare profielinformatie voor persoonlijke en efficiënte dienstverlening.

Documentatie over de Profiel Service is te vinden op [de documentatie website van MijnOverheidZakelijk](https://docs.mijnoverheidzakelijk.nl/workspace/documentation/Profiel%20Service).

## Status

Levenscyclus: **development** (zie `publiccode.yml`). De service draait in een POC-omgeving en wordt voorbereid op landing op de Logius Private Cloud (LPC).


## API

- **Base path**: `/api/profielservice/v1`
- **OpenAPI spec**: `/openapi.json`
- **Swagger UI**: `/docs`
- **Health en metrics**: op aparte management-port `9090` onder `/q/health` en `/q/metrics` (niet via de publieke port).

De API volgt de [NL GOV API Design Rules 2.1.0](https://gitdocumentatie.logius.nl/publicatie/api/adr/2.1.0/). Foutmeldingen volgen RFC 9457 (`application/problem+json`).

## Lokaal draaien

Vereisten:
- Java 21
- Maven (of de meegeleverde wrapper `./mvnw`)
- PostgreSQL (of gebruik H2 via het `test` profile)

```bash
# Database opstarten (zie docker-compose.yml)
docker compose up -d

# Dev-modus (live reload, http://localhost:8080)
./mvnw quarkus:dev

# Tests (gebruikt H2)
./mvnw verify
```

## Configuratie

Lokale ontwikkel-secrets horen in een gitignored `src/main/resources/application-dev.properties`:

- `notifynl.emailverificatie.api-key`, `template-id`, `reference` — van https://admin.notifynl.nl/, vraag het team voor toegang.
- `quarkus.datasource.*` — alleen nodig als je geen `docker compose` gebruikt.

Productie-configuratie staat in de deployment-repo.

## Quarkus

Dit project draait op Quarkus. Meer informatie hierover staat in [quarkus.md](quarkus.md).

## Circuit breaker voor de Verificatie-service API

Bij herhaalde fouten in de communicatie met de externe verificatie-service (bijvoorbeeld door netwerkproblemen of uitval) wordt de circuit breaker actief. Na een configureerbaar aantal mislukte aanroepen gaat het circuit open: nieuwe verzoeken worden direct afgewezen zonder dat er opnieuw een verbinding wordt geprobeerd. Dit voorkomt dat de applicatie vastloopt op trage of niet-reagerende externe diensten. Na een wachttijd gaat het circuit in half-open toestand en worden nieuwe aanroepen opnieuw toegestaan om te testen of de externe dienst hersteld is.

De circuit breaker is **gedeeld** tussen de twee aanroepen naar de verificatie-service (`requestEmailVerificationCode` en `verifieerEmail`). Dit betekent dat herhaalde fouten op het ene endpoint ook het andere endpoint beschermen: als de verificatie-service voor de ene aanroep niet bereikbaar is, is dat hoogstwaarschijnlijk voor de andere ook het geval. De gedeelde circuit breaker wordt beheerd via `VerificatieServiceGuard`.

### Circuit breaker instellingen

De circuit breaker wordt geconfigureerd via de volgende properties in `application.properties`. De waarden in de code gelden als standaardwaarden en kunnen per omgeving worden overschreven.

- `verificatie-service.circuit-breaker.request-volume-threshold`: Minimum aantal aanroepen binnen het meetvenster voordat het circuit kan openen (standaard `5`).
- `verificatie-service.circuit-breaker.failure-ratio`: Drempelwaarde voor het percentage mislukte aanroepen waarboven het circuit opent (standaard `1.0` — circuit opent alleen bij volledige uitval).
- `verificatie-service.circuit-breaker.delay`: Wachttijd in seconden in de open toestand voordat het circuit half-open gaat (standaard `30`).
- `verificatie-service.circuit-breaker.success-threshold`: Aantal opeenvolgende successen in half-open toestand dat nodig is om het circuit te sluiten (standaard `2`).

## Contracttesting

De Profiel Service maakt gebruik van contracttesting om te waarborgen dat wijzigingen aan de API consumenten niet ongemerkt breken.

### Hoe het werkt

- **OpenAPI-schemavalidatie**: elke integratietest valideert automatisch dat verzoeken en antwoorden overeenkomen met de live OpenAPI-specificatie van de service (`/q/openapi`).
- **Pact-providerverificatie**: pact-bestanden (JSON) in `src/test/resources/pacts/` beschrijven de verwachte contracten. De provider test verifieert dat de service hieraan voldoet. Het huidige bestand `moza-profiel-service.json` is een zelftestcontract van de provider zelf.

### Contracten bijdragen als consument

Ben je consument van de Profiel Service API en wil je een contract bijdragen? Neem dan contact op met het team om dit samen te bespreken. We stellen dan samen een pact-bestand op dat de verwachtingen van jouw toepassing beschrijft.

Een Pact Broker is een mogelijke toekomstige stap, afhankelijk van de behoefte van het team.