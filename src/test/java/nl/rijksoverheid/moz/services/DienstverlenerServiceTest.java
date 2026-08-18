package nl.rijksoverheid.moz.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.api.generated.model.DienstRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.DatabaseCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class DienstverlenerServiceTest {

    @Inject
    DienstverlenerService dienstverlenerService;

    @AfterEach
    void tearDown() {
        DatabaseCleanup.wipe();
    }

    @Test
    void addDienstverlener_NewDienstverlener() {
        DienstverlenerRequest request = new DienstverlenerRequest();
        request.setNaam("TestDienstverlener");
        request.setBeschrijving("Een test dienstverlener");

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
        request.setNaam("ExistingDV");

        dienstverlenerService.addDienstverlener(request);

        QuarkusTransaction.requiringNew().run(() -> {
            long count = Dienstverlener.count("lower(naam) = lower(?1)", "ExistingDV");
            Assertions.assertEquals(1, count);
        });
    }

    /**
     * De beschrijving van een bestaande dienstverlener werd stil genegeerd: de aanroeper kreeg
     * een 201 met de oude waarde terug. Dat is dezelfde afweging als bij een dienst met een
     * andere beschrijving, en het contract documenteerde de 409 al.
     */
    @Test
    void addDienstverlener_ExistingDienstverlenerMetAndereBeschrijving_Conflict() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("ExistingDV");
            dienstverlener.setBeschrijving("originele beschrijving");
            dienstverlener.persist();
        });

        DienstverlenerRequest request = new DienstverlenerRequest();
        request.setNaam("ExistingDV");
        request.setBeschrijving("andere beschrijving");

        BusinessException fout = Assertions.assertThrows(BusinessException.class,
                () -> dienstverlenerService.addDienstverlener(request));

        Assertions.assertEquals(BusinessException.Kind.CONFLICT, fout.getKind());

        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener onveranderd = Dienstverlener.find("naam", "ExistingDV").firstResult();
            Assertions.assertEquals("originele beschrijving", onveranderd.getBeschrijving());
        });
    }

    /**
     * {@code addDienstToDienstverlener} maakt de dienstverlener aan met een lege beschrijving.
     * Een {@code null} legt dus niets vast en mag nooit botsen, ook niet als er al een
     * beschrijving staat.
     */
    @Test
    void findOrCreateDienstverlener_ZonderBeschrijving_GeenConflict() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("ExistingDV");
            dienstverlener.setBeschrijving("originele beschrijving");
            dienstverlener.persist();
        });

        Dienstverlener gevonden = dienstverlenerService.findOrCreateDienstverlener("ExistingDV", null);

        Assertions.assertEquals("originele beschrijving", gevonden.getBeschrijving());
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
        request.setNaam("NieuweDienst");
        request.setBeschrijving("Optionele toelichting");

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
        request.setNaam("Vergunning");
        request.setBeschrijving("andere beschrijving");

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
        request.setNaam("Vergunning");

        Dienst result = dienstverlenerService.addDienstToDienstverlener("DV-B", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("Vergunning", result.getNaam());
        Assertions.assertEquals("originele beschrijving", result.getBeschrijving());

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(1, Dienst.count("naam", "Vergunning"));
            Assertions.assertEquals(2, DienstverlenerDienst.count());
        });
    }

    /**
     * Tweemaal dezelfde dienst op dezelfde dienstverlener hoort de bestaande koppeling terug te
     * geven, niet een tweede rij of een botsing op de unique constraint.
     */
    @Test
    void addDienstToDienstverlener_TweemaalDezelfdeDienst_IsIdempotent() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("DV-Idem");
            dv.persist();
        });

        DienstRequest request = new DienstRequest();
        request.setNaam("Vergunning");

        Dienst eerste = dienstverlenerService.addDienstToDienstverlener("DV-Idem", request);
        Dienst tweede = dienstverlenerService.addDienstToDienstverlener("DV-Idem", request);

        Assertions.assertEquals(eerste.id, tweede.id);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(1, Dienst.count("naam", "Vergunning"));
            Assertions.assertEquals(1, DienstverlenerDienst.count());
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
