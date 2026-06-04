package nl.rijksoverheid.moz.contract;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.helper.OpenApiValidationTest;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static nl.rijksoverheid.moz.common.IdentificatieType.KVK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.*;

/**
 * Contract tests that validate every request and response against the live OpenAPI spec.
 * A failure here means the implementation drifted from what the API spec documents.
 */
@QuarkusTest
public class OpenApiSchemaValidationTest extends OpenApiValidationTest {

    @InjectMock
    EmailVerificatieService emailVerificatieService;

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

    @Test
    void getPartij_responseMustMatchSpec() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "123456789"));
            p.persist();
        });

        var request = new PartijRequest();
        request.identificatieType = BSN;
        request.identificatieNummer = "123456789";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(OK);
    }

    @Test
    void getPartij_notFound_responseMustMatchSpec() {
        var request = new PartijRequest();
        request.identificatieType = BSN;
        request.identificatieNummer = "999999999";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void addVoorkeur_created_responseMustMatchSpec() {
        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        var request = new VoorkeurRequest();
        request.identificatieType = BSN;
        request.identificatieNummer = "123456789";
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(CREATED);
    }

    @Test
    void addVoorkeur_duplicate_returns200_responseMustMatchSpec() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "123456789"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
        });

        var request = new VoorkeurRequest();
        request.identificatieType = BSN;
        request.identificatieNummer = "123456789";
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "en";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(OK);
    }

    @Test
    void addContactgegeven_created_responseMustMatchSpec() {
        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        var request = new ContactgegevenRequest();
        request.identificatieType = BSN;
        request.identificatieNummer = "123456789";
        request.type = ContactType.Email;
        request.waarde = "test@example.com";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(CREATED);
    }

    @Test
    void getDienstverlener_responseMustMatchSpec() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener d = new Dienstverlener();
            d.setNaam("TestDV");
            d.persist();
        });

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/TestDV")
                .then()
                .statusCode(OK);
    }

    @Test
    void getDienstverlener_notFound_responseMustMatchSpec() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .get("/api/profielservice/v1/dienstverlener/Onbekend")
                .then()
                .statusCode(NOT_FOUND);
    }
}
