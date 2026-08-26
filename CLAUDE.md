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

De controllers implementeren sinds `MinBZK/MijnOverheidZakelijk#751` de
gegenereerde interfaces uit `nl.rijksoverheid.moz.api.generated.api`
(`generateApis=true`, `interfaceOnly=true`, `returnResponse=true`). De interface
draagt pad, HTTP-methode, mediatypes en de validatie van de body; de controller
draagt alleen de implementatie. `RouteDekkingTest` bewaakt dat contract en
routes elkaar blijven dekken.

**Zet geen JAX-RS-annotatie op een controllermethode.** Twee gemeten faalwijzen,
die in tegengestelde richting misleiden. Een HTTP-methode-annotatie laat álle
annotaties van de interface voor die methode vervallen, ook `@Path`: de
gedocumenteerde route geeft dan een 404 die niet van "resource niet gevonden" te
onderscheiden is. Een `@Consumes` of `@Produces` wordt juist genegeerd, want die
van de interface wint — het Content-Type van een succesantwoord zet je op de
`Response` zelf. `ControllerAnnotatiesTest` bewaakt dit.

Niet-evidente schakelaars in de generator-configuratie (zie `pom.xml` voor de
volledige toelichting per stuk):

- `openApiNullable=false` — anders importeert de generator bij elk nullable veld
  `JsonNullable`, en die dependency hebben we niet.
- `schemaMappings` wijst `Instant`, `NullableInstant`, `UUID`, de drie
  domein-enums en `HttpProblem` naar bestaande types, zodat er geen tweede
  gelijknamige klasse ontstaat.
- `inputSpec` blijft **relatief**; absoluut breekt de build op Windows.

Naast het servercontract staat er een clientcontract in
`src/main/resources/openapi/verificatie_service.yaml`, waaruit
quarkus-openapi-generator de client voor de verificatie-service bouwt.

**Trek `io.quarkiverse.openapi.generator:*` niet handmatig op.** Die is in
`.github/dependabot.yml` bewust vastgezet op `<= 2.19.0`: vanaf 2.20.0 genereert
de server-extensie beanvelden als `Object` in plaats van het juiste type. De
build blijft daarbij groen omdat de stubs shadow zijn, dus CI waarschuwt je
niet. De pin onderdrukt ook security-updates voor dat artefact; de voorwaarden
om hem op te heffen staan in `dependabot.yml`.

### ADR-lint

Wijzig je het contract, controleer het dan tegen de NLGov ADR-ruleset. Geen CI
doet dit:

```bash
npx @stoplight/spectral-cli lint src/main/resources/META-INF/openapi.yaml \
  --ruleset https://static.developer.overheid.nl/adr/ruleset.yaml
```

Het contract haalt dit vandaag niet schoon — vergelijk vóór en ná, en maak er
geen bevindingen bíj. De bekende afwijkingen staan in `CONTRIBUTING.md`; de
`servers`-afwijking is bewust en wordt door `OpenApiMetadataTest` vastgelegd.

### Grens van de contractvalidatie

