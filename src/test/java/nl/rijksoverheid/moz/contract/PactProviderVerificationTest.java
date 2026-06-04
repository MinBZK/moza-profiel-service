package nl.rijksoverheid.moz.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.VoorkeurType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;

/**
 * Verifieert dat de Profiel Service voldoet aan de contracten in src/test/resources/pacts/.
 *
 * Toekomstige migratie naar Pact Broker:
 *   Vervang @PactFolder door: @PactBroker(host = "${PACT_BROKER_HOST}")
 *   en voeg PACT_BROKER_HOST toe als GitHub Actions secret.
 *   Voeg ook een aparte GitHub Actions workflow toe voor can-i-deploy checks
 *   en het publiceren van pact-bestanden naar de broker.
 */
@QuarkusTest
@Provider("moza-profiel-service")
@PactFolder("src/test/resources/pacts")
public class PactProviderVerificationTest {

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    @BeforeEach
    void setupTarget(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", RestAssured.port));
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

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("Partij bestaat voor BSN 111111111")
    void partijBestaatVoorBsn111111111() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111111"));
            p.persist();
        });
    }

    @State("Geen Partij voor BSN 999999999")
    void geenPartijVoorBsn999999999() {
        // Geen setup nodig; database is leeg na tearDown van de vorige interactie.
    }

    @State("Geen Partij voor BSN 111111111")
    void geenPartijVoorBsn111111111() {
        // Geen setup nodig; database is leeg na tearDown van de vorige interactie.
    }

    @State("Partij met WebsiteTaal voorkeur voor BSN 111111111")
    void partijMetVoorkeurVoorBsn111111111() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111111"));
            p.persist();
            Voorkeur v = new Voorkeur();
            v.setVoorkeurType(VoorkeurType.WebsiteTaal);
            v.setWaarde("nl");
            v.setPartij(p);
            v.persist();
        });
    }

    @State("Geen Partij voor BSN 222222222")
    void geenPartijVoorBsn222222222() {
        // Geen setup nodig; database is leeg na tearDown van de vorige interactie.
    }

    @State("Partij bestaat voor BSN 111111111 maar niet voor BSN 999999999")
    void eenVanTweeBsnsBestaat() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111111"));
            p.persist();
        });
    }

    @State("Dienstverlener TestDV bestaat")
    void dienstverlenerTestDvBestaat() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener d = new Dienstverlener();
            d.setNaam("TestDV");
            d.persist();
        });
    }

    @State("Geen Dienstverlener bestaat")
    void geenDienstverlenerBestaat() {
        // Geen setup nodig; database is leeg na tearDown van de vorige interactie.
    }
}
