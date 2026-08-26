package nl.rijksoverheid.moz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_ACCEPTABLE;

/**
 * Legt vast welke {@code Accept}-headers de API bedient, en waarom dat per operatie verschilt.
 * <p>
 * Sinds de resources via gegenereerde interfaces lopen (#751) komt {@code @Produces} per operatie
 * uit de mediatypes van de responses in het contract, en niet meer uit één {@code @Produces} op de
 * controllerklasse. JAX-RS onderhandelt op wat er gedeclareerd is, vóórdat de resource-methode
 * draait. Een operatie waarvan geen enkele succesrespons {@code application/json} declareert houdt
 * daardoor alleen {@code application/problem+json} over — van de foutresponses — en wijst een
 * verzoek dat uitsluitend {@code application/json} accepteert af met een 406, ook al valt er niets
 * te onderhandelen.
 * <p>
 * Dat is een bewuste keuze: het contract eerlijk houden weegt zwaarder. {@code content:
 * application/json} op zo'n succesrespons zou het oplossen, maar declareert een body die niet
 * bestaat; die declaraties zijn eerder juist verwijderd en {@code OpenApiValidationTest} bewaakt
 * dat. Wie zo'n operatie aanroept moet dus {@code application/problem+json} accepteren, of een
 * Accept-header sturen met een wildcard erin.
 * <p>
 * De verdeling wordt uit {@code META-INF/openapi.yaml} afgeleid in plaats van hier bijgehouden.
 * Een operatie die erbij komt valt daardoor automatisch in de juiste groep; een handgeschreven
 * lijst zou hem stilzwijgend overslaan, en dat is net de faalwijze die deze test moet vangen.
 * <p>
 * Deze test controleert niet of een endpoint überhaupt bestaat — dat doet
 * {@link nl.rijksoverheid.moz.architectuur.RouteDekkingTest}, die elke operatie in het contract
 * aan een resource-methode koppelt. Hier gaat het uitsluitend om de onderhandeling.
 */
@QuarkusTest
class AcceptHeaderIntegrationTest {

    private static final String BODY = """
            {"identificatieType":"BSN","identificatieNummer":"111111104"}
            """;

    private static final String DIENSTVERLENER = "AcceptHeaderIntegrationTest-DV";

    /** Eén geval per operatie die application/json declareert, met invoer die werkelijk slaagt. */
    private record SuccesGeval(String methode, String padTemplate, String body) {
        String pad() {
            return padTemplate.replace("{naam}", DIENSTVERLENER).replace("{dienstverlenerNaam}", DIENSTVERLENER);
        }

        @Override
        public String toString() {
            return methode + " " + padTemplate;
        }
    }

    private static final List<SuccesGeval> SUCCESGEVALLEN = List.of(
            // Telefoonnummer en geen Email: een e-mailadres zou een verificatiecode aanvragen
            // bij de externe dienst, en daar gaat deze test niet over.
            new SuccesGeval("POST", "/api/profielservice/v1/contactgegeven", """
                    {"identificatieType":"BSN","identificatieNummer":"111111104",
                     "type":"Telefoonnummer","waarde":"0612345678"}
                    """),
            new SuccesGeval("POST", "/api/profielservice/v1/voorkeur", """
                    {"identificatieType":"BSN","identificatieNummer":"111111104",
                     "voorkeurType":"WebsiteTaal","waarde":"nl"}
                    """),
            new SuccesGeval("POST", "/api/profielservice/v1/partij", BODY),
            new SuccesGeval("POST", "/api/profielservice/v1/partijen/bulk", """
                    {"identificaties":[{"identificatieType":"BSN","identificatieNummer":"111111104"}]}
                    """),
            new SuccesGeval("POST", "/api/profielservice/v1/dienstverlener",
                    "{\"naam\":\"" + DIENSTVERLENER + "\"}"),
            new SuccesGeval("POST", "/api/profielservice/v1/dienstverlener/{dienstverlenerNaam}/diensten",
                    "{\"naam\":\"Proefdienst\"}"),
            new SuccesGeval("GET", "/api/profielservice/v1/dienstverlener/{naam}", null));

    static Stream<Arguments> succesGevallen() {
        return SUCCESGEVALLEN.stream().map(Arguments::of);
    }

    /**
     * De partij en de dienstverlener moeten bestaan voordat de operaties die ze opzoeken kunnen
     * slagen. Ze worden via de API aangemaakt, zodat deze test geen aannames doet over de opslag.
     */
    @BeforeEach
    void seed() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Telefoonnummer","waarde":"0611111111"}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then().statusCode(anyOf(is(200), is(201)));

