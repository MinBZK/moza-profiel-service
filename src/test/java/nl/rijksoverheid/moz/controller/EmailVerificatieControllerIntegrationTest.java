package nl.rijksoverheid.moz.controller;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.SERVICE_UNAVAILABLE;

@QuarkusTest
public class EmailVerificatieControllerIntegrationTest extends OpenApiValidationTest {

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    @Test
    void postEmailVerificatie_Success() {
        Mockito.doReturn(true).when(emailVerificatieService).verifieerEmail(Mockito.any());

        var body = new EmailVerificatieRequest();
        body.email = "email@email.com";
        body.verificatieCode = "123456";
        body.identificatieNummer = "123456782";
        body.identificatieType = IdentificatieType.BSN;

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/api/profielservice/v1/emailverificatie")
                .then()
                .statusCode(OK);
    }

    @Test
    void postEmailVerificatie_BadRequest() {
        Mockito.doReturn(false).when(emailVerificatieService).verifieerEmail(Mockito.any());

        var body = new EmailVerificatieRequest();
        body.email = "email@email.com";
        body.verificatieCode = "123456";
        body.identificatieNummer = "123456782";
        body.identificatieType = IdentificatieType.BSN;

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/api/profielservice/v1/emailverificatie")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void postEmailVerificatieCodeAanvraag_Success() {
        Mockito.doReturn(OK).when(emailVerificatieService).vraagEmailVerificatieCodeAan(Mockito.any());

        var body = new EmailVerificatieCodeAanvraagRequest();
        body.email = "email@email.com";
        body.identificatieNummer = "123";
        body.identificatieType = IdentificatieType.BSN;

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/api/profielservice/v1/emailverificatie/code")
                .then()
                .statusCode(OK);
    }

    @Test
    void postEmailVerificatieCodeAanvraag_NotFound() {
        Mockito.doReturn(NOT_FOUND).when(emailVerificatieService).vraagEmailVerificatieCodeAan(Mockito.any());

        var body = new EmailVerificatieCodeAanvraagRequest();
        body.email = "email@email.com";
        body.identificatieNummer = "123";
        body.identificatieType = IdentificatieType.BSN;

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/api/profielservice/v1/emailverificatie/code")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Partij of contactgegeven niet gevonden"));
    }

    @Test
    void postEmailVerificatieCodeAanvraag_ServiceUnavailable() {
        Mockito.doReturn(SERVICE_UNAVAILABLE).when(emailVerificatieService).vraagEmailVerificatieCodeAan(Mockito.any());

        var body = new EmailVerificatieCodeAanvraagRequest();
        body.email = "email@email.com";
        body.identificatieNummer = "123";
        body.identificatieType = IdentificatieType.BSN;

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/api/profielservice/v1/emailverificatie/code")
                .then()
                .statusCode(SERVICE_UNAVAILABLE);
    }
}
