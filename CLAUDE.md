# CLAUDE.md

Richtlijnen voor het werken aan de moza-profiel-service.

De README beschrijft wat de service doet, hoe je hem lokaal draait en hoe de
API eruitziet. Dit bestand beschrijft wat je moet weten om er wijzigingen in aan
te brengen zonder iets stilzwijgend te breken: de regels, de gates en de plekken
waar de build je niet waarschuwt.

## Taal

Communicatie, commentaar en commitmessages in het Nederlands.

De grens tussen Nederlands en Engels loopt door de code heen:

- **Domeinbegrippen blijven Nederlands**, ook als identifier: `Partij`,
  `Contactgegeven`, `Voorkeur`, `Dienstverlener`, `teVerwijderenOp`,
  `RegelDekkingTest`.
- **Vast technisch idioom blijft Engels.** Circuit breaker, retry, timeout,
  scheduler, mapper, filter. Vertalen maakt die termen minder herkenbaar, niet
  meer.
- **Testnamen zijn Nederlandse volzinnen**:
  `contractEnDomeintypeKennenDezelfdeWaarden`, niet `testEnumParity`.

Vaste term: **soft delete**, nooit "zachtverwijderd". Een contactgegeven of
voorkeur met een `teVerwijderenOp` in de toekomst is soft deleted.

## Technische stack

- **Runtime:** Quarkus 3.38.2, Java 25
- **Build:** één Maven-module, wrapper `./mvnw` (geen monorepo)
- **API:** contract-first uit `META-INF/openapi.yaml`, Quarkus REST + Jackson
- **Persistentie:** PostgreSQL 18 + Hibernate ORM Panache + Flyway; H2 in tests
- **Mapping:** MapStruct (entity → response-DTO)
- **Scheduling:** Quartz, clustered (`jdbc-cmt`) — de retentiejob draait op één
  node tegelijk
- **Test:** JUnit 5, REST-assured, Mockito, ArchUnit, Jazzer (fuzzing), Pact
  (providerverificatie); WireMock draait alleen in dev-mode, op poort 8089
- **Fouten:** RFC 9457 `application/problem+json` via quarkus-http-problem
- **Audit:** LDV-wrapper (`logboekdataverwerking`), standaard uitgeschakeld

Packages zijn **technisch** ingedeeld: `controller/`, `services/`, `entity/`,
`mapper/`, `validation/`, `filter/`, `job/`, `helper/`, `exception/`, `common/`.
Volg die indeling; dit is bewust geen functionele indeling.

## Contract-first

`src/main/resources/META-INF/openapi.yaml` is de bron. Annotatie-scanning staat
uit (`mp.openapi.scan.disable=true`), dus datzelfde bestand wordt statisch op
`/openapi.json` geserveerd én voedt de codegen.

**Schrijf geen DTO met de hand en bewerk niets onder `target/generated-sources`.**
Pas het contract aan en draai de build opnieuw. De DTO's landen in
`nl.rijksoverheid.moz.api.generated.model`.

De controllers zijn wél handgeschreven. `generateApis=false` staat er omdat
Quarkus REST geen server-resources via een gegenereerde JAX-RS interface
ondersteunt: de parameter-binding gaat dan verloren. De controllers zijn dus
concrete resources die de paden uit het contract implementeren, en
`RouteDekkingTest` bewaakt dat ze niet uiteenlopen.

Niet-evidente schakelaars in de generator-configuratie (zie `pom.xml` voor de
volledige toelichting per stuk):

- `openApiNullable=false` — anders importeert de generator bij elk nullable veld
  `JsonNullable`, en die dependency hebben we niet.
- `schemaMappings` wijst `Instant`, `UUID`, de drie domein-enums en `HttpProblem`
  naar bestaande types, zodat er geen tweede gelijknamige klasse ontstaat.
- `inputSpec` is bewust een **relatief** pad. Absoluut breekt op Windows: Maven
  maakt er `C:\...` van en de swagger-parser in generator 7.10.0 leest de
  driveletter als URI-scheme.

Naast het servercontract staat er een clientcontract in
`src/main/resources/openapi/verificatie_service.yaml`, waaruit
quarkus-openapi-generator de client voor de verificatie-service bouwt.

### Grens van de contractvalidatie

