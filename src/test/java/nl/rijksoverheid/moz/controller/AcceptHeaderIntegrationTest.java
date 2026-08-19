package nl.rijksoverheid.moz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
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

    private record Operatie(String methode, String pad, boolean produceertJson) {
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

            operaties.add(new Operatie(
                    opEntry.getKey().toUpperCase(Locale.ROOT), vulPadParameters(padEntry.getKey()), produceertJson));
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

    private static int status(Operatie operatie, String accept) {
        var verzoek = given().accept(accept);

        if (!"GET".equals(operatie.methode())) {
            verzoek = verzoek.contentType(ContentType.JSON).body(BODY);
        }

        return verzoek.when().request(operatie.methode(), operatie.pad()).then().extract().statusCode();
    }
}
