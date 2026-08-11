# ZAD PR-deploy

`deploy.yml` bouwt per Pull Request een container-image en deployt die naar een
ephemeral ZAD-omgeving (`pr-<nummer>`). Bij sluiten van de PR ruimt
`cleanup-preview` de omgeving, het image en het GitHub-environment op. Bij een
push/merge naar `main` deployt `deploy-stable` naar de persistente
`stable`-omgeving.

> **Scope:** ZAD is uitsluitend de **PR-preview-/ontwikkelomgeving**. De bestaande
> POC-deployment op het Standaard Platform (OpenShift TEST, namespace
> `logius-moz-poc`, infra-files op de Logius GitLab) en de landing op LPC blijven
> hierbuiten ongewijzigd — zie
> `Docs/structurizr/profielservicedocs/10-deployment.md`.

Referentie: analoge workflow voor de NMC, zie
[`moza-notificatiemanagementcomponent#749`](https://github.com/MinBZK/moza-notificatiemanagementcomponent/issues/749)
en diens `docs/zad-deploy.md`.

## Hoe ZAD hier werkt

De `RijksICTGilde/zad-actions/deploy` action zet alleen **welk container-image** +
optioneel **`clone-from`** (config erven van een bestaande deployment). De action
zet **geen** app-config (DB-url, wachtwoorden, verificatie-service-url, NotifyNL-
keys). Die config leeft in de deployment zelf, ingesteld in de ZAD **Operations
Manager**. Een PR-deploy erft die via `clone-from feature`.

→ Gevolg: er moet **eenmalig** een base-deployment met de juiste env bestaan,
anders start de app zonder DB en gaat de deploy rood (`/q/health/ready` faalt).

## Verschillen met de NMC-variant

Deze workflow is overgenomen van de NMC (`moza-notificatiemanagementcomponent`),
met een aantal aanpassingen specifiek voor de Profielservice:

### Management-poort en health-check

`application.properties` zet `quarkus.management.enabled=true` met
`quarkus.management.port=9090`: `/q/health/ready` en `/q/metrics` luisteren
standaard op een aparte poort, niet de publieke HTTP-poort. `quarkus.management.*`
is **build-time fixed** (niet via een runtime env var op de deployment te
overschrijven).

In plaats daarvan zet de build in `deploy.yml` de Maven-flag
`-Dquarkus.smallrye-health.management.enabled=false`. Dat is hetzelfde patroon dat
`application.properties` al gebruikt om OpenAPI/Swagger UI van de management-poort
af te houden (`quarkus.swagger-ui.management.enabled=false`), maar dan voor health.
Resultaat: `/q/health/ready` (inclusief de echte datasource-check) draait op de
publieke poort, `/q/metrics` blijft op de management-poort. Omdat dit image
uitsluitend op ZAD draait — nooit op het Standaard Platform — is dit veilig voor
zowel de `pr-<n>`- als de `stable`-deploy.

### Gedeelde database + Quartz

Alle PR-previews delen via `clone-from: feature` dezelfde managed Postgres (zoals
bij de NMC). Voor de Profielservice is dat risicovoller dan bij de NMC: de
retentie-scheduler draait op geclusterde Quartz-tabellen
(`quarkus.quartz.clustered=true`) en verwijdert data volgens
`retentie.scheduler.cron`. Met meerdere gelijktijdige PR-previews op dezelfde DB
zou die job data van een andere, nog open PR-preview kunnen opruimen.

Daarom staat `QUARKUS_SCHEDULER_ENABLED=false` op de `feature`-base-deployment (zie
env-var-tabel hieronder) — de scheduler draait niet op previews. Dit is een
bewuste afwijking; als ZAD ooit een database/schema per deployment kan leveren, kan
dit heroverwogen worden.

### Flyway

Anders dan bij de NMC hoeft `QUARKUS_FLYWAY_MIGRATE_AT_START` **niet** als env var
gezet te worden: `application.properties` zet
`quarkus.flyway.migrate-at-start=true` globaal (geen `%prod`-override), dus
migraties draaien al automatisch bij het opstarten.

## Eenmalige setup

### 1. Repo-secrets

| Secret | Waarvoor |
| --- | --- |
| `ZAD_API_KEY` | ZAD Operations Manager API key (deploy + cleanup) |
| `GITHUB_ADMIN_TOKEN` | PAT met repo-admin; nodig om het GitHub-environment te verwijderen bij cleanup |

`GITHUB_TOKEN` is automatisch beschikbaar (image push naar GHCR).

### 2. ZAD-project en component

ZAD-project: `psd-law` (los van het NMC-project `nd-j7s`), component
`profielservice`. Geen andere ZAD-services nodig dan de Postgres-add-on en
"publiceren op het web" — de app heeft verder geen infra-afhankelijkheden (Quartz
gebruikt tabellen in dezelfde Postgres, geen aparte queue/cache/mail-service). Het
component zelf hoeft geen container-image te hebben ingevuld; `zad-actions/deploy`
zet die per deploy dynamisch (zelfde als `nmcapi` bij de NMC).

### 3. Applicatieconfiguratie in ZAD (managed DB + secrets)

De `zad-actions/deploy` action zet **geen** env-vars of DB-config; de app krijgt
die uit de ZAD-deploymentconfig. Dit is geregeld via de **`feature`-deployment**
(component `profielservice`), waar `clone-from: feature` in `deploy.yml` naar
verwijst. PR-deploys erven die env. De `stable`-deployment heeft haar eigen,
losstaande configuratie.

Bij het toevoegen van de Postgres-add-on op het component geeft ZAD platform-
variabelen terug (bij de NMC zijn dat `$APP_DATABASE_USER`, `$APP_DATABASE_PASSWORD`,
`$APP_DATABASE_SERVER_HOST`, `$APP_DATABASE_PORT`, `$APP_DATABASE_DB`) — de
env-vars hieronder zijn dus zelf weer opgebouwd uit die variabelen, geen letterlijke
waarden. Controleer bij het aanmaken van de Postgres-add-on op `profielservice` of
dezelfde namen terugkomen.

Benodigde env-vars op de `feature`-deployment (en analoog op `stable`):

| Env var | Waarde | Reden |
| --- | --- | --- |
| `QUARKUS_DATASOURCE_JDBC_URL` | `jdbc:postgresql://$APP_DATABASE_SERVER_HOST:$APP_DATABASE_PORT/$APP_DATABASE_DB` | Volledige url (bekende valkuil: alleen de hostname werkt niet) |
| `QUARKUS_DATASOURCE_USERNAME` | `$APP_DATABASE_USER` | `%prod`-waarde is leeg |
| `QUARKUS_DATASOURCE_PASSWORD` | `$APP_DATABASE_PASSWORD` | `%prod`-waarde is leeg |
| `QUARKUS_REST_CLIENT_VERIFICATIE_SERVICE_URL` | `https://wiremock-dev-mozam-chu.rig.prd1.gn2.quattro.rijksapps.nl` | Default is `https://verificatie.example.invalid`. De POC wijst naar een in-cluster Service (`verificatie-service.logius-moz-poc.svc.cluster.local`) die niet vanaf ZAD bereikbaar is; dit is de gedeelde ZAD WireMock-mock uit [#800](https://github.com/MinBZK/moza-profiel-service/issues/800). Al gestubd voor `POST /request` (200, tekst-referentie-id) en `POST /verify` (200 succes; `code: "000000"` simuleert bewust een foutieve-code-response) — geen extra stub-setup nodig |
| `QUARKUS_SCHEDULER_ENABLED` | `false` | Zie "Gedeelde database + Quartz" hierboven — alleen nodig op `feature` (previews), niet per se op `stable` |
| `MOZA_CORS_ORIGINS` | — | Alleen nodig als een frontend vanaf een andere origin de preview aanroept; Swagger UI op `/docs` is same-origin |

> Quarkus mapt env-vars naar properties via name-mangling (uppercase, niet-alfanumeriek
> → `_`).

**Niet nodig op ZAD, wel bestaand in `application.properties`:**

- `NOTIFYNL_EMAILVERIFICATIE_API_KEY`, `_TEMPLATE_ID` — profiel-service belt niet
  rechtstreeks NotifyNL; `EmailVerificatieService` stuurt deze twee waarden mee als
  velden in de request-body naar de verificatie-service (`POST /request`). Op ZAD
  wijst die URL naar de gedeelde WireMock-mock (zie boven), die de body niet
  valideert — elke placeholder-waarde (of zelfs niets, `%prod` default is een lege
  string, wat voor Quarkus "aanwezig" is, niet "missing") werkt. Pas nodig zodra
  `verificatie-service-url` naar iets wijst dat de aanroep echt doorzet naar
  NotifyNL.
- `NOTIFYNL_EMAILVERIFICATIE_REFERENCE` — dead config, wordt nergens in de code via
  `@ConfigProperty` gelezen. Niet nodig, ook niet in productie.
- `LOGBOEKDATAVERWERKING_*` (inclusief `_ENABLED`) — irrelevant zolang LDV uitstaat.
  Met `LOGBOEKDATAVERWERKING_ENABLED` ongezet valt de app terug op de `%prod`-default
  (leeg), wat door de applicatiecode als "uit" behandeld wordt. Pas nodig zodra LDV
  aangezet wordt.

> Deze env-vars worden nu handmatig gezet. Automatiseren via de ZAD Operations
> Manager API staat open als
> [moza-notificatiemanagementcomponent#10](https://github.com/MinBZK/moza-notificatiemanagementcomponent/issues/10)
> — dezelfde oplossing zou hier hergebruikt moeten worden.

## Hoe het draait

- PR open/synchronize/reopen → `build` (image `pr-<n>` naar GHCR) → `deploy-preview`
  (deploy + wacht op `/q/health/ready` + plaatst deploy-URL als PR-comment).
- PR closed → `cleanup-preview`.
- Push/merge naar `main` → `build` (image `main-<sha7>` naar GHCR) → `deploy-stable`.

URL-patroon: `https://profielservice-pr-<n>-psd-law.rig.prd1.gn2.quattro.rijksapps.nl`
→ `/docs` (Swagger UI), `/openapi.json`, `/q/health/ready`.

## Gotchas (al opgelost in de workflow)

- **JDBC-URL** moet volledig zijn: `jdbc:postgresql://host:5432/<db>` (env op
  `feature`/`stable`), niet enkel host of `jdbc://...`.
- **Image-digest i.p.v. mutable `pr-N`/`main-<sha7>` tag**: ZAD/k8s pullt een
  gelijke tag niet opnieuw, waardoor image-wijzigingen niet live kwamen. Deploy
  gaat nu op digest.
- **Health-check op de management-poort**: zie "Management-poort en health-check"
  hierboven — opgelost via `-Dquarkus.smallrye-health.management.enabled=false` in
  de build.

## Bekend, niet opgelost in deze workflow

- **Swagger UI "Try it out" wijst standaard naar productie.** `OpenApiConfig.java`
  definieert een vaste `servers`-lijst (`https://api.mijnoverheidzakelijk.nl/...` +
  `localhost`), anders dan bij de NMC (die bewust geen `servers`-blok heeft, zodat
  Swagger UI de request-origin volgt). Op een ZAD-preview moet je in de Swagger UI
  het "Servers"-dropdown handmatig naar de `pr-<n>`-URL zetten voordat "Try it out"
  werkt.
- **LDV-snapshot-dependency**: de build trekt
  `nl.mijnoverheidzakelijk.ldv:logboekdataverwerking-wrapper:1.4.0-SNAPSHOT` uit
  central-portal-snapshots. Snapshots zijn niet reproduceerbaar en kunnen
  verdwijnen; zie
  [#613](https://github.com/MinBZK/moza-profiel-service/issues/613). Als de
  deploy-build hierop faalt, is dit de eerste plek om te kijken.
- Overgeërfde open ZAD-issues die ook hier gelden: deploy-verificatie (#882),
  reaper voor verweesde `pr-<n>`-deployments (#884), vaste GitHub-environment
  (#885), verweesde GHCR-versies (#888), cleanup die fail-open faalt (#891), bewijs
  dat image/pod echt draait (#892).

## Restpunten

- [x] ZAD-project aangemaakt (`psd-law`) en `ZAD_PROJECT_ID` in `deploy.yml` ingevuld.
- [ ] `feature`- en `stable`-deployment in Operations Manager inrichten met de
  env-vars uit de tabel hierboven.
- [ ] `ZAD_API_KEY` als repo-secret toevoegen.
- [ ] `GITHUB_ADMIN_TOKEN` (repo-admin PAT) als repo-secret toevoegen, daarna in
  `deploy.yml` `delete-github-env` + `delete-github-deployments` weer op `'true'`
  zetten (nu `'false'` om hard falen van cleanup te voorkomen).
