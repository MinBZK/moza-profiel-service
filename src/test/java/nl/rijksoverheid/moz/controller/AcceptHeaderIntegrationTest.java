package nl.rijksoverheid.moz.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_ACCEPTABLE;

/**
 * Legt vast welke {@code Accept}-headers de API bedient, en waarom dat per operatie verschilt.
 * <p>
 * Sinds de resources via gegenereerde interfaces lopen (#751) komt {@code @Produces} per operatie
 * uit de mediatypes van de responses in het contract, en niet meer uit één {@code @Produces} op de
 * controllerklasse. JAX-RS onderhandelt op wat er gedeclareerd is, vóórdat de resource-methode
 * draait. Een operatie waarvan de succesrespons geen body heeft, declareert daardoor alleen
 * {@code application/problem+json} — van de foutresponses — en wijst een verzoek dat uitsluitend
 * {@code application/json} accepteert af met een 406, ook al valt er niets te onderhandelen.
 * <p>
 * Dat is een bewuste keuze: het contract eerlijk houden weegt zwaarder. {@code content:
 * application/json} op zo'n succesrespons zou het wél oplossen, maar declareert een body die niet
 * bestaat; die declaraties zijn eerder juist verwijderd en {@code OpenApiValidationTest} bewaakt
 * dat. Aanroepers sturen {@code * / *}, {@code application/problem+json} of, zoals de gangbare
 * clients doen, een lijst met een wildcard erin.
 * <p>
 * Deze test staat er zodat die keuze zichtbaar blijft: verandert het gedrag, dan valt hij om in
 * plaats van dat een aanroeper er in productie tegenaan loopt.
 */
@QuarkusTest
class AcceptHeaderIntegrationTest {

    private static final String BODY = """
            {"identificatieType":"BSN","identificatieNummer":"111111104"}
            """;

    /** Operaties zonder body in de succesrespons: die declareren alleen problem+json. */
    @ParameterizedTest(name = "{0} {1} met Accept: application/json geeft 406")
    @CsvSource({
            "PUT,   /api/profielservice/v1/contactgegeven",
            "PATCH, /api/profielservice/v1/contactgegeven/te-verwijderen-op",
            "PUT,   /api/profielservice/v1/voorkeur",
            "PATCH, /api/profielservice/v1/voorkeur/te-verwijderen-op",
            "POST,  /api/profielservice/v1/emailverificatie",
            "POST,  /api/profielservice/v1/emailverificatie/code",
    })
    void operatieZonderResponsebodyWeigertAlleenApplicationJson(String methode, String pad) {
        Assertions.assertEquals(NOT_ACCEPTABLE, status(methode.trim(), pad.trim(), "application/json"));

        // Met een mediatype dat de operatie wél declareert komt het verzoek gewoon binnen.
        Assertions.assertNotEquals(NOT_ACCEPTABLE,
                status(methode.trim(), pad.trim(), "application/problem+json"));
        Assertions.assertNotEquals(NOT_ACCEPTABLE, status(methode.trim(), pad.trim(), "*/*"));
    }

    @ParameterizedTest(name = "DELETE {0} met Accept: application/json geeft 406")
    @CsvSource({
            "/api/profielservice/v1/contactgegeven/",
            "/api/profielservice/v1/voorkeur/",
    })
    void deleteWeigertAlleenApplicationJson(String basispad) {
        String pad = basispad + UUID.randomUUID();

        Assertions.assertEquals(NOT_ACCEPTABLE, status("DELETE", pad, "application/json"));
        Assertions.assertNotEquals(NOT_ACCEPTABLE, status("DELETE", pad, "*/*"));
    }

    /**
     * Operaties die wél een JSON-body teruggeven declareren application/json en bedienen een
     * aanroeper die daar exclusief om vraagt. Zonder deze kant zou de test hierboven net zo goed
     * kunnen slagen doordat de hele API onbereikbaar is geworden.
     */
    @ParameterizedTest(name = "{0} {1} met Accept: application/json werkt")
    @CsvSource({
            "POST, /api/profielservice/v1/partij",
            "POST, /api/profielservice/v1/partijen/bulk",
            "POST, /api/profielservice/v1/contactgegeven",
            "POST, /api/profielservice/v1/voorkeur",
            "POST, /api/profielservice/v1/dienstverlener",
    })
    void operatieMetResponsebodyAccepteertApplicationJson(String methode, String pad) {
        Assertions.assertNotEquals(NOT_ACCEPTABLE,
                status(methode.trim(), pad.trim(), "application/json"));
    }

    private static int status(String methode, String pad, String accept) {
        return given()
                .accept(accept)
                .contentType(ContentType.JSON)
                .body(BODY)
                .when().request(methode, pad)
                .then().extract().statusCode();
    }
}
