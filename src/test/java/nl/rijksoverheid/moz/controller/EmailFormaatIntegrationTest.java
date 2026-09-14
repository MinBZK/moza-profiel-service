package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

/**
 * Bewaakt dat de e-mailregel uit MinBZK/MijnOverheidZakelijk#766 daadwerkelijk in de HTTP-pipeline
 * zit; zie {@link nl.rijksoverheid.moz.validation.HeeftContactWaarde} voor waarom hij class-level
 * is. {@code EmailWaardeValidatorTest} roept de validator rechtstreeks aan en blijft groen als
 * {@code x-class-extra-annotation} uit het contract verdwijnt — zonder {@code x-implements}
 * compileert die test niet eens.
 *
 * <p>Op de e-mailverificatie-endpoints is {@code email} niet polymorf; daar staat een gewone
 * {@code @Email} op het veld. Ook die kant loopt hier mee, want ook die annotatie komt uit een
 * vendor-extensie en verdwijnt geruisloos.
 *
 * <p>{@code validationFilter} hangt alleen aan de contractgeldige requests: hij keurt het verzoek
 * pas ná verzending, samen met het antwoord, en laat de test dan alsnog vallen. De negatieve
 * gevallen controleren de responsevorm daarom zelf.
 */
@QuarkusTest
class EmailFormaatIntegrationTest extends OpenApiValidationTest {

    private static final String CONTACTGEGEVEN = "/api/profielservice/v1/contactgegeven";

    /** Grof ongeldig, met opzet: randgevallen zouden Hibernate's invulling van @Email vastpinnen. */
    private static final String ONGELDIG = "geen adres";

    /** Doorstaat de elfproef, zodat de 400 hieronder niet mede door #923 wordt veroorzaakt. */
    private static final String GELDIG_BSN = "123456782";

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    @AfterEach
    @Transactional
    void tearDown() {
        ScopeContactgegeven.deleteAll();
        Contactgegeven.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
    }

