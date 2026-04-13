package nl.rijksoverheid.moz.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Scope;
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
        Contactgegeven.deleteAll();
        Voorkeur.deleteAll();
        Scope.deleteAll();
        Dienst.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
        Dienstverlener.deleteAll();
    }

    @Test
    void addDienstverlener_NewDienstverlener() {
        DienstverlenerRequest request = new DienstverlenerRequest();
        request.naam = "TestDienstverlener";
        request.oin = "12345";

        dienstverlenerService.addDienstverlener(request);

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = Dienstverlener.find("naam", "TestDienstverlener").firstResult();
            Assertions.assertNotNull(dienstverlener);
            Assertions.assertEquals("12345", dienstverlener.getOin());
            Assertions.assertEquals(1, dienstverlener.getDiensten().size());
            Assertions.assertEquals("Alles", dienstverlener.getDiensten().get(0).getBeschrijving());
        });
    }

    @Test
    void addDienstverlener_ExistingDienstverlener() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("ExistingDV");
            dienstverlener.setOin("99999");
            dienstverlener.persist();
        });

        DienstverlenerRequest request = new DienstverlenerRequest();
        request.naam = "ExistingDV";
        request.oin = "12345";

        dienstverlenerService.addDienstverlener(request);

        QuarkusTransaction.requiringNew().run(() -> {
            long count = Dienstverlener.count("lower(naam) = lower(?1)", "ExistingDV");
            Assertions.assertEquals(1, count);
        });
    }

    @Test
    void getDienstenVoorDienstverlener_Found() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();

            Dienst dienst = new Dienst();
            dienst.setBeschrijving("TestDienst");
            dienst.setDienstverlener(dienstverlener);
            dienst.persist();
        });

        Dienstverlener result = dienstverlenerService.getDienstenVoorDienstverlener("TestDV");

        Assertions.assertNotNull(result);
        Assertions.assertEquals("TestDV", result.getNaam());
    }

    @Test
    void getDienstenVoorDienstverlener_NotFound() {
        Dienstverlener result = dienstverlenerService.getDienstenVoorDienstverlener("NonExistent");
        Assertions.assertNull(result);
    }

    @Test
    void addDienstToDienstverlener_ExistingDienstverlener() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();
        });

        DienstRequest request = new DienstRequest();
        request.beschrijving = "NewDienst";

        Dienst result = dienstverlenerService.addDienstToDienstverlener("TestDV", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NewDienst", result.getBeschrijving());

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = Dienstverlener.find("naam", "TestDV").firstResult();
            Assertions.assertNotNull(dienstverlener);
            Assertions.assertTrue(dienstverlener.getDiensten().size() >= 1);
        });
    }

    @Test
    void addDienstToDienstverlener_NewDienstverlener() {
        DienstRequest request = new DienstRequest();
        request.beschrijving = "NewDienst";

        Dienst result = dienstverlenerService.addDienstToDienstverlener("NewDV", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NewDienst", result.getBeschrijving());

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = Dienstverlener.find("naam", "NewDV").firstResult();
            Assertions.assertNotNull(dienstverlener);
            Assertions.assertEquals(2, dienstverlener.getDiensten().size()); // "Alles" + "NewDienst"
        });
    }

    @Test
    void findOrCreateDienstverlener_CreateNew() {
        Dienstverlener result = dienstverlenerService.findOrCreateDienstverlener("NewDV", "12345");

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NewDV", result.getNaam());
        Assertions.assertEquals("12345", result.getOin());
        Assertions.assertEquals(1, result.getDiensten().size());
        Assertions.assertEquals("Alles", result.getDiensten().get(0).getBeschrijving());
    }

    @Test
    void findOrCreateDienstverlener_FindExisting() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("ExistingDV");
            dienstverlener.setOin("99999");
            dienstverlener.persist();
        });

        Dienstverlener result = dienstverlenerService.findOrCreateDienstverlener("ExistingDV", "12345");

        Assertions.assertNotNull(result);
        Assertions.assertEquals("ExistingDV", result.getNaam());
        Assertions.assertEquals("99999", result.getOin());

        QuarkusTransaction.requiringNew().run(() -> {
            long count = Dienstverlener.count("lower(naam) = lower(?1)", "ExistingDV");
            Assertions.assertEquals(1, count);
        });
    }

    @Test
    void findOrCreateDienstverlener_CaseInsensitive() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
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

    @Test
    void findOrCreateDienstverlener_DefaultDienstCreated() {
        Dienstverlener result = dienstverlenerService.findOrCreateDienstverlener("TestDV", "12345");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getDiensten().size());

        Dienst defaultDienst = result.getDiensten().get(0);
        Assertions.assertEquals("Alles", defaultDienst.getBeschrijving());
        Assertions.assertEquals(result, defaultDienst.getDienstverlener());
    }

}
