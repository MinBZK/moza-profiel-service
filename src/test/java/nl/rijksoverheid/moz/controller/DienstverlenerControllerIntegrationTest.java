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
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.*;


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
                .body("naam", org.hamcrest.Matchers.equalTo("Test"))
                .body("beschrijving", org.hamcrest.Matchers.equalTo("Een test dienstverlener"));
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
                .body("diensten.size()", org.hamcrest.Matchers.equalTo(1))
                .body("diensten[0].naam", org.hamcrest.Matchers.equalTo("TestDienst"));
    }

    @Test
    void getDienstverlener_NotFound() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Test")
                .then()
                .statusCode(NOT_FOUND);
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
                .header("Location", org.hamcrest.Matchers.endsWith("/dienstverlener/Test"));
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
                .header("Location", org.hamcrest.Matchers.endsWith("/dienstverlener/Test"));
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
