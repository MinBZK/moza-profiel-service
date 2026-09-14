package nl.rijksoverheid.moz;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Bewaakt de metadata van het contract dat op /openapi.json wordt gepubliceerd.
 * Dat is het statische bestand META-INF/openapi.yaml: annotatie-scanning
 * staat uit, dus niets in de code corrigeert deze velden nog.
 */
@QuarkusTest
class OpenApiMetadataTest {

    /**
     * De versie staat op twee plekken: in het contract en in {@link ApiVersion#CURRENT},
     * dat de API-Version-header vult. Ze horen gelijk te blijven.
     */
    @Test
    void contractVersieIsGelijkAanApiVersion() {
        given()
                .accept(ContentType.JSON)
                .when().get("/openapi.json?format=JSON")
                .then()
                .statusCode(200)
                .body("info.version", equalTo(ApiVersion.CURRENT));
    }

    /**
     * ADR /core/doc-openapi-contact vereist contactgegevens; de Spectral-regels
     * nlgov:info-contact-fields-exist en nlgov:semver controleren hierop.
     */
    @Test
    void contractBevatVerplichteInfoVelden() {
        given()
                .accept(ContentType.JSON)
                .when().get("/openapi.json?format=JSON")
                .then()
                .statusCode(200)
                .body("info.title", equalTo("MOZa Profiel Service API"))
                .body("info.contact.name", equalTo("MijnOverheid Zakelijk Team"))
                .body("info.contact.email", equalTo("moza@minbzk.nl"))
                .body("info.contact.url", equalTo("https://docs.mijnoverheidzakelijk.nl"))
                .body("info.license.name", equalTo("EUPL-1.2"));
    }

    /**
     * Geen servers-array: clients en Swagger UI vallen dan terug op same-origin, zodat
     * "Try it out" tegen de omgeving praat waar het document vandaan komt — nodig voor de
     * ZAD-previews, zie docs/zad-deploy.md. Een geëxporteerd bind-adres als 0.0.0.0:8080
     * hoort hier al helemaal niet in.
     */
    @Test
    void contractAdverteertGeenServers() {
        given()
                .accept(ContentType.JSON)
                .when().get("/openapi.json?format=JSON")
                .then()
                .statusCode(200)
                .body("servers", nullValue());
    }
}