Sinds validator 3.0.0 (#151) wordt `type` op dit 3.1-document wél gehandhaafd.
Uit de tijd daarvóór stamt de regel dat een `anyOf` met een null-tak de validatie
voor dat veld uitschakelde — die tak matchte toen alles, omdat `type: "null"`
zelf niet werd gecontroleerd. Dat geldt niet meer: gemeten wijzen de `type`-vorm
en de `anyOf`-vorm dezelfde bodies af, en de generator maakt er hetzelfde
veldtype van. Nullbaarheid hoort nog steeds in het type, maar om één
schrijfwijze te houden en niet omdat de andere dekking kost.

De validatie is wel nog steeds geen vangnet voor alles. Twee dingen die ze niet
dekt: een regel over twee velden — "`waarde` moet een e-mailadres zijn wanneer
`type` Email is" is daarom niet als schema uit te drukken
(`MinBZK/MijnOverheidZakelijk#766`) — en de vorm van padparameters, waar de
server meer accepteert dan `format: uuid` belooft
(`MinBZK/MijnOverheidZakelijk#980`).

## Bewakingstests

Twaalf klassen bewaken elk één specifiek gat. Ze overlappen bewust niet, en de
meeste lichten in hun javadoc toe wat ze níet dekken — lees die voordat je er
een aanpast. Raak je iets uit de rechterkolom aan, dan is de klasse links
degene die je moet uitbreiden.

Acht staan in `src/test/java/nl/rijksoverheid/moz/architectuur/`; de drie
contracttests direct in `.../moz/`, en `OpenApiValidationTest` in
`.../moz/controller/`.

| Klasse | Bewaakt |
|--------|---------|
| `OpenApiContractDriftTest` | Gepubliceerd `/openapi.json` is gelijk aan het contractbestand — dus: de configuratie, niet de inhoud |
| `RouteDekkingTest` | Contract ↔ JAX-RS-routes, beide richtingen: pad en HTTP-methode |
| `ControllerAnnotatiesTest` | Dat controllers de JAX-RS-annotaties van hun gegenereerde interface niet alsnog zelf dragen |
| `OpenApiValidationTest` | Abstracte basisklasse zonder eigen tests: levert de validatiefilter die de vorm van de berichten tegen het gepubliceerde document toetst. Vijf integratietests erven ervan |
| `ContractHandhavingTest` | Dat het contract werkelijk afwijst wat het zegt af te wijzen (een contract dat álles afwijst is óók groen) |
| `StandardErrorResponsesTest` | Elke operatie documenteert een 500 → `HttpProblem` en een 400 → `HttpValidationProblem` |
| `OpenApiMetadataTest` | Contractversie == `ApiVersion.CURRENT`, plus de door ADR vereiste `info`-velden |
| `EnumPariteitTest` | Contract-enums == domein-enums; `schemaMappings` haalt die vergelijking anders uit de build weg |
| `UpdateSchemaPariteitTest` | Elk update-schema == zijn create-schema plus precies de toegestane extra's |
| `ValidatieExtensiesTest` | `x-class-extra-annotation` en `x-implements` staan samen op de schema's die de elfproef dragen |
| `RequestDtoOnveranderbaarheidTest` | Productiecode muteert geen binnenkomend request |
| `RegelDekkingTest` | Dat `GEEN_MUTERENDE_NAAM` en `GEEN_AANROEP_MET_PARAMETERS` écht vangen — in de test hierboven draaien ze tegen code die ze niet overtreedt |

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
`pom.xml` is leidend voor die getallen — controleer ze daar voor je je erop
baseert.

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
  of ontbrekende `V*.sql` komt dus **niet** in de testsuite boven. Controleer
  een nieuwe migratie apart tegen PostgreSQL: `docker compose up -d postgres`
  en dan `./mvnw quarkus:dev`, want dev-mode draait Flyway wél. Kijk of
  `flyway_schema_history` de nieuwe versie krijgt.
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
// De responses staan per operatie op volgorde met de succescode eerst. Voor
// OpenAPI zelf is die volgorde betekenisloos, maar de generator leidt er
// @Produces uit af en JAX-RS kiest het eerst vermelde mediatype als standaard,
// dus met een foutrespons vooraan krijgt elk succesantwoord problem+json.

// Wel
// Succescode eerst: de generator leidt hier @Produces uit af en JAX-RS neemt
// het eerste mediatype.
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
- **Merge squash.** De commitmessage eindigt dan op `(#nummer)`. In oudere
  historie staan nog merge-commits; dat is niet het patroon om te volgen.
- Commitmessages zijn een korte Nederlandse beschrijving, eventueel met een
  conventional-commit-prefix.
- Voeg bij het aanmaken van een PR **geen** reviewer toe.

### Issues en PR's koppelen

Issues staan in de
[MijnOverheidZakelijk](https://github.com/MinBZK/MijnOverheidZakelijk/issues)-tracker
met label `profiel-service`, niet in deze repo. Elke PR hoort bij een issue, en
die koppeling moet van twee kanten zichtbaar zijn:

- **Zet het issuenummer vooraan in de branchnaam**, gevolgd door een korte
  kebab-case-beschrijving: `766-validatiedetails-email`. Daarmee is aan de
  branch en aan elke PR-lijst af te lezen waar het werk bij hoort.
- **Noem het issue in de PR-beschrijving én de PR in het issue.** GitHub legt
  die koppeling hier niet vanzelf: de Development-zijbalk werkt alleen binnen
  dezelfde repo, en onze issues staan in een andere.
- **Verwijs cross-repo altijd voluit**: `MinBZK/MijnOverheidZakelijk#766`, nooit
  kaal `#766`. Deze repo heeft een eigen nummerreeks waarin issues en PR's
  elkaar delen (nu rond 160), dus een kale `#766` wijst hier naar niets en een
  laag nummer naar de verkeerde PR.

## Belangrijke bestanden

| Pad | Beschrijving |
|-----|--------------|
| `src/main/resources/META-INF/openapi.yaml` | Het contract: bron voor `/openapi.json` én voor de DTO-codegen |
| `src/main/resources/openapi/verificatie_service.yaml` | Clientcontract voor de externe verificatie-service |
| `src/main/resources/application.properties` | Runtime-configuratie; productiewaarden staan als lege `%prod`-sleutels klaar |
| `src/test/resources/application.properties` | H2, Flyway uit, JaCoCo-excludes |
| `src/main/resources/db/migration/` | Flyway-migraties |
| `pom.xml` | Quarkus-BOM, generator-configuratie en de JaCoCo-gate — met toelichting per keuze |
| `.github/dependabot.yml` | Groepering én versie-pins met hun reden; lees dit vóór je een dependency handmatig optrekt |
| `src/test/java/.../architectuur/` | Acht van de twaalf bewakingstests; zie de tabel hierboven voor de rest |
| `docs/zad-deploy.md` | ZAD PR-preview-deploys: hoe de workflow werkt en hoe je hem debugt |
| `FUZZING.md` | Fuzz-opzet, JUnit-targets en ClusterFuzzLite |
| `.github/workflows/` | CI: Maven-build, CodeQL, Scorecard, ClusterFuzzLite, ZAD-deploy |

## Deploy

ZAD is uitsluitend de **PR-preview- en ontwikkelomgeving** (project `psd-law`).
Elke PR vanuit deze repo krijgt een `pr-<nummer>`-deployment die zijn
configuratie via `clone-from` erft van de `feature`-deployment; push naar `main`
gaat naar de persistente `stable`-deployment. PR's vanaf een fork en PR's van
`dependabot[bot]` worden bewust overgeslagen — andere bots vangt die guard niet.

De workflow zet alleen wélk container-image draait. Applicatieconfiguratie
(DB-url, wachtwoorden, NotifyNL-keys) staat in de deployment zelf, in de ZAD
Operations Manager — niet in de workflow en niet in deze repo. De POC-deployment
op het Standaard Platform en de landing op LPC staan hierbuiten.

Details en debugroutes: `docs/zad-deploy.md`.
