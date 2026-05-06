# Profiel Service
![Project Pre-Alpha Status](https://img.shields.io/badge/life_cycle-pre_alpha-red)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/MinBZK/moza-profiel-service/badge)](https://scorecard.dev/viewer/?uri=github.com/MinBZK/moza-profiel-service)

De Profiel Service stelt burgers en ondernemers in staat om op één vertrouwde plek hun contactgegevens en communicatievoorkeuren te beheren, en biedt overheidsinstanties via federatieve koppelingen veilige, actuele en herbruikbare profielinformatie voor persoonlijke en efficiënte dienstverlening.

Documentatie over de Profiel Service is te vinden op [de documentatie website van MijnOverheidZakelijk](https://docs.mijnoverheidzakelijk.nl/workspace/documentation/Profiel%20Service).

## Quarkus
Dit project draait op Quarkus. Meer informatie hierover staat in [quarkus.md](quarkus.md)

In de application.properties file staat een notifyNl gedeelte, deze moeten worden gevuld met information van https://admin.notifynl.nl/ vraag de developers van dit project voor deze gegevens.

Plaats deze gegevens vervolgens NIET in de `application.properties` file maar maak een file `/src/main/resources/application-dev.properties` aan en zet de values hier in. Deze file staat in de `.gitignore`.

## Circuit breaker voor de Verificatie-service API

Bij herhaalde fouten in de communicatie met de externe verificatie-service (bijvoorbeeld door netwerkproblemen of uitval) wordt de circuit breaker actief. Na een configureerbaar aantal mislukte aanroepen gaat het circuit open: nieuwe verzoeken worden direct afgewezen zonder dat er opnieuw een verbinding wordt geprobeerd. Dit voorkomt dat de applicatie vastloopt op trage of niet-reagerende externe diensten. Na een wachttijd gaat het circuit in half-open toestand en worden nieuwe aanroepen opnieuw toegestaan om te testen of de externe dienst hersteld is.

De circuit breaker is **gedeeld** tussen de twee aanroepen naar de verificatie-service (`requestEmailVerificationCode` en `verifieerEmail`). Dit betekent dat herhaalde fouten op het ene endpoint ook het andere endpoint beschermen: als de verificatie-service voor de ene aanroep niet bereikbaar is, is dat hoogstwaarschijnlijk voor de andere ook het geval. De gedeelde circuit breaker wordt beheerd via `VerificatieServiceGuard`.

### Circuit breaker instellingen

De circuit breaker wordt geconfigureerd via de volgende properties in `application.properties`. De waarden in de code gelden als standaardwaarden en kunnen per omgeving worden overschreven.

- `verificatie-service.circuit-breaker.request-volume-threshold`: Minimum aantal aanroepen binnen het meetvenster voordat het circuit kan openen (standaard `5`).
- `verificatie-service.circuit-breaker.failure-ratio`: Drempelwaarde voor het percentage mislukte aanroepen waarboven het circuit opent (standaard `1.0` — circuit opent alleen bij volledige uitval).
- `verificatie-service.circuit-breaker.delay`: Wachttijd in seconden in de open toestand voordat het circuit half-open gaat (standaard `30`).
- `verificatie-service.circuit-breaker.success-threshold`: Aantal opeenvolgende successen in half-open toestand dat nodig is om het circuit te sluiten (standaard `2`).