De contractvalidatie is geen vangnet voor alles. Een `anyOf` met een null-tak
zet de validatie voor dat veld feitelijk uit: elke waarde matcht dan een van de
takken. Gebruik dat alleen waar het echt nodig is, en weet dat je er dekking mee
inlevert. Vanaf validator 3.0.0 wordt `type` op OpenAPI 3.1 wél gehandhaafd.

## Bewakingstests

Elf tests bewaken elk één specifiek gat. Ze overlappen bewust niet, en de
javadoc van elke test legt uit wat hij níet dekt — lees die voordat je hem
aanpast. Wijzig je iets in de tabel hieronder, dan is dat de test die je moet
uitbreiden:

| Test | Bewaakt |
|------|---------|
| `OpenApiContractDriftTest` | Gepubliceerd `/openapi.json` is gelijk aan het contractbestand — dus: de configuratie, niet de inhoud |
| `RouteDekkingTest` | Contract ↔ JAX-RS-routes, beide richtingen: pad en HTTP-methode |
| `OpenApiValidationTest` | De vorm van de berichten zelf, tegen het gepubliceerde document |
| `ContractHandhavingTest` | Dat het contract werkelijk afwijst wat het zegt af te wijzen (een contract dat álles afwijst is óók groen) |
| `StandardErrorResponsesTest` | Elke operatie documenteert een 500 → `HttpProblem` en een 400 → `HttpValidationProblem` |
| `OpenApiMetadataTest` | Contractversie == `ApiVersion.CURRENT`, plus de door ADR vereiste `info`-velden |
| `EnumPariteitTest` | Contract-enums == domein-enums; `schemaMappings` haalt die vergelijking anders uit de build weg |
| `UpdateSchemaPariteitTest` | Elk update-schema == zijn create-schema plus precies de toegestane extra's |
| `ValidatieExtensiesTest` | `x-class-extra-annotation` en `x-implements` staan samen op de schema's die de elfproef dragen |
| `RequestDtoOnveranderbaarheidTest` | Productiecode muteert geen binnenkomend request |
| `RegelDekkingTest` | Dat de twee regels hierboven écht vangen — ze draaien in de andere test tegen code die ze niet overtreedt |

Twee dingen die deze tests **niet** dekken en die je zelf moet bewaken:

- Een endpoint dat erbij komt zónder contractwijziging blijft groen in
  `OpenApiContractDriftTest`; dat is wat `RouteDekkingTest` opvangt.
- `RequestDtoOnveranderbaarheidTest` groen betekent "niet via de DTO gemuteerd",
  niet "onveranderlijk": een lijst-getter geeft de levende collectie terug.
  Bewerk je een lijst uit een request, kopieer hem dan eerst.

Voeg je een nieuwe ontsnappingsvorm toe aan `RequestMutaties`, geef hem dan ook
een waarde in `Mutatievorm` — `elkeFixtureIsGedekt()` bewaakt die tweede stap.

## Build en test

Java en Maven staan **niet op `PATH`**; ze komen uit SDKMAN. Source die eerst:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

```bash
docker compose up -d          # PostgreSQL (+ ClickHouse) voor dev-mode
./mvnw quarkus:dev            # live reload op http://localhost:8080
./mvnw verify                 # volledige suite; draait op H2, geen Docker nodig
```

Tests hebben geen Docker nodig: ze draaien op H2 in-memory en `devservices` staat
uit. `docker compose` is alleen voor dev-mode.

Alle tests draaien onder Surefire — ook de klassen die `IntegrationTest` heten,
want Failsafe kijkt naar `*IT` en `skipITs` staat op `true`.

### Fuzzing

Fuzz-tests draaien standaard met een handvol iteraties mee in de suite. Langer
draaien:

```bash
./mvnw test -Dtest=EndpointFuzzTest -Djazzer.duration=1m -Djacoco.skip=true
```

`-Djacoco.skip=true` is nodig omdat een deelverzameling van de tests de
coverage-gate niet haalt. Zie `FUZZING.md` voor de standalone
ClusterFuzzLite-targets.

### Coverage

JaCoCo-gate: **85% line, 80% branch** op BUNDLE-niveau, uitgesloten zijn
`nl/rijksoverheid/moz/external/**` en `nl/rijksoverheid/moz/api/generated/**`.

