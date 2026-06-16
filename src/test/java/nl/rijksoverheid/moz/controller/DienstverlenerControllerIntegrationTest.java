package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CONFLICT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;


@QuarkusTest
public class DienstverlenerControllerIntegrationTest extends OpenApiValidationTest {

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
    void getDienstverlener_Success() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener d = new Dienstverlener();
            d.setNaam("Test");
            d.setBeschrijving("Een test dienstverlener");
            d.persist();
        });

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Test")
                .then()
                .statusCode(OK)
                .body("naam", equalTo("Test"))
                .body("beschrijving", equalTo("Een test dienstverlener"));
    }

    @Test
    void getDienstverlener_IncludesDiensten() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Test");
            dv.persist();

            Dienst dienst = new Dienst();
            dienst.setNaam("TestDienst");
            dienst.persist();

            new DienstverlenerDienst(dv, dienst).persist();
        });

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Test")
                .then()
                .statusCode(OK)
                .body("diensten.size()", equalTo(1))
                .body("diensten[0].naam", equalTo("TestDienst"));
    }

    @Test
    void getDienstverlener_NotFound() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Test")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Dienstverlener niet gevonden"));
    }

    @Test
    void addDienstverlener_Success() {
        DienstverlenerRequest request = new DienstverlenerRequest();
        request.naam = "Test";
        request.beschrijving = "Test beschrijving";
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(CREATED)
                .header("Location", endsWith("/dienstverlener/Test"));
    }

    @Test
    void addDienstverlener_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(BAD_REQUEST);
    }


    @Test
    void addDienstToDienstverlener_Success() {
        DienstRequest request = new DienstRequest();
        request.naam = "TestDienst";
        request.beschrijving = "Optionele toelichting";

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener d = new Dienstverlener();
            d.setNaam("Test");
            d.persist();
        });

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener/Test/diensten")
                .then()
                .statusCode(CREATED)
                .header("Location", containsString("/dienstverlener/Test/diensten/"));
    }

    @Test
    void addDienstToDienstverlener_ExistingDienstWithDifferentBeschrijving_Returns409() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Test");
            dv.persist();
            Dienst d = new Dienst();
            d.setNaam("TestDienst");
            d.setBeschrijving("originele beschrijving");
            d.persist();
            new DienstverlenerDienst(dv, d).persist();
        });

        DienstRequest request = new DienstRequest();
        request.naam = "TestDienst";
        request.beschrijving = "andere beschrijving";

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener/Test/diensten")
                .then()
                .statusCode(CONFLICT)
                .contentType("application/problem+json")
                .body("title", equalTo("Conflict"));
    }

    @Test
    void addDienstToDienstverlener_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/dienstverlener/Test/diensten")
                .then()
                .statusCode(BAD_REQUEST);
    }
}
