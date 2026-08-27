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
import nl.rijksoverheid.moz.DatabaseCleanup;
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
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CONFLICT;
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
    void tearDown() {
        DatabaseCleanup.wipe();
    }

    private void assertSecondPostReturnsConflict(String path, Object body) {
        given().filter(validationFilter).contentType(ContentType.JSON).body(body).post(path).then().statusCode(CREATED);
        given().filter(validationFilter).contentType(ContentType.JSON).body(body).post(path).then()
                .statusCode(CONFLICT)
                .contentType("application/problem+json")
                .body("title", equalTo("Conflict"));
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
    void addContactgegeven_Duplicate_Returns409() {
        var body = new ContactgegevenRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setType(ContactType.Email);
        body.setWaarde("dup@example.com");

        assertSecondPostReturnsConflict("/api/profielservice/v1/contactgegeven", body);
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
    void verwijderContactgegeven_NotFound() {
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
    void addVoorkeur_Duplicate_Returns409() {
        var body = new VoorkeurRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("123456789");
        body.setVoorkeurType(VoorkeurType.WebsiteTaal);
        body.setWaarde("nl");

        assertSecondPostReturnsConflict("/api/profielservice/v1/voorkeur", body);
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
    void verwijderContactgegeven_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/contactgegeven/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                // De melding onderscheidt RequireBodyReaderInterceptor van de @NotNull op
                // de gegenereerde interface. Alleen de eerste houdt het verzoek tegen vóór
                // @Logboek, zodat er geen LDV-span ontstaat voor een verzoek zonder gegevens.
                .body("detail", equalTo("Request body mag niet leeg zijn"));
    }

    @Test
    void verwijderVoorkeur_BadRequest() {
        given()
                .contentType(ContentType.JSON)
                .delete("/api/profielservice/v1/voorkeur/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                // De melding onderscheidt RequireBodyReaderInterceptor van de @NotNull op
                // de gegenereerde interface. Alleen de eerste houdt het verzoek tegen vóór
                // @Logboek, zodat er geen LDV-span ontstaat voor een verzoek zonder gegevens.
                .body("detail", equalTo("Request body mag niet leeg zijn"));
    }

    /**
     * De twee tests hierboven sturen geen body en worden door {@code RequireBodyReaderInterceptor}
     * beantwoord, vóór de bean-validatie. Deze twee sturen een body die het contract schendt en
     * raken daarmee de {@code @Valid} op de DELETE-parameter; zonder die annotatie blijft het
     * verzoek staan tot de service er een 404 van maakt.
     */
    @Test
    void verwijderContactgegeven_BlancoIdentificatieNummer_GeeftViolations() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"identificatieType\":\"KVK\",\"identificatieNummer\":\" \"}")
                .delete("/api/profielservice/v1/contactgegeven/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("identificatieNummer"));
    }

    @Test
    void verwijderVoorkeur_BlancoIdentificatieNummer_GeeftViolations() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"identificatieType\":\"KVK\",\"identificatieNummer\":\" \"}")
                .delete("/api/profielservice/v1/voorkeur/" + UUID.randomUUID())
                .then()
                .statusCode(BAD_REQUEST)
                .body("violations.field", hasItem("identificatieNummer"));
    }

    @Test
    void verwijderVoorkeur_NotFound() {
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
    void verwijderVoorkeur_Success() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111122"));
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
        body.setIdentificatieNummer("111111122");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .delete("/api/profielservice/v1/voorkeur/" + voorkeurId.get())
                .then()
                .statusCode(NO_CONTENT);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur v = Voorkeur.findById(voorkeurId.get());
            Assertions.assertNotNull(v, "the voorkeur must still exist (soft delete, not hard delete)");
            Assertions.assertNotNull(v.getVerwijderdOp(), "verwijderdOp must be set by the delete endpoint");
        });
    }

    @Test
    void verwijderContactgegeven_Success() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111123"));
            p.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
        });

        var body = new PartijIdentificatieRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111123");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .delete("/api/profielservice/v1/contactgegeven/" + contactId.get())
                .then()
                .statusCode(NO_CONTENT);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven c = Contactgegeven.findById(contactId.get());
            Assertions.assertNotNull(c, "the contactgegeven must still exist (soft delete, not hard delete)");
            Assertions.assertNotNull(c.getVerwijderdOp(), "verwijderdOp must be set by the delete endpoint");
        });
    }

    @Test
    void verwijderVoorkeur_Herhaald_TweedeGeeft404() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111128"));
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
        body.setIdentificatieNummer("111111128");

        given().filter(validationFilter).contentType(ContentType.JSON).body(body)
                .delete("/api/profielservice/v1/voorkeur/" + voorkeurId.get())
                .then().statusCode(NO_CONTENT);

        given().filter(validationFilter).contentType(ContentType.JSON).body(body)
                .delete("/api/profielservice/v1/voorkeur/" + voorkeurId.get())
                .then().statusCode(NOT_FOUND);
    }

    @Test
    void verwijderContactgegeven_Herhaald_TweedeGeeft404() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111129"));
            p.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
        });

        var body = new PartijIdentificatieRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111129");

        given().filter(validationFilter).contentType(ContentType.JSON).body(body)
                .delete("/api/profielservice/v1/contactgegeven/" + contactId.get())
                .then().statusCode(NO_CONTENT);

        given().filter(validationFilter).contentType(ContentType.JSON).body(body)
                .delete("/api/profielservice/v1/contactgegeven/" + contactId.get())
                .then().statusCode(NOT_FOUND);
    }

    @Test
    void verwijderVoorkeur_LaatsteActieveKind_PartijNietMeerOpvraagbaar() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111124"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
            voorkeurId.set(v.id);
        });

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111124");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/voorkeur/" + voorkeurId.get())
                .then().statusCode(NO_CONTENT);

        var request = new PartijRequest();
        request.setIdentificatieType(BSN);
        request.setIdentificatieNummer("111111124");

        given().filter(validationFilter).contentType(ContentType.JSON).body(request)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void verwijderVoorkeur_AndereContactgegevenActief_PartijBlijftOpvraagbaar() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111128"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
            voorkeurId.set(v.id);
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
        });

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111128");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/voorkeur/" + voorkeurId.get())
                .then().statusCode(NO_CONTENT);

        var request = new PartijRequest();
        request.setIdentificatieType(BSN);
        request.setIdentificatieNummer("111111128");

        given().filter(validationFilter).contentType(ContentType.JSON).body(request)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(OK)
                .body("voorkeuren", org.hamcrest.Matchers.empty())
                .body("contactgegevens", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty()));
    }

    @Test
    void verwijderContactgegeven_LaatsteActieveKind_PartijNietMeerOpvraagbaar() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111125"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
        });

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111125");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/contactgegeven/" + contactId.get())
                .then().statusCode(NO_CONTENT);

        var request = new PartijRequest();
        request.setIdentificatieType(BSN);
        request.setIdentificatieNummer("111111125");

        given().filter(validationFilter).contentType(ContentType.JSON).body(request)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(NOT_FOUND);
    }

    @Test
    void verwijderContactgegeven_AndereVoorkeurActief_PartijBlijftOpvraagbaar() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111129"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
        });

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111129");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/contactgegeven/" + contactId.get())
                .then().statusCode(NO_CONTENT);

        var request = new PartijRequest();
        request.setIdentificatieType(BSN);
        request.setIdentificatieNummer("111111129");

        given().filter(validationFilter).contentType(ContentType.JSON).body(request)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(OK)
                .body("contactgegevens", org.hamcrest.Matchers.empty())
                .body("voorkeuren", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty()));
    }

    @Test
    void verwijderContactgegeven_LaatsteActieveKind_DanOpnieuwToevoegen_MaaktNieuwePartij() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111130"));
            p.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Telefoonnummer);
            c.setWaarde("0612345678");
            c.setPartij(p);
            c.persist();
            contactId.set(c.id);
        });

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111130");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/contactgegeven/" + contactId.get())
                .then().statusCode(NO_CONTENT);

        var getRequest = new PartijRequest();
        getRequest.setIdentificatieType(BSN);
        getRequest.setIdentificatieNummer("111111130");

        given().filter(validationFilter).contentType(ContentType.JSON).body(getRequest)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(NOT_FOUND);

        var addRequest = new ContactgegevenRequest();
        addRequest.setIdentificatieType(BSN);
        addRequest.setIdentificatieNummer("111111130");
        addRequest.setType(ContactType.Telefoonnummer);
        addRequest.setWaarde("0687654321");

        given().filter(validationFilter).contentType(ContentType.JSON).body(addRequest)
                .post("/api/profielservice/v1/contactgegeven")
                .then().statusCode(CREATED);

        given().filter(validationFilter).contentType(ContentType.JSON).body(getRequest)
                .post("/api/profielservice/v1/partij")
                .then()
                .statusCode(OK)
                .body("contactgegevens", org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    void verwijderVoorkeur_DanOpnieuwToevoegen_GeeftNieuweRijMet201() {
        var addBody = new VoorkeurRequest();
        addBody.setIdentificatieType(BSN);
        addBody.setIdentificatieNummer("111111126");
        addBody.setVoorkeurType(VoorkeurType.WebsiteTaal);
        addBody.setWaarde("nl");

        UUID origineleId = UUID.fromString(
                given().filter(validationFilter).contentType(ContentType.JSON).body(addBody)
                        .post("/api/profielservice/v1/voorkeur")
                        .then().statusCode(CREATED)
                        .extract().path("id"));

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111126");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/voorkeur/" + origineleId)
                .then().statusCode(NO_CONTENT);

        // Opnieuw dezelfde waarde toevoegen: de rij met de soft delete mag niet hersteld worden
        // (dat zou 200 + de oude id geven), er moet een nieuwe rij ontstaan (201 + nieuwe id).
        given().filter(validationFilter).contentType(ContentType.JSON).body(addBody)
                .post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(CREATED)
                .body("id", org.hamcrest.Matchers.not(equalTo(origineleId.toString())));
    }

    @Test
    void verwijderContactgegeven_DanOpnieuwToevoegen_GeeftNieuweRijMet201() {
        var addBody = new ContactgegevenRequest();
        addBody.setIdentificatieType(BSN);
        addBody.setIdentificatieNummer("111111127");
        addBody.setType(ContactType.Telefoonnummer);
        addBody.setWaarde("0612345678");

        UUID origineleId = UUID.fromString(
                given().filter(validationFilter).contentType(ContentType.JSON).body(addBody)
                        .post("/api/profielservice/v1/contactgegeven")
                        .then().statusCode(CREATED)
                        .extract().path("id"));

        var deleteBody = new PartijIdentificatieRequest();
        deleteBody.setIdentificatieType(BSN);
        deleteBody.setIdentificatieNummer("111111127");

        given().filter(validationFilter).contentType(ContentType.JSON).body(deleteBody)
                .delete("/api/profielservice/v1/contactgegeven/" + origineleId)
                .then().statusCode(NO_CONTENT);

        given().filter(validationFilter).contentType(ContentType.JSON).body(addBody)
                .post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(CREATED)
                .body("id", org.hamcrest.Matchers.not(equalTo(origineleId.toString())));
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
}