De `check` hangt aan fase **`test`**, niet `verify` — CI draait `mvn package` en
handhaaft de gate daarmee dus wél.

Twee dingen die hier vaak misgaan:

- **Gewone unittests tellen mee.** Dat is niet vanzelfsprekend: de
  quarkus-jacoco-extensie meet alleen `@QuarkusTest`-klassen en gooit het
  exec-bestand aan het begin van de run weg. `quarkus.jacoco.reuse-data-file=true`
  plus een expliciete `prepare-agent` op de Surefire-JVM zorgt dat beide in
  `jacoco-quarkus.exec` accumuleren.
- **De excludelijst staat op twee plekken** — in `pom.xml` en als
  `quarkus.jacoco.excludes` in `src/test/resources/application.properties`. Die
  horen gelijk te blijven.

## Database en migraties

- **Migraties zijn immutable na toepassing.** Wijzig een bestaande `V*.sql`
  nooit; voeg een `V(N+1)__...sql` toe in `src/main/resources/db/migration/`.
- **SQL moet op H2 én PostgreSQL draaien.** Prod/dev is PostgreSQL 18, tests
  draaien op H2 in PostgreSQL-modus.
- **Tests draaien de migraties niet.** Flyway staat uit in de testconfiguratie
  en Hibernate bouwt het schema uit de entities (`drop-and-create`). Een kapotte
  of ontbrekende `V*.sql` komt dus **niet** in de testsuite boven — controleer
  een migratie apart tegen een echte PostgreSQL voordat je hem mergt.
- In prod staat `schema-management.strategy=validate`: wijkt een entity af van
  het gemigreerde schema, dan start de applicatie niet.
- Kolomnamen volgen `CamelCaseToUnderscoresNamingStrategy`; schrijf ze in de
  migratie dus met underscores.
- Envers staat uit (`quarkus.hibernate-envers.active=false`) zolang de audittabellen
  nog niet in een migratie staan.

## Quarkus-configuratie

- **Management-port 9090** draagt `/q/health` en `/q/metrics`, apart van de
  publieke port, zodat ze cluster-intern gescrapet worden zonder via de gateway
  te lopen. `/openapi.json` en `/docs` staan bewust wél op de publieke port.
- **`/openapi.json` is expliciet geconfigureerd** — ADR-eis; de Quarkus-default
  is `/q/openapi`.
- **CORS-origins zijn een expliciete lijst, nooit `*`**, per omgeving te
  overschrijven via `MOZA_CORS_ORIGINS`.
- Lokale secrets horen in een gitignored
  `src/main/resources/application-dev.properties`, nooit in
  `application.properties`.

## Teststrategie

Beoordeel bij elke codewijziging of er tests bij of om moeten.

- **Happy én unhappy paths.** Foutgevallen, edge cases en validatiefouten horen
  erbij, niet alleen het successcenario.
- **Kies testdata die het gedrag uitlokt**, niet de makkelijkste die slaagt. Bij
  collecties altijd minstens leeg, één en meerdere elementen: een lijst van één
  verbergt "geeft het enige element terug" achter "discrimineert per sleutel".
  Bundel die cardinaliteiten met `@ParameterizedTest`.
- **Integratietests** (`@QuarkusTest`) wanneer de wijziging meerdere componenten
  of een externe afhankelijkheid raakt. De externe verificatie-service mock je
  op clientniveau met `@InjectMock @RestClient` plus Mockito — niet over HTTP.
  De WireMock-mappings in `src/test/resources/mappings/` zijn voor dev-mode, niet
  voor de testsuite: `quarkus.devservices.enabled=false` zet de WireMock-devservice
  in tests uit.
- **Fuzzing** overwegen bij input-parsing, validatielogica en security-gevoelige
  code.
- **Pact** verifieert de providerkant tegen `src/test/resources/pacts/`. Het
  huidige bestand is een zelftestcontract van de provider.

## Commentaar

Houd commentaar compact. Eén of twee zinnen die zeggen wát er niet vanzelf
spreekt en waaróm. Geen alinea's met de afweging, de alternatieven en de
meetresultaten erbij; die horen in de commitmessage of het issue.

Schrijf alleen op wat je hebt gecontroleerd. Een verklaring van een mechanisme —
"de validator doet X", "de generator emit Y" — is een bewering over gedrag: klopt
hij niet, dan is hij schadelijker dan geen commentaar, want de volgende lezer
bouwt erop voort. Weet je het niet zeker, laat het weg of noteer het als aanname.

