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
standaard op een aparte poort, niet de publieke HTTP-poort.
`quarkus.management.enabled` (en `.root-path`) is **build-time fixed** — niet via
een runtime env var op de deployment te overschrijven. (`.port`/`.host` zijn wel
runtime-instelbaar, maar dat lost dit probleem niet op: een andere poort kiezen
maakt `/q/health/ready` niet alsnog bereikbaar op de publieke poort.)

In plaats daarvan zet de build in `deploy.yml` de Maven-flag
`-Dquarkus.smallrye-health.management.enabled=false`. Dat is hetzelfde patroon dat
`application.properties` al gebruikt om OpenAPI/Swagger UI van de management-poort
af te houden (`quarkus.smallrye-openapi.management.enabled=false`), maar dan voor
health. Resultaat: `/q/health/ready` (inclusief de echte datasource-check) draait op de
publieke poort, `/q/metrics` blijft op de management-poort. Omdat dit image
uitsluitend op ZAD draait — nooit op het Standaard Platform — is dit veilig voor
zowel de `pr-<n>`- als de `stable`-deploy.

Let op de keerzijde: hiermee is `/q/health/ready` — inclusief of de datasource
bereikbaar is — ongeauthenticeerd zichtbaar voor iedereen die het publieke
ZAD-adres kent, op zowel `pr-<n>` als `stable`. Deze service heeft geen auth; dat
is hier een bewuste afweging (geldig zolang dit alleen op ZAD draait), geen
oversight.

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

Een tweede, nog niet opgelost risico van diezelfde gedeelde database:
`quarkus.flyway.migrate-at-start=true` staat globaal aan, en Flyway's defaults
(`validate-on-migrate=true`, `out-of-order=false`) zijn strikt. Met meerdere
gelijktijdig open PR-previews op één database kan de ene PR de andere breken:
een PR die een nieuwe migratie toevoegt en deployt, laat elke andere (van vóór die
migratie afgetakte) PR-preview bij de eerstvolgende redeploy falen met "detected
applied migration not resolved locally" — de boot faalt, `/q/health/ready` komt
nooit op, en de deploy gaat rood voor een PR die inhoudelijk niets fout deed. Twee
PR's die allebei een migratie met hetzelfde versienummer maar andere inhoud
toevoegen, geven een checksum-mismatch die pas met een handmatige reset van de
gedeelde database opgelost is. Dit speelt zodra er twee migratie-toevoegende PR's
tegelijk open staan; een schema (of database) per preview zou dit structureel
oplossen, maar is nu niet hoe ZAD dit inricht.

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
| `GH_ADMIN_TOKEN` | PAT met repo-admin; nodig om het GitHub-environment te verwijderen bij cleanup |

`GITHUB_TOKEN` is automatisch beschikbaar (image push naar GHCR).

> Bewust `GH_ADMIN_TOKEN`, niet `GITHUB_ADMIN_TOKEN`: GitHub verbiedt secret-namen
> met het gereserveerde `GITHUB_`-prefix ("Secret names must not start with
> GITHUB_"). De NMC-variant van deze workflow gebruikt nog wel `GITHUB_ADMIN_TOKEN`
> — dat secret kan daar dus nooit succesvol zijn aangemaakt.

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
| `NOTIFYNL_EMAILVERIFICATIE_API_KEY` | Elke niet-lege placeholder, bv. `zad-preview-key` | Verplicht, zie hieronder ("`%prod`-lege waarden" gotcha). Profiel-service belt niet rechtstreeks NotifyNL; `EmailVerificatieService` stuurt deze waarde mee als veld in de request-body naar de verificatie-service (`POST /request`). Op ZAD wijst die URL naar de gedeelde WireMock-mock (zie boven), die de body niet valideert — elke waarde werkt, als hij maar niet leeg is |
| `NOTIFYNL_EMAILVERIFICATIE_TEMPLATE_ID` | Elke niet-lege placeholder, bv. `zad-preview-template` | Idem |
| `LOGBOEKDATAVERWERKING_ENABLED` | `false` | Verplicht ondanks dat LDV uitstaat — zie gotcha hieronder |
| `LOGBOEKDATAVERWERKING_SERVICE_NAME` | `profiel-service` | Verplicht, zelfde gotcha |
| `MOZA_CORS_ORIGINS` | — | Alleen nodig als een frontend vanaf een andere origin de preview aanroept; Swagger UI op `/docs` is same-origin |

> Quarkus mapt env-vars naar properties via name-mangling (uppercase, niet-alfanumeriek
> → `_`).

### Gotcha: `%prod.x=` (leeg) resolveert als *afwezig*, niet als lege string

`application.properties` gebruikt op meerdere plekken het patroon `x=<default>` +
`%prod.x=` (leeg) om een property "verplicht per omgeving" te maken. Een leeg
`%prod`-scoped override schaduwt de default-waarde volledig en resolveert als
*afwezig*, niet als lege string — ongeacht welk mechanisme de property verderop
leest. Dat mechanisme verschilt wél per var, met een ander foutbeeld tot gevolg:

- **`NOTIFYNL_EMAILVERIFICATIE_API_KEY`/`_TEMPLATE_ID`**: constructor-parameters
  met `@ConfigProperty` in `EmailVerificatieService`. Quarkus valideert alle
  verplichte (niet-`Optional`, geen `defaultValue`) `@ConfigProperty`-injecties bij
  het opstarten in één keer; ontbreekt er een, dan faalt de boot volledig met
  `Failed to load config value of type class ... for: ...`.
- **`QUARKUS_DATASOURCE_*`**: Agroal-datasourceconfig, geen `@ConfigProperty`-
  injectie. Een ontbrekende username/password faalt niet in bovenstaande
  boot-sweep, maar pas bij de eerste connectiepoging, met een ander foutbeeld.
- **`LOGBOEKDATAVERWERKING_ENABLED`/`_SERVICE_NAME`**: worden lazy gelezen via
  `ConfigProvider.getValue(...)` in de LDV-wrapper (`ConfigurationLoader`), niet
  via `@ConfigProperty`. Een ontbrekende waarde faalt pas bij de eerste keer dat
  hij daadwerkelijk gelezen wordt, met `SRCFG00014: The config property ... is
  required but it could not be found` — een andere melding dan hierboven.

Praktisch verandert dit niets: deze env-vars (en om dezelfde reden ook
`QUARKUS_DATASOURCE_JDBC_URL` en de verificatie-service-url) moeten allemaal
expliciet gezet zijn, ook al lijkt een lege waarde op het eerste gezicht een
geldig "uitgeschakeld"-signaal. Het verschil zit in welke foutmelding je te zien
krijgt als je er een vergeet — handig om te weten bij het debuggen van een
volgende crash.

`NOTIFYNL_EMAILVERIFICATIE_REFERENCE` blijft wel echt overbodig: dead config,
wordt nergens in de code via `@ConfigProperty` gelezen (dus zit niet in de
build-time validatieset en kan niet op deze manier crashen). De
`LOGBOEKDATAVERWERKING_CLICKHOUSE_*`-vars blijven ook echt optioneel zolang
`_ENABLED=false` staat: `ConfigurationLoader.validateClickhouseConfig()` in de
LDV-wrapper wordt alleen aangeroepen als `enabled` op `true` staat, dus die keys
worden dan nooit gelezen.

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

Het geserveerde `/openapi.json` bevat geen `servers`-array, dus Swagger UI valt
terug op same-origin requests — "Try it out" op `/docs` werkt hierdoor direct
tegen de preview zelf, zonder iets handmatig te hoeven omzetten.

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
