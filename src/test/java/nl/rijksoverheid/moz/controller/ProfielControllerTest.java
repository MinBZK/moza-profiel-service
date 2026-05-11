package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.entity.*;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static nl.rijksoverheid.moz.common.IdentificatieType.KVK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.*;

@QuarkusTest
public class ProfielControllerTest {

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    @BeforeEach
    @Transactional
    void setup() {
        // Mock the email verification service to return a reference ID
        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        Contactgegeven.deleteAll();
        Voorkeur.deleteAll();
        Scope.deleteAll();
        Dienst.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
        Dienstverlener.deleteAll();
    }

    private void assertSecondPostReturns200(String path, Object body, String expectedWaarde) {
        given().contentType(ContentType.JSON).body(body).post(path).then().statusCode(CREATED);
        given().contentType(ContentType.JSON).body(body).post(path).then()
                .statusCode(OK)
                .body("waarde", org.hamcrest.Matchers.equalTo(expectedWaarde));
    }

    @Test
    void getPartij_Success()  {

        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(KVK, "111111111"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("test@example.com");
            c.setPartij(p);
            c.persist();
        });

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/profielservice/v1/identificaties/KVK/111111111")
                .then()
                .statusCode(OK)
                .body("identificaties[0].identificatieType", org.hamcrest.Matchers.equalTo("KVK"))
                .body("identificaties[0].identificatieNummer", org.hamcrest.Matchers.equalTo("111111111"))
                .body("contactgegevens[0].type", org.hamcrest.Matchers.equalTo("Email"))
                .body("contactgegevens[0].waarde", org.hamcrest.Matchers.equalTo("test@example.com"));

    }

    @Test
    void getPartij_TouchesLastUsedAtOnFirstReadButNotWithinThreshold() {
        AtomicLong contactId = new AtomicLong();
        AtomicLong voorkeurId = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(KVK, "222222222"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("touch@example.com");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
            voorkeurId.set(v.id);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertNull(Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUsedAt());
            Assertions.assertNull(Voorkeur.<Voorkeur>findById(voorkeurId.get()).getLastUsedAt());
        });

        given().contentType(ContentType.JSON)
                .when().get("/api/profielservice/v1/identificaties/KVK/222222222")
                .then().statusCode(OK);

        AtomicReference<LocalDateTime> contactFirstTouch = new AtomicReference<>();
        AtomicReference<LocalDateTime> voorkeurFirstTouch = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            LocalDateTime cTs = Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUsedAt();
            LocalDateTime vTs = Voorkeur.<Voorkeur>findById(voorkeurId.get()).getLastUsedAt();
            Assertions.assertNotNull(cTs);
            Assertions.assertNotNull(vTs);
            contactFirstTouch.set(cTs);
            voorkeurFirstTouch.set(vTs);
        });

        given().contentType(ContentType.JSON)
                .when().get("/api/profielservice/v1/identificaties/KVK/222222222")
                .then().statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(contactFirstTouch.get(),
                    Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUsedAt());
            Assertions.assertEquals(voorkeurFirstTouch.get(),
                    Voorkeur.<Voorkeur>findById(voorkeurId.get()).getLastUsedAt());
        });
    }

    @Test
    void getPartij_ReadDoesNotBumpLastUpdated() {
        AtomicLong contactId = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(KVK, "333333333"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("stable@example.com");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
        });

        AtomicReference<LocalDateTime> before = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            before.set(Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUpdated());
        });

        given().contentType(ContentType.JSON)
                .when().get("/api/profielservice/v1/identificaties/KVK/333333333")
                .then().statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(before.get(),
                    Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUpdated());
        });
    }

    @Test
    void getPartij_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/profielservice/v1/identificaties/BSN/999999999")
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void addContactgegeven_Success() {
        var body = new ContactgegevenRequest();
        body.type = ContactType.Email;
        body.waarde = "test@example.com";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/contactgegevens/BSN/123456789")
                .then()
                .statusCode(CREATED)
                .header("Location", org.hamcrest.Matchers.containsString("/contactgegevens/BSN/123456789/"))
                .body("waarde", org.hamcrest.Matchers.equalTo("test@example.com"));

    }

    @Test
    void addContactgegeven_Duplicate_Returns200() {
        var body = new ContactgegevenRequest();
        body.type = ContactType.Email;
        body.waarde = "dup@example.com";

        assertSecondPostReturns200("/api/profielservice/v1/contactgegevens/BSN/123456789", body, "dup@example.com");
    }

    @Test
    void addContactgegeven_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/contactgegevens/BSN/123456789")
                .then()
                .statusCode(BAD_REQUEST);

    }

    @Test
    void updateContactgegeven_Success() {

        AtomicLong id = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111111"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("test@example.com");
            c.setPartij(p);
            c.persist();
            id.set(c.id);
        });

        var body = new ContactgegevenUpdateRequest();
        body.id = id.get();
        body.type = ContactType.Email;
        body.waarde = "test2@example.com";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegevens/BSN/111111111/" + id.get())
                .then()
                .statusCode(OK);
    }

    @Test
    void updateContactgegeven_BadRequest() {

        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111113"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("test@example.com");
            c.setPartij(p);
            c.persist();
        });

        given()
                .contentType(ContentType.JSON)
                .put("/api/profielservice/v1/contactgegevens/BSN/111111113/1")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateContactgegeven_NotFound() {

        var body = new ContactgegevenUpdateRequest();
        body.id = 1;
        body.type = ContactType.Email;
        body.waarde = "test2@example.com";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegevens/BSN/123456789/1")
                .then()
                .statusCode(NOT_FOUND);
    }


    @Test
    void deleteContactgegeven_Success() {
        AtomicLong contactGegevenId = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111114"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("test@example.com");
            c.setPartij(p);
            c.persist();
            contactGegevenId.set(c.id);
        });

        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/contactgegevens/BSN/111111114/" + contactGegevenId.get())
                .then()
                .statusCode(NO_CONTENT);
    }
    @Test
    void deleteContactgegeven_NotFound() {

        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/contactgegevens/BSN/111111114/" + 1)
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void addVoorkeur_Success() {
        var body = new VoorkeurRequest();
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "nl";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/voorkeuren/BSN/123456789")
                .then()
                .statusCode(CREATED)
                .header("Location", org.hamcrest.Matchers.containsString("/voorkeuren/BSN/123456789/"));
    }

    @Test
    void addVoorkeur_Duplicate_Returns200() {
        var body = new VoorkeurRequest();
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "nl";

        assertSecondPostReturns200("/api/profielservice/v1/voorkeuren/BSN/123456789", body, "nl");
    }

    @Test
    void addVoorkeur_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/voorkeuren/BSN/123456789")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateVoorkeur_Success() {
        AtomicLong id = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111115"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
            id.set(v.id);
        });

        var body = new VoorkeurUpdateRequest();
        body.id = id.get();
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "en";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/voorkeuren/BSN/111111115/" + id.get())
                .then()
                .statusCode(OK);
    }

    @Test
    void updateVoorkeur_BadRequest() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111116"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
        });

        given()
                .contentType(ContentType.JSON)
                .put("/api/profielservice/v1/voorkeuren/BSN/111111116/1")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateVoorkeur_NotFound() {
        var body = new VoorkeurUpdateRequest();
        body.id = 1;
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "en";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/voorkeuren/BSN/123456789/1")
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void deleteVoorkeur_Success() {
        AtomicLong voorkeurId = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111118"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
            voorkeurId.set(v.id);
        });

        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/voorkeuren/BSN/111111118/" + voorkeurId.get())
                .then()
                .statusCode(NO_CONTENT);
    }

    @Test
    void deleteVoorkeur_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/voorkeuren/BSN/111111119/" + 1)
                .then()
                .statusCode(NOT_FOUND);
    }
}
