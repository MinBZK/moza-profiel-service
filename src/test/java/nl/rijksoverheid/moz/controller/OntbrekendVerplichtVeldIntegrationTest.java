package nl.rijksoverheid.moz.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;

/**
 * Een ontbrekend verplicht veld levert een andere foutvorm op dan een veld dat wél aanwezig is
 * maar de validatie niet haalt. De generator zet sinds 7.25.0 op elke DTO een
 * {@code @JsonCreator} met {@code @JsonProperty(required = true)} voor de verplichte velden,
 * waardoor Jackson het verzoek afwijst vóór de bean-validatie: geen {@code violations}-lijst,
 * maar een {@code detail} met het eerste ontbrekende veld in {@code field}.
 *
 * <p>Beide vormen zijn 400 en beide passen op {@code HttpValidationProblem}, dat
 * {@code additionalProperties: true} heeft en geen {@code required} kent. De contractvalidatie
 * ziet het verschil dus niet; deze test legt het vast. De andere kant — een verplicht veld dat
 * er wél staat maar blanco is, en dus op de bean-validatie valt — staat in
 * {@link BlancoWaardenIntegrationTest}.
 *
 * <p>Geen {@code validationFilter}: die valideert ook het request, client-side, en zou een
 * opzettelijk onvolledige body weigeren voordat hij de server bereikt.
 */
@QuarkusTest
class OntbrekendVerplichtVeldIntegrationTest {

    /**
     * De body mist {@code type} én {@code waarde}. Jackson meldt alleen de eerste; dat verschil
     * met de bean-validatie, die alle overtredingen tegelijk teruggeeft, hoort hier vastgelegd
     * te zijn.
     */
    @Test
    void contactgegevenZonderVerplichteVeldenGeeftMalformedRequestBody() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"identificatieType\":\"KVK\",\"identificatieNummer\":\"12345678\"}")
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("detail", equalTo("Malformed request body"))
                .body("field", equalTo("type"))
                .body("violations", nullValue());
    }

    @Test
    void dienstverlenerZonderNaamGeeftMalformedRequestBody() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("detail", equalTo("Malformed request body"))
                .body("field", equalTo("naam"))
                .body("violations", nullValue());
    }
}
