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
import nl.rijksoverheid.moz.dto.request.PartijBulkRequest;
import nl.rijksoverheid.moz.dto.request.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.ScopeRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static nl.rijksoverheid.moz.common.IdentificatieType.KVK;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NO_CONTENT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

@QuarkusTest
public class ProfielControllerIntegrationTest extends OpenApiValidationTest {

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    @BeforeEach
    @Transactional
    void setup() {
        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());
    }

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

    private void assertSecondPostReturns200(String path, Object body, String expectedWaarde) {
        given().filter(validationFilter).contentType(ContentType.JSON).body(body).post(path).then().statusCode(CREATED);
        given().filter(validationFilter).contentType(ContentType.JSON).body(body).post(path).then()
                .statusCode(OK)
                .body("waarde", equalTo(expectedWaarde));
    }

    @Test
    void getPartij_Success() {

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

        var request = new PartijRequest();
        request.identificatieType = KVK;
        request.identificatieNummer = "111111111";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(OK)
                .body("identificaties[0].identificatieType", equalTo("KVK"))
                .body("identificaties[0].identificatieNummer", equalTo("111111111"))
                .body("contactgegevens[0].type", equalTo("Email"))
                .body("contactgegevens[0].waarde", equalTo("test@example.com"));
    }

    @Test
    void getPartij_TouchesLastUsedAtOnFirstReadButNotWithinThreshold() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
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

        var request = new PartijRequest();
        request.identificatieType = KVK;
        request.identificatieNummer = "222222222";

        given().filter(validationFilter).contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/profielservice/v1/partij")
                .then().statusCode(OK);

        AtomicReference<Instant> contactFirstTouch = new AtomicReference<>();
        AtomicReference<Instant> voorkeurFirstTouch = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Instant cTs = Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUsedAt();
            Instant vTs = Voorkeur.<Voorkeur>findById(voorkeurId.get()).getLastUsedAt();
            Assertions.assertNotNull(cTs);
            Assertions.assertNotNull(vTs);
            contactFirstTouch.set(cTs);
            voorkeurFirstTouch.set(vTs);
        });

        given().filter(validationFilter).contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/profielservice/v1/partij")
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
        AtomicReference<UUID> contactId = new AtomicReference<>();
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

        AtomicReference<Instant> before = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            before.set(Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUpdated());
        });

        var request = new PartijRequest();
        request.identificatieType = KVK;
        request.identificatieNummer = "333333333";

        given().filter(validationFilter).contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/profielservice/v1/partij")
                .then().statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(before.get(),
                    Contactgegeven.<Contactgegeven>findById(contactId.get()).getLastUpdated());
        });
    }

    @Test
    void getPartij_NotFound() {
        var request = new PartijRequest();
        request.identificatieType = BSN;
        request.identificatieNummer = "999999999";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Partij niet gevonden"))
                .body("status", equalTo(404))
                .body("detail", equalTo("Geen partij gevonden voor het opgegeven identificatienummer."))
                .body("instance", equalTo("/api/profielservice/v1/partij"));
    }

    @Test
    void getPartijBulk_Success() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p1 = new Partij();
            p1.addIdentificatie(new Identificatie(BSN, "111111120"));
            p1.persist();
            Partij p2 = new Partij();
            p2.addIdentificatie(new Identificatie(KVK, "111111121"));
            p2.persist();
        });

        var id1 = new PartijIdentificatieRequest();
        id1.identificatieType = BSN;
        id1.identificatieNummer = "111111120";
        var id2 = new PartijIdentificatieRequest();
        id2.identificatieType = KVK;
        id2.identificatieNummer = "111111121";

        var request = new PartijBulkRequest();
        request.identificaties = List.of(id1, id2);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(OK)
                .body("size()", equalTo(2));  // all found → 200
    }

    @Test
    void getPartijBulk_PartialFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111122"));
            p.persist();
        });

        var id1 = new PartijIdentificatieRequest();
        id1.identificatieType = BSN;
        id1.identificatieNummer = "111111122";
        var id2 = new PartijIdentificatieRequest();
        id2.identificatieType = BSN;
        id2.identificatieNummer = "999999999";

        var request = new PartijBulkRequest();
        request.identificaties = List.of(id1, id2);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(206)  // partial found → 206
                .body("size()", equalTo(1));
    }

    @Test
    void getPartijBulk_NoneFound() {
        var id = new PartijIdentificatieRequest();
        id.identificatieType = BSN;
        id.identificatieNummer = "000000000";

        var request = new PartijBulkRequest();
        request.identificaties = List.of(id);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(NOT_FOUND)  // none found → 404
                .contentType("application/problem+json")
                .body("title", equalTo("Partijen niet gevonden"));
    }

    @Test
    void getPartij_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void getPartijBulk_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void addContactgegeven_Success() {
        var body = new ContactgegevenRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.type = ContactType.Email;
        body.waarde = "test@example.com";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(CREATED)
                .header("Location", containsString("/contactgegeven/"))
                .body("waarde", equalTo("test@example.com"));
    }

    @Test
    void addContactgegeven_Duplicate_Returns200() {
        var body = new ContactgegevenRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.type = ContactType.Email;
        body.waarde = "dup@example.com";

        assertSecondPostReturns200("/api/profielservice/v1/contactgegeven", body, "dup@example.com");
    }

    @Test
    void addContactgegeven_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void addContactgegeven_UnknownDienstverlenerInScope_Returns404() {
        var body = new ContactgegevenRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.type = ContactType.Email;
        body.waarde = "test@example.com";
        body.scope = new ScopeRequest();
        body.scope.dienstverlenerNaam = "BestaatNiet";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Not Found"));
    }

    @Test
    void addContactgegeven_DienstNaamWithoutDienstverlenerNaam_Returns400() {
        var body = new ContactgegevenRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.type = ContactType.Email;
        body.waarde = "test@example.com";
        body.scope = new ScopeRequest();
        body.scope.dienstNaam = "SomeDienst";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("title", equalTo("Bad Request"));
    }

    @Test
    void updateContactgegeven_Success() {
        AtomicReference<UUID> id = new AtomicReference<>();
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
        body.identificatieType = BSN;
        body.identificatieNummer = "111111111";
        body.id = id.get();
        body.type = ContactType.Email;
        body.waarde = "test2@example.com";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven updated = Contactgegeven.findById(id.get());
            Assertions.assertEquals("test2@example.com", updated.getWaarde());
            Assertions.assertFalse(updated.isIsGeverifieerd(),
                    "Geverifieerd-status moet resetten zodra de email-waarde verandert");
            Assertions.assertNull(updated.getGeverifieerdAt(),
                    "GeverifieerdAt moet leeg zijn na waarde-wijziging");
        });
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
                .put("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateContactgegeven_NotFound() {
        var body = new ContactgegevenUpdateRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.id = UUID.randomUUID();
        body.type = ContactType.Email;
        body.waarde = "test2@example.com";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Contactgegeven niet gevonden"));
    }

    @Test
    void deleteContactgegeven_Success() {
        AtomicReference<UUID> contactGegevenId = new AtomicReference<>();
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

        var body = new PartijIdentificatieRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "111111114";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .delete("/api/profielservice/v1/contactgegeven/" + contactGegevenId.get())
                .then()
                .statusCode(NO_CONTENT);
    }

    @Test
    void deleteContactgegeven_NotFound() {
        var body = new PartijIdentificatieRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "111111114";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .delete("/api/profielservice/v1/contactgegeven/" + UUID.randomUUID())
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Contactgegeven niet gevonden"));
    }

    @Test
    void addVoorkeur_Success() {
        var body = new VoorkeurRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "nl";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(CREATED)
                .header("Location", containsString("/voorkeur/"));
    }

    @Test
    void addVoorkeur_Duplicate_Returns200() {
        var body = new VoorkeurRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "nl";

        assertSecondPostReturns200("/api/profielservice/v1/voorkeur", body, "nl");
    }

    @Test
    void addVoorkeur_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateVoorkeur_Success() {
        AtomicReference<UUID> id = new AtomicReference<>();
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
        body.identificatieType = BSN;
        body.identificatieNummer = "111111115";
        body.id = id.get();
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "en";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/voorkeur")
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
                .put("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void updateVoorkeur_NotFound() {
        var body = new VoorkeurUpdateRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "123456789";
        body.id = UUID.randomUUID();
        body.voorkeurType = VoorkeurType.WebsiteTaal;
        body.waarde = "en";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Voorkeur niet gevonden"));
    }

    @Test
    void deleteContactgegeven_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/contactgegeven/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void deleteVoorkeur_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/voorkeur/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST);
    }

    @Test
    void deleteVoorkeur_Success() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
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

        var body = new PartijIdentificatieRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "111111118";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .delete("/api/profielservice/v1/voorkeur/" + voorkeurId.get())
                .then()
                .statusCode(NO_CONTENT);
    }

    @Test
    void deleteVoorkeur_NotFound() {
        var body = new PartijIdentificatieRequest();
        body.identificatieType = BSN;
        body.identificatieNummer = "111111119";

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .delete("/api/profielservice/v1/voorkeur/" + UUID.randomUUID())
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Voorkeur niet gevonden"));
    }
}
