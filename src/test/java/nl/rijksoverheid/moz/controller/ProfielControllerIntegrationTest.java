package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijBulkRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.ScopeRequest;
import nl.rijksoverheid.moz.api.generated.model.TeVerwijderenOpRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static nl.rijksoverheid.moz.common.IdentificatieType.KVK;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.FORBIDDEN;
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
        request.setIdentificatieType(KVK);
        request.setIdentificatieNummer("111111111");

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
        request.setIdentificatieType(KVK);
        request.setIdentificatieNummer("222222222");

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
        request.setIdentificatieType(KVK);
        request.setIdentificatieNummer("333333333");

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
        request.setIdentificatieType(BSN);
        request.setIdentificatieNummer("999999999");

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
        id1.setIdentificatieType(BSN);
        id1.setIdentificatieNummer("111111120");
        var id2 = new PartijIdentificatieRequest();
        id2.setIdentificatieType(KVK);
        id2.setIdentificatieNummer("111111121");

        var request = new PartijBulkRequest();
        request.setIdentificaties(List.of(id1, id2));

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
        id1.setIdentificatieType(BSN);
        id1.setIdentificatieNummer("111111122");
        var id2 = new PartijIdentificatieRequest();
        id2.setIdentificatieType(BSN);
        id2.setIdentificatieNummer("999999999");

        var request = new PartijBulkRequest();
        request.setIdentificaties(List.of(id1, id2));

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
        id.setIdentificatieType(BSN);
        id.setIdentificatieNummer("000000000");

        var request = new PartijBulkRequest();
        request.setIdentificaties(List.of(id));

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setType(ContactType.Email);
        body.setWaarde("test@example.com");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setType(ContactType.Email);
        body.setWaarde("dup@example.com");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setType(ContactType.Email);
        body.setWaarde("test@example.com");
        body.setScope(new ScopeRequest());
        body.getScope().setDienstverlenerNaam("BestaatNiet");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setType(ContactType.Email);
        body.setWaarde("test@example.com");
        body.setScope(new ScopeRequest());
        body.getScope().setDienstNaam("SomeDienst");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111111");
        body.setId(id.get());
        body.setType(ContactType.Email);
        body.setWaarde("test2@example.com");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setId(UUID.randomUUID());
        body.setType(ContactType.Email);
        body.setWaarde("test2@example.com");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111114");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111114");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setVoorkeurType(VoorkeurType.WebsiteTaal);
        body.setWaarde("nl");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setVoorkeurType(VoorkeurType.WebsiteTaal);
        body.setWaarde("nl");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111115");
        body.setId(id.get());
        body.setVoorkeurType(VoorkeurType.WebsiteTaal);
        body.setWaarde("en");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setId(UUID.randomUUID());
        body.setVoorkeurType(VoorkeurType.WebsiteTaal);
        body.setWaarde("en");

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

    /**
     * De twee tests hierboven sturen geen body en worden door {@code RequireBodyReaderInterceptor}
     * beantwoord, vóór de bean-validatie. Deze twee sturen een body die het contract schendt en
     * raken daarmee de {@code @Valid} op de DELETE-parameter; zonder die annotatie blijft het
     * verzoek staan tot de service er een 404 van maakt.
     */
    @Test
    void deleteContactgegeven_BlancoIdentificatieNummer_GeeftViolations() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"identificatieType\":\"KVK\",\"identificatieNummer\":\" \"}")
                .delete("/api/profielservice/v1/contactgegeven/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("identificatieNummer"));
    }

    @Test
    void deleteVoorkeur_BlancoIdentificatieNummer_GeeftViolations() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"identificatieType\":\"KVK\",\"identificatieNummer\":\" \"}")
                .delete("/api/profielservice/v1/voorkeur/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("identificatieNummer"));
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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111118");

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
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111119");

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

    @Test
    void updateContactgegeven_ZonderId_BadRequest() {
        var body = new ContactgegevenUpdateRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111111");
        body.setType(ContactType.Email);
        body.setWaarde("test@example.com");

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("id"));
    }

    @Test
    void updateVoorkeur_ZonderId_BadRequest() {
        var body = new VoorkeurUpdateRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111111");
        body.setVoorkeurType(VoorkeurType.WebsiteTaal);
        body.setWaarde("nl");

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("id"));
    }

    @Test
    void updateContactgegevenTeVerwijderenOp_NotFound() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(teVerwijderenOpRequest(UUID.randomUUID(), "999999999"))
                .patch("/api/profielservice/v1/contactgegeven/te-verwijderen-op")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Contactgegeven niet gevonden"));
    }

    @Test
    void updateVoorkeurTeVerwijderenOp_NotFound() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(teVerwijderenOpRequest(UUID.randomUUID(), "999999999"))
                .patch("/api/profielservice/v1/voorkeur/te-verwijderen-op")
                .then()
                .statusCode(NOT_FOUND)
                .contentType("application/problem+json")
                .body("title", equalTo("Voorkeur niet gevonden"));
    }

    @Test
    void updateContactgegevenTeVerwijderenOp_Success() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("ScopeDV");
            dv.persist();

            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111120"));
            p.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);

            DienstverlenerDienst link = new DienstverlenerDienst(dv, null);
            link.persist();
            new ScopeContactgegeven(c, link).persist();
        });

        var body = teVerwijderenOpRequest(contactId.get(), "111111120");
        body.setDienstverlenerNaam("ScopeDV");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .patch("/api/profielservice/v1/contactgegeven/te-verwijderen-op")
                .then()
                .statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven c = Contactgegeven.findById(contactId.get());
            Assertions.assertEquals(body.getTeVerwijderenOp(), c.getTeVerwijderenOp());
        });
    }

    @Test
    void updateVoorkeurTeVerwijderenOp_Success() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("ScopeDV");
            dv.persist();

            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111121"));
            p.persist();

            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
            voorkeurId.set(v.id);

            DienstverlenerDienst link = new DienstverlenerDienst(dv, null);
            link.persist();
            new ScopeVoorkeur(v, link).persist();
        });

        var body = teVerwijderenOpRequest(voorkeurId.get(), "111111121");
        body.setDienstverlenerNaam("ScopeDV");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .patch("/api/profielservice/v1/voorkeur/te-verwijderen-op")
                .then()
                .statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur v = Voorkeur.findById(voorkeurId.get());
            Assertions.assertEquals(body.getTeVerwijderenOp(), v.getTeVerwijderenOp());
        });
    }

    /**
     * De autorisatiegrens waarvoor dit endpoint bestaat: een dienstverlener mag alleen een
     * verwijderdatum zetten op een contactgegeven waar hij zelf scope op heeft.
     *
     * <p>"ScopeDV" bestaat hier en heeft scope op een ánder contactgegeven van dezelfde partij.
     * Dat maakt geen onderscheid in de response — {@code requireDienstverlenerAuthorized} loopt
     * alleen de scopes van het doel-contactgegeven af en zoekt de dienstverlener nooit op naam
     * op, dus een onbekende naam levert exact hetzelfde antwoord. De fixture vangt wél een
     * regressie waarbij de scope-controle van contactgegeven naar partij zou verbreden: dan
     * telt de scope op dat andere contactgegeven ineens mee en verdwijnt de 403.
     *
     * <p>{@code DomainExceptionMapperTest} toetst de mapper los en {@code PartijServiceTest}
     * alleen de exception, en die laatste dekt bovendien alleen de voorkeur-variant. Wat hier
     * uniek wordt vastgelegd is dat Quarkus de {@code @ServerExceptionMapper} daadwerkelijk
     * oppikt en het geheel over HTTP een 403 met problem-body oplevert.
     */
    @Test
    void updateContactgegevenTeVerwijderenOp_ZonderScope_Forbidden() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener eigenaar = new Dienstverlener();
            eigenaar.setNaam("AndereDV");
            eigenaar.persist();

            Dienstverlener buitenstaander = new Dienstverlener();
            buitenstaander.setNaam("ScopeDV");
            buitenstaander.persist();

            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111122"));
            p.persist();

            Contactgegeven doelwit = new Contactgegeven();
            doelwit.setType(ContactType.Telefoonnummer);
            doelwit.setWaarde("0612345678");
            doelwit.setPartij(p);
            doelwit.persist();
            contactId.set(doelwit.id);

            DienstverlenerDienst eigenaarLink = new DienstverlenerDienst(eigenaar, null);
            eigenaarLink.persist();
            new ScopeContactgegeven(doelwit, eigenaarLink).persist();

            Contactgegeven ander = new Contactgegeven();
            ander.setType(ContactType.Email);
            ander.setWaarde("ander@example.com");
            ander.setPartij(p);
            ander.persist();

            DienstverlenerDienst buitenstaanderLink = new DienstverlenerDienst(buitenstaander, null);
            buitenstaanderLink.persist();
            new ScopeContactgegeven(ander, buitenstaanderLink).persist();
        });

        var body = teVerwijderenOpRequest(contactId.get(), "111111122");
        body.setDienstverlenerNaam("ScopeDV");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .patch("/api/profielservice/v1/contactgegeven/te-verwijderen-op")
                .then()
                .statusCode(FORBIDDEN)
                .contentType("application/problem+json")
                .body("title", equalTo("Forbidden"))
                .body("detail", containsString("niet bevoegd"));
    }

    /**
     * De dienst-specifieke autorisatie: een scope op precies deze dienst geeft wél toegang.
     *
     * <p>De overige te-verwijderen-op tests laten {@code dienstNaam} weg, waardoor
     * {@code requireDienstverlenerAuthorized} al teruggeeft op de eerste controle en de
     * dienstnaam-vergelijking nooit bereikt wordt. Het níet-matchende geval is elders wel gedekt,
     * door {@code PartijServiceTest.updateVoorkeurTeVerwijderenOpByDienstverlener_WithWrongDienstNaam_ThrowsForbidden};
     * wat deze test toevoegt is het matchende geval, end-to-end over HTTP en voor de
     * contactgegeven-variant.
     */
    @Test
    void updateContactgegevenTeVerwijderenOp_MetDienstSpecifiekeScope_Success() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("ScopeDV");
            dv.persist();
            Dienst dienst = new Dienst();
            dienst.setNaam("Parkeervergunning");
            dienst.persist();
            DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
            link.persist();

            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111123"));
            p.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);

            new ScopeContactgegeven(c, link).persist();
        });

        var body = teVerwijderenOpRequest(contactId.get(), "111111123");
        body.setDienstverlenerNaam("ScopeDV");
        body.setDienstNaam("Parkeervergunning");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .patch("/api/profielservice/v1/contactgegeven/te-verwijderen-op")
                .then()
                .statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven c = Contactgegeven.findById(contactId.get());
            Assertions.assertEquals(body.getTeVerwijderenOp(), c.getTeVerwijderenOp());
        });
    }

    /**
     * De andere kant van dezelfde vergelijking: een DV-brede scope ({@code dienst == null}) geeft
     * toegang ongeacht welke dienstNaam het request noemt. Zonder deze test kan die kortsluiting
     * wegvallen zonder dat er iets faalt, en krijgt elke houder van een brede scope die een
     * dienstNaam meestuurt voortaan een 403.
     */
    @Test
    void updateContactgegevenTeVerwijderenOp_BredeScopeMetDienstNaam_Success() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("BredeDV");
            dv.persist();

            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111124"));
            p.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);

            DienstverlenerDienst link = new DienstverlenerDienst(dv, null);
            link.persist();
            new ScopeContactgegeven(c, link).persist();
        });

        var body = teVerwijderenOpRequest(contactId.get(), "111111124");
        body.setDienstverlenerNaam("BredeDV");
        body.setDienstNaam("WelkeDienstDanOok");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .patch("/api/profielservice/v1/contactgegeven/te-verwijderen-op")
                .then()
                .statusCode(OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven c = Contactgegeven.findById(contactId.get());
            Assertions.assertEquals(body.getTeVerwijderenOp(), c.getTeVerwijderenOp());
        });
    }

    private static TeVerwijderenOpRequest teVerwijderenOpRequest(UUID id, String identificatieNummer) {
        var request = new TeVerwijderenOpRequest();
        request.setId(id);
        request.setIdentificatieType(BSN);
        request.setIdentificatieNummer(identificatieNummer);
        request.setDienstverlenerNaam("OnbekendeDV");
        request.setTeVerwijderenOp(Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS));
        return request;
    }
}