        given().contentType(ContentType.JSON)
                .body("{\"naam\":\"" + DIENSTVERLENER + "\"}")
                .when().post("/api/profielservice/v1/dienstverlener")
                .then().statusCode(201);
    }

    @AfterEach
    @Transactional
    void ruimOp() {
        ScopeContactgegeven.deleteAll();
        ScopeVoorkeur.deleteAll();
        Contactgegeven.deleteAll();
        Voorkeur.deleteAll();
        DienstverlenerDienst.deleteAll();
        Dienst.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
        Dienstverlener.deleteAll();
    }

    private record Operatie(String methode, String padTemplate, String pad, boolean produceertJson) {
        @Override
        public String toString() {
            return methode + " " + pad;
        }
    }

    /**
     * Alle operaties uit het contract, met per operatie of een succesrespons application/json
     * declareert. Path-parameters worden gevuld met waarden die nergens bestaan: het antwoord
     * mag een 404 zijn, als het maar geen 406 is.
     */
    private static List<Operatie> operatiesUitHetContract() throws Exception {
        JsonNode paden;

        try (InputStream in = AcceptHeaderIntegrationTest.class.getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            paden = new ObjectMapper(new YAMLFactory()).readTree(in).path("paths");
        }

        Set<String> httpMethoden = Set.of("get", "post", "put", "patch", "delete");
        List<Operatie> operaties = new ArrayList<>();

        paden.fields().forEachRemaining(padEntry -> padEntry.getValue().fields().forEachRemaining(opEntry -> {
            if (!httpMethoden.contains(opEntry.getKey())) {
                return;
            }

            boolean produceertJson = false;
            var responses = opEntry.getValue().path("responses").fields();

            while (responses.hasNext()) {
                var response = responses.next();

                if (response.getKey().startsWith("2")
                        && response.getValue().path("content").has("application/json")) {
                    produceertJson = true;
                }
            }

            operaties.add(new Operatie(opEntry.getKey().toUpperCase(Locale.ROOT),
                    padEntry.getKey(), vulPadParameters(padEntry.getKey()), produceertJson));
        }));

        Assertions.assertFalse(operaties.isEmpty(), "Geen operaties uit het contract gelezen");

        return operaties;
    }

    private static String vulPadParameters(String pad) {
        return pad
                .replaceAll("\\{\\w*[Ii]d}", UUID.randomUUID().toString())
                .replaceAll("\\{\\w+}", "BestaatNiet");
    }

    static Stream<Arguments> zonderJsonRespons() throws Exception {
        return operatiesUitHetContract().stream().filter(op -> !op.produceertJson()).map(Arguments::of);
    }

    static Stream<Arguments> metJsonRespons() throws Exception {
        return operatiesUitHetContract().stream().filter(Operatie::produceertJson).map(Arguments::of);
    }

    /**
     * Geen succesrespons met application/json betekent: alleen problem+json in @Produces, dus een
     * 406 op een verzoek dat uitsluitend application/json accepteert. Met een mediatype dat de
     * operatie wél declareert komt hetzelfde verzoek gewoon binnen.
     */
    @ParameterizedTest(name = "{0} weigert alleen application/json")
    @MethodSource("zonderJsonRespons")
    void operatieZonderJsonResponsWeigertApplicationJson(Operatie operatie) {
        Assertions.assertEquals(NOT_ACCEPTABLE, status(operatie, "application/json"));
        Assertions.assertNotEquals(NOT_ACCEPTABLE, status(operatie, "application/problem+json"));
        Assertions.assertNotEquals(NOT_ACCEPTABLE, status(operatie, "*/*"));
    }

    /**
     * Een operatie die application/json declareert hoort een aanroeper die daar exclusief om
     * vraagt hetzelfde antwoord te geven als een aanroeper met een wildcard. De vergelijking met
     * {@code * / *} is scherper dan alleen "niet 406": zou de onderhandeling hier ooit gaan
     * afwijken, dan valt dat op ongeacht welke status eruit komt.
     */
    @ParameterizedTest(name = "{0} bedient application/json")
    @MethodSource("metJsonRespons")
    void operatieMetJsonResponsAccepteertApplicationJson(Operatie operatie) {
        int metWildcard = status(operatie, "*/*");

        Assertions.assertNotEquals(NOT_ACCEPTABLE, metWildcard,
                "een wildcard hoort altijd bediend te worden");
        Assertions.assertEquals(metWildcard, status(operatie, "application/json"),
                "de Accept-header hoort hier geen verschil te maken");
    }

    /**
     * De gegenereerde interfaces zetten voor een operatie met een JSON-body béide mediatypes op
     * {@code @Produces}. Een aanroeper die problem+json laat winnen — exclusief, of met een hogere
     * q-waarde — zou daardoor een geslaagd antwoord terugkrijgen met de succes-DTO onder
     * {@code Content-Type: application/problem+json}, het mediatype dat RFC 9457 voor fouten
     * reserveert. De controllers zetten het type daarom expliciet op de succesresponses; dat is
     * de enige plek waar het kan, want een {@code @Produces} op de implementatie wordt genegeerd.
     * <p>
     * Zonder deze assertie zou dat ook stilzwijgend terugkeren wanneer in het contract ooit een
     * foutrespons boven de succesrespons komt te staan: dan kantelt de volgorde in
     * {@code @Produces} en daarmee het standaardantwoord.
     */
    @ParameterizedTest(name = "{0} labelt succes als application/json")
    @MethodSource("metJsonRespons")
    void operatieMetJsonResponsLabeltSuccesAlsJson(Operatie operatie) {
        for (String accept : new String[]{"application/problem+json", "*/*", "application/json"}) {
            var antwoord = antwoord(operatie, accept);
            String verwacht = antwoord.statusCode() / 100 == 2 ? "application/json" : "application/problem+json";

            Assertions.assertTrue(antwoord.contentType().startsWith(verwacht),
                    operatie + " met Accept: " + accept + " gaf status " + antwoord.statusCode()
                            + " met Content-Type " + antwoord.contentType() + ", verwacht " + verwacht);
        }
    }

    /**
     * Een geslaagd antwoord hoort application/json te dragen, ook wanneer de aanroeper
     * problem+json prefereert. Dit is het geval dat werkelijk misging.
     * <p>
     * De parameterized tests hierboven bereiken die 2xx-tak niet: zij sturen één generieke body
     * die elke operatie afwijst, dus daar is de verwachting altijd problem+json. Vandaar per
     * operatie een body die wél slaagt. Dat kan niet uit het contract worden afgeleid — geldige
     * invoer volgt niet uit een schema — dus bewaakt
     * {@link #elkeOperatieMetJsonResponsHeeftEenSuccesGeval()} dat deze lijst niet achterloopt.
     */
    @ParameterizedTest(name = "{0} labelt een geslaagd antwoord als application/json")
    @MethodSource("succesGevallen")
    void geslaagdAntwoordBlijftJsonOokAlsDeAanroeperProblemJsonPrefereert(SuccesGeval geval) {
        var verzoek = given().accept("application/problem+json;q=0.9, application/json;q=0.1");

        if (geval.body() != null) {
            verzoek = verzoek.contentType(ContentType.JSON).body(geval.body());
        }

        var antwoord = verzoek.when().request(geval.methode(), geval.pad()).then().extract();

        Assertions.assertEquals(2, antwoord.statusCode() / 100,
                geval + " hoort te slagen, anders toetst deze test niets; kreeg "
                        + antwoord.statusCode() + " " + antwoord.body().asString());
        Assertions.assertTrue(antwoord.contentType().startsWith("application/json"),
                geval + " gaf Content-Type " + antwoord.contentType());
    }

    /**
     * Zonder deze controle zou een nieuwe operatie met een JSON-body stilzwijgend buiten de
     * succestest hierboven vallen — precies de faalwijze die deze klasse moet vangen.
     */
    @Test
    void elkeOperatieMetJsonResponsHeeftEenSuccesGeval() throws Exception {
        Set<String> uitHetContract = operatiesUitHetContract().stream()
                .filter(Operatie::produceertJson)
                .map(operatie -> operatie.methode() + " " + operatie.padTemplate())
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> gedekt = SUCCESGEVALLEN.stream()
                .map(geval -> geval.methode() + " " + geval.padTemplate())
                .collect(Collectors.toCollection(TreeSet::new));

        Assertions.assertEquals(uitHetContract, gedekt,
                "Elke operatie die application/json declareert hoort een succesgeval te hebben");
    }

    private record Antwoord(int statusCode, String contentType) {}

    private static Antwoord antwoord(Operatie operatie, String accept) {
        var verzoek = given().accept(accept);

        if (!"GET".equals(operatie.methode())) {
            verzoek = verzoek.contentType(ContentType.JSON).body(BODY);
        }

        var respons = verzoek.when().request(operatie.methode(), operatie.pad()).then().extract();

        return new Antwoord(respons.statusCode(),
                respons.contentType() == null ? "" : respons.contentType());
    }

    private static int status(Operatie operatie, String accept) {
        return antwoord(operatie, accept).statusCode();
    }
}
