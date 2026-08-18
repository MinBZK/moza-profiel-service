package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.api.generated.model.DienstRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerRequest;
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
        request.setNaam("Test");
        request.setBeschrijving("Test beschrijving");
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(CREATED)
                .header("Location", endsWith("/dienstverlener/Test"));
    }

    /**
     * Asserteert bewust op de problem-body en niet alleen op de statuscode. De lege body wordt
     * door {@code RequireBodyReaderInterceptor} afgewezen; de controller heeft er geen eigen
     * null-check meer naast staan. Zonder deze assertie zou het verdwijnen van dat mechanisme
     * — of een NPE die als 500 eindigt — niet van een nette 400 te onderscheiden zijn.
     */
    @Test
    void addDienstverlener_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body(containsString("Request body mag niet leeg zijn"));
    }

    /**
     * Een tweede POST met een afwijkende beschrijving gaf eerder een 201 met de óude beschrijving
     * erin: {@code findOrCreateDienstverlener} las het veld niet als de rij al bestond, dus de
     * schrijfactie verdween zonder fout. Het contract documenteerde intussen wel een 409 die
     * niemand gooide. Nu botst het, net als bij een dienst met een andere beschrijving.
     */
    @Test
    void addDienstverlener_ExistingWithDifferentBeschrijving_Returns409() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Test");
            dv.setBeschrijving("originele beschrijving");
            dv.persist();
        });

        DienstverlenerRequest request = new DienstverlenerRequest();
        request.setNaam("Test");
        request.setBeschrijving("andere beschrijving");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(CONFLICT)
                .contentType("application/problem+json")
                .body("title", equalTo("Conflict"));
    }

    /**
     * Tegenhanger van de test hierboven: dezelfde beschrijving, en een weggelaten beschrijving,
     * horen géén conflict op te leveren. Zonder deze test zou een controle die élke tweede POST
     * afwijst er net zo groen uitzien — en {@code addDienstToDienstverlener} maakt de
     * dienstverlener aan met {@code beschrijving == null}, dus die route zou erdoor breken.
     */
    @Test
    void addDienstverlener_ExistingWithSameBeschrijving_Returns201() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Test");
            dv.setBeschrijving("originele beschrijving");
            dv.persist();
        });

        DienstverlenerRequest gelijk = new DienstverlenerRequest();
        gelijk.setNaam("Test");
        gelijk.setBeschrijving("originele beschrijving");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(gelijk)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(CREATED)
                .body("beschrijving", equalTo("originele beschrijving"));

        DienstverlenerRequest zonder = new DienstverlenerRequest();
        zonder.setNaam("Test");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(zonder)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(CREATED)
                .body("beschrijving", equalTo("originele beschrijving"));
    }

    /**
     * Het antwoord vulde de dienstenlijst met {@code List.of()}. Bij een dienstverlener die al
     * bestond beweerde de 201 dus dat hij geen diensten had, terwijl GET op dezelfde resource ze
     * wél teruggaf.
     */
    @Test
    void addDienstverlener_ExistingWithDiensten_ReturnsDiensten() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Test");
            dv.persist();

            Dienst dienst = new Dienst();
            dienst.setNaam("TestDienst");
            dienst.persist();

            new DienstverlenerDienst(dv, dienst).persist();
        });

        DienstverlenerRequest request = new DienstverlenerRequest();
        request.setNaam("Test");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(CREATED)
                .body("diensten.size()", equalTo(1))
                .body("diensten[0].naam", equalTo("TestDienst"));
    }


    @Test
    void addDienstToDienstverlener_Success() {
        DienstRequest request = new DienstRequest();
        request.setNaam("TestDienst");
        request.setBeschrijving("Optionele toelichting");

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
        request.setNaam("TestDienst");
        request.setBeschrijving("andere beschrijving");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/dienstverlener/Test/diensten")
                .then()
                .statusCode(CONFLICT)
                .contentType("application/problem+json")
                .body("title", equalTo("Conflict"));
    }

    /** Zie {@link #addDienstverlener_BadRequest()} voor waarom de body meegecontroleerd wordt. */
    @Test
    void addDienstToDienstverlener_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/dienstverlener/Test/diensten")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body(containsString("Request body mag niet leeg zijn"));
    }
}
