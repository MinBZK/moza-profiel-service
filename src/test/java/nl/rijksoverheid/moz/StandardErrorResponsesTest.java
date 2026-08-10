package nl.rijksoverheid.moz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Bewaakt dat elke operatie haar foutresponses op dezelfde manier documenteert. Sinds de
 * gedeelde blokken in {@code components/responses} staan, is dat niet meer per operatie na
 * te lezen: een operatie verwijst met een {@code $ref} en de inhoud staat één keer centraal.
 * Deze test volgt die verwijzing en controleert waar hij uitkomt.
 *
 * <p>Drie invarianten. Elke operatie documenteert een 500 die naar {@code HttpProblem} wijst.
 * En elke 400 wijst naar {@code HttpValidationProblem}, want bij bean-validatie levert de
 * applicatie een {@code violations}-lijst; de twee schema's zijn structureel niet van elkaar
 * te onderscheiden (geen {@code required}, {@code additionalProperties: true}), dus de
 * contractvalidatie in {@code OpenApiValidationTest} merkt
 * het niet als er per ongeluk naar {@code HttpProblem} verwezen wordt. De overige
 * foutcodes horen andersom juist wél een kale {@code HttpProblem} te zijn.
 */
@QuarkusTest
class StandardErrorResponsesTest {

    /**
     * Dit contract kent geen head-, options- of trace-operaties. Komen die er ooit, dan moeten ze
     * hier bij, anders vallen ze stilzwijgend buiten de sweep.
     */
    private static final List<String> HTTP_METHODEN = List.of("get", "post", "put", "patch", "delete");

    /**
     * De foutcodes die dit contract vandaag gebruikt, buiten de 400 en de 500 die in de test
     * hieronder apart worden gecontroleerd. Bewust een vaste lijst en
     * geen universele regel: komt er ooit een 401, 415 of 429 bij, dan staat die er niet
     * automatisch tussen en moet hij hier worden toegevoegd.
     */
    private static final List<String> OVERIGE_FOUTCODES = List.of("403", "404", "409", "503");

    /**
     * Gelijk aan het huidige aantal operaties, dus zonder speling: elke verwijdering valt hierop
     * om en moet bewust worden bijgesteld. Zonder ondergrens zou een sweep die vastloopt op één
     * operatie er hetzelfde uitzien als een volledige controle.
     */
    private static final int MINIMAAL_AANTAL_OPERATIES = 15;

    @Test
    void elkeOperatieDocumenteertDeStandaardFoutresponses() throws Exception {
        String json = given()
                .accept(ContentType.JSON)
                .when().get("/openapi.json?format=JSON")
                .then()
                .statusCode(200)
                .extract().asString();

        JsonNode document = new ObjectMapper().readTree(json);
        JsonNode paths = document.get("paths");
        Assertions.assertTrue(paths != null && paths.size() > 0, "Het contract bevat geen paths");

        List<String> bevindingen = new ArrayList<>();
        int gecontroleerd = 0;

        var padNamen = paths.fieldNames();

        while (padNamen.hasNext()) {
            String pad = padNamen.next();

            for (String methode : HTTP_METHODEN) {
                JsonNode operatie = paths.get(pad).get(methode);

                if (operatie == null) {
                    continue;
                }

                gecontroleerd++;
                String plek = methode.toUpperCase() + " " + pad;
                controleer(document, operatie, "500", "HttpProblem", plek, bevindingen);
                controleer(document, operatie, "400", "HttpValidationProblem", plek, bevindingen);

                for (String foutcode : OVERIGE_FOUTCODES) {
                    controleer(document, operatie, foutcode, "HttpProblem", plek, bevindingen);
                }
            }
        }

        Assertions.assertTrue(gecontroleerd >= MINIMAAL_AANTAL_OPERATIES,
                "Slechts " + gecontroleerd + " operaties gecontroleerd, minimaal "
                        + MINIMAAL_AANTAL_OPERATIES + " verwacht");
        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }

    /**
     * Alleen de 500 is verplicht: die kan overal ontstaan. De overige codes zijn optioneel —
     * een operatie waarvan geen enkele parameter of body een bean-validatiefout kan opleveren
     * documenteert terecht geen 400 — maar staan ze er, dan moeten ze naar het juiste schema
     * wijzen.
     */
    private static void controleer(
            JsonNode document,
            JsonNode operatie,
            String statuscode,
            String verwachtSchema,
            String plek,
            List<String> bevindingen) {

        JsonNode respons = operatie.path("responses").get(statuscode);

        if (respons == null) {
            if (statuscode.equals("500")) {
                bevindingen.add(plek + " documenteert geen " + statuscode);
            }

            return;
        }

        JsonNode opgelost = resolveer(document, respons);
        String schemaRef = opgelost.path("content").path("application/problem+json").path("schema").path("$ref").asText("");

        if (!schemaRef.endsWith("/" + verwachtSchema)) {
            bevindingen.add(plek + " " + statuscode + " verwijst naar '" + schemaRef
                    + "' in plaats van " + verwachtSchema);
        }
    }

    /**
     * Volgt één {@code $ref} naar {@code components/responses}. Dieper nesten komt in dit
     * contract niet voor; gebeurt dat ooit wel, dan valt de assertie om in plaats van stil
     * het verkeerde te controleren.
     */
    private static JsonNode resolveer(JsonNode document, JsonNode respons) {
        String ref = respons.path("$ref").asText("");

        if (ref.isEmpty()) {
            return respons;
        }

        String naam = ref.substring(ref.lastIndexOf('/') + 1);

        return document.path("components").path("responses").path(naam);
    }
}
