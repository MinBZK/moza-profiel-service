package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nl.rijksoverheid.moz.DatabaseCleanup;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

/**
 * De dienstverlenernaam in het pad moet aan dezelfde pattern voldoen als in de body
 * (MinBZK/MijnOverheidZakelijk#967). Zonder die constraint hangt het van de gekozen route af hoe
 * dezelfde naam wordt beoordeeld: de bodyroute geeft een 400 met een violations-lijst, de padroute
 * een 404 die naar het verkeerde probleem wijst.
 *
 * <p>{@code urlEncodingEnabled(false)}: anders codeert RestAssured het procentteken nog een keer
 * en komt {@code %2520} bij de server aan, waarmee de test een gewone naam zou versturen.
 *
 * <p>{@code validationFilter} hangt alleen aan de contractgeldige requests; zie
 * {@code BlancoWaardenIntegrationTest} voor waarom de negatieve gevallen hem missen.
 */
@QuarkusTest
class PadparameterValidatieIntegrationTest extends OpenApiValidationTest {

    private static final String DIENSTVERLENER = "/api/profielservice/v1/dienstverlener/";

    @AfterEach
    void tearDown() {
        DatabaseCleanup.wipe();
    }

    /** Een spatie, een tab, en een naam met een newline erin. */
    @ParameterizedTest
    @ValueSource(strings = {"%20", "%09", "naam%0Aregel2"})
    void dienstToevoegenOnderOngeldigePadnaamWordtAfgewezen(String padnaam) {
        given()
                .urlEncodingEnabled(false)
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"DienstA\"}")
                .when().post(DIENSTVERLENER + padnaam + "/diensten")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("dienstverlenerNaam"));
    }

    /**
     * De leesactie erlangs. Die kon nooit data bederven — een onbekende naam gaf een 404 — maar
     * zolang de constraint hier ontbreekt hangt het van de gekozen route af of dezelfde naam
     * geldig heet te zijn.
     */
    @ParameterizedTest
    @ValueSource(strings = {"%20", "%09", "naam%0Aregel2"})
    void dienstverlenerOpvragenOnderOngeldigePadnaamWordtAfgewezen(String padnaam) {
        given()
                .urlEncodingEnabled(false)
                .when().get(DIENSTVERLENER + padnaam)
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("naam"));
    }

    /**
     * De tegenhanger: een naam met spaties erin is gewoon geldig. Zou de pattern per ongeluk op
     * {@code \S+} uitkomen, dan valt deze om terwijl de negatieve gevallen groen blijven.
     */
    @Test
    void gewonePadnaamKomtGewoonDoor() {
        // De dienstverlener moet bestaan: sinds #967 maakt deze POST hem niet meer impliciet aan.
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Gemeente Amsterdam");
            dv.persist();
        });

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"Parkeervergunning\"}")
                .when().post(DIENSTVERLENER + "Gemeente Amsterdam/diensten")
                .then()
                .statusCode(CREATED);

        given()
                .filter(validationFilter)
                .when().get(DIENSTVERLENER + "Gemeente Amsterdam")
                .then()
                .statusCode(OK);
    }
}
