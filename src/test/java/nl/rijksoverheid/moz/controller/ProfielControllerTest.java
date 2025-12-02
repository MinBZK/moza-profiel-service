package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static nl.rijksoverheid.moz.common.IdentificatieType.KVK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.*;

@QuarkusTest
public class ProfielControllerTest {

    @BeforeEach
    @Transactional
    void setup() {
        Contactgegeven.deleteAll();
        Afdeling.deleteAll();
        Dienstverlener.deleteAll();
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
                .get("/api/profielservice/v1/KVK/111111111")
                .then()
                .statusCode(OK)
                .body("identificaties[0].identificatieType", org.hamcrest.Matchers.equalTo("KVK"))
                .body("identificaties[0].identificatieNummer", org.hamcrest.Matchers.equalTo("111111111"))
                .body("contactgegevens[0].type", org.hamcrest.Matchers.equalTo("Email"))
                .body("contactgegevens[0].waarde", org.hamcrest.Matchers.equalTo("test@example.com"));

    }

    @Test
    void getPartij_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/profielservice/v1/BSN/999999999")
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void addPartij_Success() {
        var body = new ContactgegevenRequest();
        body.afdelingId = 0;
        body.type = ContactType.Email;
        body.waarde = "test@example.com";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/contactgegeven/BSN/123456789")
                .then()
                .statusCode(CREATED);

    }

    @Test
    void addPartij_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/contactgegeven/BSN/123456789")
                .then()
                .statusCode(BAD_REQUEST);

    }

    @Test
    void updateContactgegeven_Success() {

        AtomicLong id = new AtomicLong();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111112"));
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
        body.afdelingId = 0;
        body.type = ContactType.Email;
        body.waarde = "test2@example.com";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegeven/BSN/111111112")
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
                .put("/api/profielservice/v1/contactgegeven/BSN/111111113")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateContactgegeven_NotFound() {

        var body = new ContactgegevenUpdateRequest();
        body.id = 1;
        body.afdelingId = 0;
        body.type = ContactType.Email;
        body.waarde = "test2@example.com";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegeven/BSN/123456789")
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
                .delete("/api/profielservice/v1/contactgegeven/BSN/111111114/" + contactGegevenId.get())
                .then()
                .statusCode(NO_CONTENT);
    }
    @Test
    void deleteContactgegeven_NotFound() {

        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/contactgegeven/BSN/111111114/" + 1)
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
                .post("/api/profielservice/v1/voorkeur/BSN/123456789")
                .then()
                .statusCode(CREATED);
    }

    @Test
    void addVoorkeur_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/voorkeur/BSN/123456789")
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
                .put("/api/profielservice/v1/voorkeur/BSN/111111115")
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
                .put("/api/profielservice/v1/voorkeur/BSN/111111116")
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
                .put("/api/profielservice/v1/voorkeur/BSN/123456789")
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
                .delete("/api/profielservice/v1/voorkeur/BSN/111111118/" + voorkeurId.get())
                .then()
                .statusCode(NO_CONTENT);
    }

    @Test
    void deleteVoorkeur_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/voorkeur/BSN/111111119/" + 1)
                .then()
                .statusCode(NOT_FOUND);
    }
}
