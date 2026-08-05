package nl.rijksoverheid.moz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static io.restassured.RestAssured.given;

/**
 * Bewaakt dat het contract in META-INF/openapi.yaml ook werkelijk is wat de applicatie
 * op /openapi.json publiceert. Sinds #651 is het contract de bron: annotatie-scanning
 * staat uit en het bestand wordt statisch geserveerd. Zou iemand die schakelaar omzetten
 * of het bestand verplaatsen, dan gaan contract en gepubliceerd document stilzwijgend
 * uiteenlopen — precies het probleem dat contract-first moet voorkomen.
 */
@QuarkusTest
class OpenApiContractDriftTest {

    @Test
    void gepubliceerdDocumentIsGelijkAanHetContract() throws Exception {
        JsonNode contract;

        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            contract = new ObjectMapper(new YAMLFactory()).readTree(in);
        }

        String gepubliceerd = given()
                .accept(ContentType.JSON)
                .when().get("/openapi.json?format=JSON")
                .then()
                .statusCode(200)
                .extract().asString();

        Assertions.assertEquals(contract, new ObjectMapper().readTree(gepubliceerd),
                "Het gepubliceerde document wijkt af van META-INF/openapi.yaml");
    }
}