```java
// Niet
// anyOf en niet oneOf: een null-waarde matcht bij de contractvalidatie zowel de
// Instant-tak als de null-tak, en oneOf eist er precies één. Semantisch maakt
// het hier niets uit — de unie is hetzelfde — maar oneOf zou op elke lege
// verwijderdatum een valse fout geven.

// Wel
// anyOf: oneOf faalt hier op elke waarde omdat beide takken alles matchen.
```

Verwijs in commentaar niet naar CLAUDE.md-secties. Beschrijf de regel zelf, zodat
het commentaar zonder dit bestand leesbaar blijft.

## Codestijl

### Witregels rond `if`-statements

Gebruik witregels rondom `if`-statements voor de leesbaarheid: een lege regel
vóór het `if`-blok, ná het `if`-blok, en vóór een afsluitende `return`. Een `if`
dat het eerste statement van een methode of blok is heeft geen witregel ervóór
nodig.

```java
// Niet
Instant clearedAt = registreerGebruik(cg);
ContactgegevenResponse cr = mapContactgegeven(cg);
if (clearedAt != null) {
    cr.lastUpdated = clearedAt;
    cr.teVerwijderenOp = null;
}
return cr;

// Wel
Instant clearedAt = registreerGebruik(cg);
ContactgegevenResponse cr = mapContactgegeven(cg);

if (clearedAt != null) {
    cr.lastUpdated = clearedAt;
    cr.teVerwijderenOp = null;
}

return cr;
```

## Git-werkwijze

- **Nooit direct pushen naar `main`.** Alles via een feature branch en een Pull
  Request.
- PR's worden squash-gemerged; de commitmessage eindigt op `(#nummer)`.
- Dependabot-commits volgen `build(deps): ...`; eigen commits zijn een korte
  Nederlandse beschrijving, eventueel met een conventional-commit-prefix.
- Voeg bij het aanmaken van een PR **geen** reviewer toe.
- Issues staan in de [MijnOverheidZakelijk](https://github.com/MinBZK/MijnOverheidZakelijk/issues)-tracker
  met label `profiel-service`, niet in deze repo.

## Belangrijke bestanden

| Pad | Beschrijving |
|-----|--------------|
| `src/main/resources/META-INF/openapi.yaml` | Het contract: bron voor `/openapi.json` én voor de DTO-codegen |
| `src/main/resources/openapi/verificatie_service.yaml` | Clientcontract voor de externe verificatie-service |
| `src/main/resources/application.properties` | Runtime-configuratie; secrets alleen als lege `%prod`-sleutels |
| `src/test/resources/application.properties` | H2, Flyway uit, JaCoCo-excludes |
| `src/main/resources/db/migration/` | Flyway-migraties |
| `pom.xml` | Quarkus-BOM, generator-configuratie en de JaCoCo-gate — met toelichting per keuze |
| `src/test/java/.../architectuur/` | De bewakingstests uit de tabel hierboven |
| `docs/zad-deploy.md` | ZAD PR-preview-deploys: hoe de workflow werkt en hoe je hem debugt |
| `FUZZING.md` | Fuzz-opzet, JUnit-targets en ClusterFuzzLite |
| `.github/workflows/` | CI: Maven-build, CodeQL, Scorecard, ClusterFuzzLite, ZAD-deploy |

## Deploy

ZAD is uitsluitend de **PR-preview- en ontwikkelomgeving** (project `psd-law`).
Elke PR vanuit deze repo van een niet-bot auteur krijgt een
`pr-<nummer>`-deployment die zijn configuratie via `clone-from` erft van de
`feature`-deployment; push naar `main` gaat naar de persistente
`stable`-deployment. PR's vanaf een fork en dependabot-PR's worden bewust
overgeslagen.

De workflow zet alleen wélk container-image draait. Applicatieconfiguratie
(DB-url, wachtwoorden, NotifyNL-keys) staat in de deployment zelf, in de ZAD
Operations Manager — niet in de workflow en niet in deze repo. De POC-deployment
op het Standaard Platform en de landing op LPC staan hierbuiten.

Details en debugroutes: `docs/zad-deploy.md`.
