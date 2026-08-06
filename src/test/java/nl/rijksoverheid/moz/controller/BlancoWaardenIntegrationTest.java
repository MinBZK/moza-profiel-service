package nl.rijksoverheid.moz.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;

/**
 * Verplichte tekstvelden mogen niet uit alleen witruimte bestaan. Het contract legt dat
 * vast met {@code pattern: ".*\S.*"} op de betreffende velden, waaruit de generator een
 * {@code @Pattern} op de DTO maakt. Zonder die pattern liet {@code @NotNull} een lege
 * string ongehinderd door (MinBZK/MijnOverheidZakelijk#766).
 */
@QuarkusTest
class BlancoWaardenIntegrationTest extends OpenApiValidationTest {

    @Test
    void contactgegevenMetBlancoWaardeWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"   "}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("waarde"));
    }

    @Test
    void dienstverlenerMetBlancoNaamWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"   \",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener/")
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("naam"));
    }

    @Test
    void voorkeurMetBlancoWaardeWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "voorkeurType":"WebsiteTaal","waarde":" "}
                        """)
                .when().post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("waarde"));
    }

    /**
     * Een gevulde waarde van meer dan één teken hoort gewoon door de pattern te komen;
     * dit legt vast dat de regex een deelstring toestaat en niet per ongeluk een
     * full-match op één niet-witruimteteken afdwingt.
     */
    @Test
    void dienstverlenerMetNormaleNaamWordtGeaccepteerd() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"Gemeente Amsterdam\",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener/")
                .then()
                .statusCode(201)
                .body("naam", equalTo("Gemeente Amsterdam"));
    }
}
