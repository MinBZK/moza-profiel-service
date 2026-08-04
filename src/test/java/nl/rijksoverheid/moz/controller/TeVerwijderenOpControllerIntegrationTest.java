package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.dto.request.TeVerwijderenOpRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.FORBIDDEN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

/**
 * De twee PATCH .../te-verwijderen-op endpoints waren op servicelaag wel getest, maar de
 * controllerlaag eromheen niet: geen enkele test deed er een HTTP-aanroep op. Daarmee lagen
 * de statuscode-afhandeling, het LDV-datasubject en de OpenAPI-conformiteit van deze twee
 * endpoints volledig open.
 */
@QuarkusTest
class TeVerwijderenOpControllerIntegrationTest extends OpenApiValidationTest {

    private static final String VOORKEUR_PAD = "/api/profielservice/v1/voorkeur/te-verwijderen-op";
    private static final String CONTACT_PAD = "/api/profielservice/v1/contactgegeven/te-verwijderen-op";
    private static final String BSN_NUMMER = "123456789";

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

    /** Maakt een partij met één voorkeur en één contactgegeven, beide gescoped op TestDV/TestDienst. */
    private Scenario scenarioMetScope() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();
            Dienst dienst = new Dienst();
            dienst.setNaam("TestDienst");
            dienst.persist();
            DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
            link.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(BSN, BSN_NUMMER));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(partij);
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);

            Contactgegeven contact = new Contactgegeven();
            contact.setPartij(partij);
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.addScope(new ScopeContactgegeven(contact, link));
            contact.persist();
            contactId.set(contact.id);
        });
        return new Scenario(voorkeurId.get(), contactId.get());
    }

    private record Scenario(UUID voorkeurId, UUID contactId) {}

    private TeVerwijderenOpRequest request(UUID id, Instant teVerwijderenOp) {
        TeVerwijderenOpRequest request = new TeVerwijderenOpRequest();
        request.id = id;
        request.identificatieType = BSN;
        request.identificatieNummer = BSN_NUMMER;
        request.dienstverlenerNaam = "TestDV";
        request.teVerwijderenOp = teVerwijderenOp;
        return request;
    }

    private void patch(String pad, Object body, int verwachteStatus) {
        given().filter(validationFilter).contentType(ContentType.JSON)
                .body(body)
                .when().patch(pad)
                .then().statusCode(verwachteStatus);
    }

    @Test
    void voorkeur_ZetTeVerwijderenOpEnGeeft200() {
        Scenario scenario = scenarioMetScope();
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        patch(VOORKEUR_PAD, request(scenario.voorkeurId(), teVerwijderenOp), OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(scenario.voorkeurId());
            Assertions.assertEquals(teVerwijderenOp, voorkeur.getTeVerwijderenOp());
        });
    }

    @Test
    void contactgegeven_ZetTeVerwijderenOpEnGeeft200() {
        Scenario scenario = scenarioMetScope();
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        patch(CONTACT_PAD, request(scenario.contactId(), teVerwijderenOp), OK);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(scenario.contactId());
            Assertions.assertEquals(teVerwijderenOp, contact.getTeVerwijderenOp());
        });
    }

    @Test
    void onbekendePartij_Geeft404() {
        scenarioMetScope();
        TeVerwijderenOpRequest request = request(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(30)));
        request.identificatieNummer = "987654321";

        patch(VOORKEUR_PAD, request, NOT_FOUND);
        patch(CONTACT_PAD, request, NOT_FOUND);
    }

    @Test
    void onbekendeId_Geeft404() {
        scenarioMetScope();

        patch(VOORKEUR_PAD, request(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(30))), NOT_FOUND);
        patch(CONTACT_PAD, request(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(30))), NOT_FOUND);
    }

    @Test
    void dienstverlenerZonderScope_Geeft403() {
        // Een dienstverlener mag geen bewaartermijn zetten op gegevens waar hij geen scope op heeft.
        Scenario scenario = scenarioMetScope();

        TeVerwijderenOpRequest voorkeurRequest = request(scenario.voorkeurId(), Instant.now().plus(Duration.ofDays(30)));
        voorkeurRequest.dienstverlenerNaam = "AndereDV";
        patch(VOORKEUR_PAD, voorkeurRequest, FORBIDDEN);

        TeVerwijderenOpRequest contactRequest = request(scenario.contactId(), Instant.now().plus(Duration.ofDays(30)));
        contactRequest.dienstverlenerNaam = "AndereDV";
        patch(CONTACT_PAD, contactRequest, FORBIDDEN);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertNull(Voorkeur.<Voorkeur>findById(scenario.voorkeurId()).getTeVerwijderenOp());
            Assertions.assertNull(Contactgegeven.<Contactgegeven>findById(scenario.contactId()).getTeVerwijderenOp());
        });
    }

    @Test
    void datumInHetVerleden_Geeft400() {
        Scenario scenario = scenarioMetScope();

        patch(VOORKEUR_PAD, request(scenario.voorkeurId(), Instant.now().minus(Duration.ofDays(1))), BAD_REQUEST);
        patch(CONTACT_PAD, request(scenario.contactId(), Instant.now().minus(Duration.ofDays(1))), BAD_REQUEST);
    }

    @Test
    void datumVerderDanZevenJaar_Geeft400() {
        // Bewaartermijn is begrensd op 7 jaar na de referentiedatum; anders zou een
        // dienstverlener gegevens onbeperkt kunnen laten staan.
        Scenario scenario = scenarioMetScope();
        Instant teVer = Instant.now().plus(Duration.ofDays(365L * 8));

        patch(VOORKEUR_PAD, request(scenario.voorkeurId(), teVer), BAD_REQUEST);
        patch(CONTACT_PAD, request(scenario.contactId(), teVer), BAD_REQUEST);
    }

    @Test
    void legeBody_Geeft400() {
        given().filter(validationFilter).contentType(ContentType.JSON)
                .body("null")
                .when().patch(VOORKEUR_PAD)
                .then().statusCode(BAD_REQUEST);
    }
}