    /**
     * {@code contains} en niet {@code hasItem}: de pattern op {@code waarde} laat deze waarden door,
     * dus er hoort er precies één violation te zijn. Met {@code hasItem} zou een tweede melding met
     * een leeg veld — het gevolg van een vergeten {@code disableDefaultConstraintViolation} —
     * onopgemerkt blijven.
     *
     * <p>De laatste waarde heeft spaties eromheen. Die passeren de pattern op {@code waarde} en
     * komen dus echt bij {@code @Email} terecht; gemeten wijst die ze af.
     */
    @ParameterizedTest
    @ValueSource(strings = {"geen adres", "jan(at)rijksoverheid.nl", "jan@@rijksoverheid.nl",
            "@rijksoverheid.nl", " jan@rijksoverheid.nl "})
    void nieuwContactgegevenMetOngeldigEmailadresWordtAfgewezen(String waarde) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"%s"}
                        """.formatted(waarde))
                .when().post(CONTACTGEGEVEN)
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", contains("waarde"));

        // De constraint hoort vóór de resource te vuren, dus het ongeldige adres mag nooit bij de
        // verificatiedienst zijn aangeboden.
        Mockito.verifyNoInteractions(emailVerificatieService);
    }

    /**
     * Een blanco waarde bij {@code type: Email} hoort één violation op te leveren, niet twee: de
     * pattern op {@code waarde} wijst hem al af, en de e-mailvalidator slaat blanco daarom over.
     */
    @Test
    void blancoWaardeLevertGeenTweedeViolationOp() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"   "}
                        """)
                .when().post(CONTACTGEGEVEN)
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.size()", equalTo(1))
                .body("violations[0].field", equalTo("waarde"));
    }

    /**
     * Het contract is op dit punt strenger dan de server: {@code format: email} wijst een
     * bare-IPv4-domein af, Jakarta {@code @Email} accepteert het. De divergentie loopt één kant op —
     * het gepubliceerde contract belooft meer dan de service afdwingt. Deze test legt de serverkant
     * vast, {@code ContractHandhavingTest} de contractkant.
     *
     * <p>Daarom hangt {@code validationFilter} hier niet aan: die zou op de contractregel vallen,
     * niet op het servergedrag dat we willen vastleggen.
     */
    @Test
    void adresDatHetContractAfwijstMaarDeServerAccepteert() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"jan@123.45.67.89"}
                        """)
                .when().post(CONTACTGEGEVEN)
                .then()
                .statusCode(CREATED);
    }

    @Test
    void nieuwContactgegevenMetGeldigEmailadresWordtAangemaakt() {
        var body = new ContactgegevenRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111104");
        body.setType(ContactType.Email);
        body.setWaarde("jan@rijksoverheid.nl");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(CONTACTGEGEVEN)
                .then()
                .statusCode(CREATED);
    }

    /**
     * Dezelfde waarde die als e-mailadres wordt geweigerd, hoort onder een ander type gewoon door
     * te komen. Zonder deze kant zou een validator die het type negeert er ook groen uitzien.
     */
    @ParameterizedTest
    @EnumSource(value = ContactType.class, names = "Email", mode = EnumSource.Mode.EXCLUDE)
    void contactgegevenVanAnderTypeBlijftOngemoeid(ContactType type) {
        var body = new ContactgegevenRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111104");
        body.setType(type);
        body.setWaarde(ONGELDIG);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(CONTACTGEGEVEN)
                .then()
                .statusCode(CREATED);
    }

    @Test
    void wijzigingNaarOngeldigEmailadresWordtAfgewezen() {
        UUID id = bestaandEmailContactgegeven();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104","id":"%s",
                         "type":"Email","waarde":"%s"}
                        """.formatted(id, ONGELDIG))
                .when().put(CONTACTGEGEVEN)
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", contains("waarde"));
    }

    /**
     * De update-tegenhanger van {@code contactgegevenVanAnderTypeBlijftOngemoeid}: een bestaand
     * e-mailcontactgegeven omzetten naar een telefoonnummer hoort te slagen. Zonder deze kant is de
     * voorwaardelijkheid op de PUT-route alleen via de gedeelde validator gedekt.
     */
    @Test
    void wijzigingNaarAnderTypeBlijftOngemoeid() {
        var body = new ContactgegevenUpdateRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111104");
        body.setId(bestaandEmailContactgegeven());
        body.setType(ContactType.Telefoonnummer);
        body.setWaarde("0612345678");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().put(CONTACTGEGEVEN)
                .then()
                .statusCode(OK);
    }

    @Test
    void wijzigingNaarGeldigEmailadresSlaagt() {
        var body = new ContactgegevenUpdateRequest();
        body.setIdentificatieType(BSN);
        body.setIdentificatieNummer("111111104");
        body.setId(bestaandEmailContactgegeven());
        body.setType(ContactType.Email);
        body.setWaarde("nieuw@rijksoverheid.nl");

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().put(CONTACTGEGEVEN)
                .then()
                .statusCode(OK);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/profielservice/v1/emailverificatie",
            "/api/profielservice/v1/emailverificatie/code"})
    void emailverificatieMetOngeldigAdresWordtAfgewezen(String pad) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"%s",
                         "email":"%s","verificatieCode":"123456"}
                        """.formatted(GELDIG_BSN, ONGELDIG))
                .when().post(pad)
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("email"));
    }

    private UUID bestaandEmailContactgegeven() {
        AtomicReference<UUID> id = new AtomicReference<>();

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(BSN, "111111104"));
            partij.persist();
            Contactgegeven contactgegeven = new Contactgegeven();
            contactgegeven.setType(ContactType.Email);
            contactgegeven.setWaarde("oud@rijksoverheid.nl");
            contactgegeven.setPartij(partij);
            contactgegeven.persist();
            id.set(contactgegeven.id);
        });

        return id.get();
    }
}
