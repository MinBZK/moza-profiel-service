package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.*;


@QuarkusTest
public class DienstverlenerControllerTest {

    @AfterEach
    @Transactional
    void tearDown() {
        Contactgegeven.deleteAll();
        Scope.deleteAll();
        Dienst.deleteAll();
        Dienstverlener.deleteAll();
    }

    @Test
    void getDienstenDienstverlener_Success() {

        AtomicLong id = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener d = new Dienstverlener();
            d.setNaam("Test");
            d.setOin("123456789");
            d.persist();

            Dienst dienst = new Dienst();
            dienst.setBeschrijving("Test");
            dienst.setDienstverlener(d);
            dienst.persist();

            id.set(dienst.id);
        });

        given()
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Test")
                .then()
                .statusCode(OK)
                .body("naam", org.hamcrest.Matchers.equalTo("Test"))
                .body("oin", org.hamcrest.Matchers.equalTo("123456789"))
                .body("diensten[0].id", org.hamcrest.Matchers.equalTo((int) id.get()))
                .body("diensten[0].beschrijving", org.hamcrest.Matchers.equalTo("Test"));

    }

    @Test
    void getDienstenDienstverlener_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Test")
                .then()
                .statusCode(NOT_FOUND);

    }


    @Test
    void addDienstverlener_Success() {
        DienstverlenerRequest request = new DienstverlenerRequest();
        request.naam = "Test";
        request.oin = "123456789";
        given()
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
        request.beschrijving = "Test";

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener d = new Dienstverlener();
            d.setNaam("Test");
            d.setOin("123456789");
            d.persist();
        });

        given()
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
