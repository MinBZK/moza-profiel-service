package nl.rijksoverheid.moz.controller;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;

/**
 * Verplichte tekstvelden moeten één regel zijn en mogen niet uit alleen witruimte bestaan.
 * Het contract legt dat vast met {@code pattern: "^[^\r\n]*\S[^\r\n]*$"} op de betreffende
 * velden, waaruit de generator een {@code @Pattern} op de DTO maakt. Zonder pattern liet
 * {@code @NotNull} een lege string ongehinderd door (MinBZK/MijnOverheidZakelijk#766).
 *
 * <p>De expressie is bewust geankerd. JSON Schema {@code pattern} zoekt een deelstring,
 * terwijl Jakarta {@code @Pattern} een full-match doet; zonder {@code ^} en {@code $} keurt
 * het gepubliceerde contract dus waarden goed die de server weigert. Twee alternatieven zijn
 * afgevallen: {@code ".*\S.*"} laat die divergentie bestaan, en {@code "(?s).*\S.*"} heft hem
 * op voor Java maar is geen geldige ECMA 262-regex — daarmee wordt het contract onbruikbaar
 * voor consumers en valt de contractvalidatie in deze suite op élk endpoint om.
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
        Voorkeur.deleteAll();
        DienstverlenerDienst.deleteAll();
        Dienst.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
        Dienstverlener.deleteAll();
    }

    /**
     * Witruimte, leeg en meerregelig gaan alle drie langs dezelfde pattern. De laatste is
     * de reden dat de expressie geankerd is en niet op {@code .*\S.*} steunt.
     */
    @ParameterizedTest
    @ValueSource(strings = {"   ", "", "regel1\\nregel2"})
    void contactgegevenMetOngeldigeWaardeWordtAfgewezen(String waarde) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"%s"}
                        """.formatted(waarde))
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("waarde"));
    }

    /**
     * Een identificatienummer met een afsluitende CR zou anders als aparte partij worden
     * opgeslagen, waarna de echte partij op die sleutel niet meer te vinden is.
     */
    @Test
    void contactgegevenMetMeerregeligIdentificatieNummerWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104\\r",
                         "type":"Email","waarde":"test@example.com"}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("identificatieNummer"));
    }

    @Test
    void dienstverlenerMetBlancoNaamWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"   \",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener")
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
     * Een blanco scope kwam eerder ongehinderd langs de validatie en strandde pas op een
     * 404 "Dienstverlener bestaat niet" — een foutmelding die naar het verkeerde probleem
     * wijst. Nu draagt {@code ScopeRequest} dezelfde pattern als de rest.
     */
    @Test
    void contactgegevenMetBlancoScopeWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"test@example.com",
                         "scope":{"dienstverlenerNaam":"   "}}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("scope.dienstverlenerNaam"));
    }

    /**
     * De scope-velden op PartijRequest zaten eerder zonder pattern. Een blanco waarde gold
     * daar als "wel opgegeven", waarna er op een lege dienstverlenernaam werd gefilterd en de
     * aanroeper een 200 kreeg met een leeg profiel voor een partij die gewoon bestaat.
     */
    @Test
    void partijMetBlancoDienstverlenerWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "dienstverlener":"   "}
                        """)
                .when().post("/api/profielservice/v1/partij")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("dienstverlener"));
    }

    /**
     * Zelfde klasse fout op TeVerwijderenOpRequest: een blanco dienstNaam haalde de
     * naamvergelijking in requireDienstverlenerAuthorized niet en leverde een 403 op, terwijl
     * er niets mis was met de bevoegdheid.
     */
    @Test
    void teVerwijderenOpMetBlancoDienstNaamWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"id":"00000000-0000-0000-0000-000000000001",
                         "identificatieType":"BSN","identificatieNummer":"111111104",
                         "dienstverlenerNaam":"Gemeente Amsterdam","dienstNaam":"   ",
                         "teVerwijderenOp":"2099-01-01T00:00:00Z"}
                        """)
                .when().patch("/api/profielservice/v1/contactgegeven/te-verwijderen-op")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("dienstNaam"));
    }

    /**
     * Positieve tegenhanger: een gewone waarde met een spatie erin hoort door de pattern te
     * komen. Zonder deze test zou een expressie die alles weigert er net zo groen uitzien.
     */
    @Test
    void dienstverlenerMetNormaleNaamWordtGeaccepteerd() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"Gemeente Amsterdam\",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(201)
                .body("naam", equalTo("Gemeente Amsterdam"));
    }
}
