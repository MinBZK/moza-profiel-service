package nl.rijksoverheid.moz.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.dto.request.AfdelingRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
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
        Afdeling.deleteAll();
        Identificatie.deleteAll();
        Voorkeur.deleteAll();
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
            Assertions.assertEquals(1, dienstverlener.getAfdelingen().size());
            Assertions.assertEquals("Alles", dienstverlener.getAfdelingen().get(0).getBeschrijving());
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
    void getAfdelingenVoorDienstverlener_Found() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();

            Afdeling afdeling = new Afdeling();
            afdeling.setBeschrijving("TestAfdeling");
            afdeling.setDienstverlener(dienstverlener);
            afdeling.persist();
        });

        Dienstverlener result = dienstverlenerService.getAfdelingenVoorDienstverlener("TestDV");

        Assertions.assertNotNull(result);
        Assertions.assertEquals("TestDV", result.getNaam());
    }

    @Test
    void getAfdelingenVoorDienstverlener_NotFound() {
        Dienstverlener result = dienstverlenerService.getAfdelingenVoorDienstverlener("NonExistent");
        Assertions.assertNull(result);
    }

    @Test
    void addAfdelingToDienstverlener_ExistingDienstverlener() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();
        });

        AfdelingRequest request = new AfdelingRequest();
        request.beschrijving = "NewAfdeling";

        Afdeling result = dienstverlenerService.addAfdelingToDienstverlener("TestDV", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NewAfdeling", result.getBeschrijving());

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = Dienstverlener.find("naam", "TestDV").firstResult();
            Assertions.assertNotNull(dienstverlener);
            Assertions.assertTrue(dienstverlener.getAfdelingen().size() >= 1);
        });
    }

    @Test
    void addAfdelingToDienstverlener_NewDienstverlener() {
        AfdelingRequest request = new AfdelingRequest();
        request.beschrijving = "NewAfdeling";

        Afdeling result = dienstverlenerService.addAfdelingToDienstverlener("NewDV", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NewAfdeling", result.getBeschrijving());

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = Dienstverlener.find("naam", "NewDV").firstResult();
            Assertions.assertNotNull(dienstverlener);
            Assertions.assertEquals(2, dienstverlener.getAfdelingen().size()); // "Alles" + "NewAfdeling"
        });
    }

    @Test
    void findOrCreateDienstverlener_CreateNew() {
        Dienstverlener result = dienstverlenerService.findOrCreateDienstverlener("NewDV", "12345");

        Assertions.assertNotNull(result);
        Assertions.assertEquals("NewDV", result.getNaam());
        Assertions.assertEquals("12345", result.getOin());
        Assertions.assertEquals(1, result.getAfdelingen().size());
        Assertions.assertEquals("Alles", result.getAfdelingen().get(0).getBeschrijving());
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
        Assertions.assertEquals("99999", result.getOin()); // Original OIN, not the new one

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
        Assertions.assertEquals("TestDV", result.getNaam()); // Original case

        QuarkusTransaction.requiringNew().run(() -> {
            long count = Dienstverlener.count();
            Assertions.assertEquals(1, count);
        });
    }

    @Test
    void findOrCreateDienstverlener_DefaultAfdelingCreated() {
        Dienstverlener result = dienstverlenerService.findOrCreateDienstverlener("TestDV", "12345");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getAfdelingen().size());

        Afdeling defaultAfdeling = result.getAfdelingen().get(0);
        Assertions.assertEquals("Alles", defaultAfdeling.getBeschrijving());
        Assertions.assertEquals(result, defaultAfdeling.getDienstverlener());
    }

}
