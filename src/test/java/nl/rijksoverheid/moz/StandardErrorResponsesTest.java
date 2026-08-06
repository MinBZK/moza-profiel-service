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
 * <p>Twee invarianten. Elke operatie documenteert een 500 die naar {@code HttpProblem} wijst.
 * En elke 400 wijst naar {@code HttpValidationProblem}, want bij bean-validatie levert de
 * applicatie een {@code violations}-lijst; de twee schema's zijn structureel niet van elkaar
 * te onderscheiden (geen {@code required}, {@code additionalProperties: true}), dus de
 * contractvalidatie in {@link nl.rijksoverheid.moz.controller.OpenApiValidationTest} merkt
 * het niet als er per ongeluk naar {@code HttpProblem} verwezen wordt.
 */
@QuarkusTest
class StandardErrorResponsesTest {

    private static final List<String> HTTP_METHODEN = List.of("get", "post", "put", "patch", "delete");

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
            }
        }

        Assertions.assertTrue(gecontroleerd > 0, "Geen operaties gevonden om te controleren");
        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }

    /**
     * Een 400 is niet op elke operatie verplicht — een endpoint zonder request body en
     * zonder parameters kan er geen produceren — maar staat hij er, dan moet hij naar het
     * juiste schema wijzen. Een 500 is dat wel: die kan overal ontstaan.
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
