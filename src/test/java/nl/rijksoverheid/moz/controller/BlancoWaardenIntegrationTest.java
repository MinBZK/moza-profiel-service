package nl.rijksoverheid.moz.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;

/**
 * Verplichte tekstvelden mogen niet uit alleen witruimte bestaan. Het contract legt dat
 * vast met {@code pattern: "[\s\S]*\S[\s\S]*"} op de betreffende velden, waaruit de generator
 * een {@code @Pattern} op de DTO maakt. Zonder die pattern liet {@code @NotNull} een lege
 * string ongehinderd door (MinBZK/MijnOverheidZakelijk#766).
 *
 * <p>De expressie is bewust niet {@code ".*\S.*"} en ook niet {@code "(?s).*\S.*"}. Jakarta
 * {@code @Pattern} doet een full-match waarbij {@code .} geen newline matcht, dus de eerste
 * variant weigert elke meerregelige waarde; de tweede repareert dat voor Java maar is geen
 * geldige ECMA 262-regex, waardoor het gepubliceerde contract onbruikbaar wordt voor
 * consumers en de contractvalidatie in de testsuite afbreekt. {@code [\s\S]} klopt in beide.
 *
 * <p>{@code validationFilter} hangt alleen aan de tests die een contractgeldig request sturen.
 * De filter valideert namelijk ook het request, en doet dat client-side: een opzettelijk
 * ongeldige body wordt geweigerd voordat hij de server bereikt, waarmee de negatieve test
 * niet meer test wat hij moet testen. Die tests controleren de responsevorm daarom zelf.
 */
@QuarkusTest
class BlancoWaardenIntegrationTest extends OpenApiValidationTest {

    @AfterEach
    @Transactional
    void tearDown() {
        ScopeContactgegeven.deleteAll();
        ScopeVoorkeur.deleteAll();
        Contactgegeven.deleteAll();
        DienstverlenerDienst.deleteAll();
        Dienst.deleteAll();
        Dienstverlener.deleteAll();
    }

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
                .contentType("application/problem+json")
                .body("violations.field", hasItem("waarde"));
    }

    @Test
    void contactgegevenMetLegeWaardeWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":""}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
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
                .contentType("application/problem+json")
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
                .contentType("application/problem+json")
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
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"Gemeente Amsterdam\",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener/")
                .then()
                .statusCode(201)
                .body("naam", equalTo("Gemeente Amsterdam"));
    }

    /**
     * JSON Schema {@code pattern} zoekt een deelstring, maar Jakarta {@code @Pattern} doet een
     * full-match waarbij {@code .} standaard geen newline matcht. Met {@code ".*\S.*"} zou een
     * meerregelige waarde dus 400 geven terwijl het gepubliceerde contract die toestaat.
     */
    @Test
    void contactgegevenMetMeerregeligeWaardeWordtGeaccepteerd() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"ApplicatieId","waarde":"regel1\\nregel2"}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(201);
    }
}
