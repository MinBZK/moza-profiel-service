package nl.rijksoverheid.moz.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
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

@QuarkusTest
public class DienstverlenerServiceTest {

    @Inject
    DienstverlenerService dienstverlenerService;

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
    void addDienstverlener_NewDienstverlener() {
        DienstverlenerRequest request = new DienstverlenerRequest();
        request.naam = "TestDienstverlener";
        request.beschrijving = "Een test dienstverlener";

        dienstverlenerService.addDienstverlener(request);

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = Dienstverlener.find("naam", "TestDienstverlener").firstResult();
            Assertions.assertNotNull(dienstverlener);
            Assertions.assertEquals("Een test dienstverlener", dienstverlener.getBeschrijving());
        });
    }

    @Test
    void addDienstverlener_ExistingDienstverlener_NoDuplicate() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("ExistingDV");
            dienstverlener.persist();
        });

        DienstverlenerRequest request = new DienstverlenerRequest();
        request.naam = "ExistingDV";

        dienstverlenerService.addDienstverlener(request);

        QuarkusTransaction.requiringNew().run(() -> {
            long count = Dienstverlener.count("lower(naam) = lower(?1)", "ExistingDV");
            Assertions.assertEquals(1, count);
        });
    }

    @Test
    void getDienstverlener_Found() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.persist();
        });

        Dienstverlener result = dienstverlenerService.getDienstverlener("TestDV");

        Assertions.assertNotNull(result);
        Assertions.assertEquals("TestDV", result.getNaam());
    }

    @Test
    void getDienstverlener_NotFound() {
        Dienstverlener result = dienstverlenerService.getDienstverlener("NonExistent");
        Assertions.assertNull(result);
    }

    @Test
    void addDienstToDienstverlener_ExistingDienstverlener() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.persist();
        });

        DienstRequest request = new DienstRequest();
        request.naam = "NieuweDienst";
        request.beschrijving = "Optionele toelichting";

        Dienst result = dienstverlenerService.addDienstToDienstverlener("TestDV", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NieuweDienst", result.getNaam());

        QuarkusTransaction.requiringNew().run(() -> {
            long links = DienstverlenerDienst.count();
            Assertions.assertEquals(1, links);
        });
    }

    @Test
    void addDienstToDienstverlener_ExistingDienstWithDifferentBeschrijving_Throws409() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dvA = new Dienstverlener();
            dvA.setNaam("DV-A");
            dvA.persist();
            Dienst shared = new Dienst();
            shared.setNaam("Vergunning");
            shared.setBeschrijving("originele beschrijving");
            shared.persist();
            new DienstverlenerDienst(dvA, shared).persist();

            Dienstverlener dvB = new Dienstverlener();
            dvB.setNaam("DV-B");
            dvB.persist();
        });

        DienstRequest request = new DienstRequest();
        request.naam = "Vergunning";
        request.beschrijving = "andere beschrijving";

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> dienstverlenerService.addDienstToDienstverlener("DV-B", request));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());
    }

    @Test
    void addDienstToDienstverlener_ExistingDienstReusedWhenBeschrijvingOmitted() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dvA = new Dienstverlener();
            dvA.setNaam("DV-A");
            dvA.persist();
            Dienst shared = new Dienst();
            shared.setNaam("Vergunning");
            shared.setBeschrijving("originele beschrijving");
            shared.persist();
            new DienstverlenerDienst(dvA, shared).persist();

            Dienstverlener dvB = new Dienstverlener();
            dvB.setNaam("DV-B");
            dvB.persist();
        });

        DienstRequest request = new DienstRequest();
        request.naam = "Vergunning";

        Dienst result = dienstverlenerService.addDienstToDienstverlener("DV-B", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("Vergunning", result.getNaam());
        Assertions.assertEquals("originele beschrijving", result.getBeschrijving());

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(1, Dienst.count("naam", "Vergunning"));
            Assertions.assertEquals(2, DienstverlenerDienst.count());
        });
    }

    @Test
    void findOrCreateDienstverlener_CaseInsensitive() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.persist();
        });

        Dienstverlener result = dienstverlenerService.findOrCreateDienstverlener("testdv", null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("TestDV", result.getNaam());

        QuarkusTransaction.requiringNew().run(() -> {
            long count = Dienstverlener.count();
            Assertions.assertEquals(1, count);
        });
    }
}
