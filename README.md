# Profiel Service
![Project Pre-Alpha Status](https://img.shields.io/badge/life_cycle-pre_alpha-red)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/MinBZK/moza-profiel-service/badge)](https://scorecard.dev/viewer/?uri=github.com/MinBZK/moza-profiel-service)

De Profiel Service stelt burgers en ondernemers in staat om op één vertrouwde plek hun contactgegevens en communicatievoorkeuren te beheren, en biedt overheidsinstanties via federatieve koppelingen veilige, actuele en herbruikbare profielinformatie voor persoonlijke en efficiënte dienstverlening.

Documentatie over de Profiel Service is te vinden op [de documentatie website van MijnOverheidZakelijk](https://docs.mijnoverheidzakelijk.nl/workspace/documentation/Profiel%20Service).

## Quarkus
Dit project draait op Quarkus. Meer informatie hierover staat in [quarkus.md](quarkus.md)

## Configuratie
In de application.properties file staat een notifyNl gedeelte, deze moeten worden gevuld met information van https://admin.notifynl.nl/ vraag de developers van dit project voor deze gegevens.

Plaats deze gegevens vervolgens NIET in de `application.properties` file maar maak een file `/src/main/resources/application-dev.properties` aan en zet de values hier in. Deze file staat in de `.gitignore`.