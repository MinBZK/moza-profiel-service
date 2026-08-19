package nl.rijksoverheid.moz.mapper;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenResponse;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurResponse;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@QuarkusTest
class PartijMapperTest {

    @Inject
    PartijMapper partijMapper;

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

    // ---------------------------------------------------------------------
    // Zuivere mapping (in-memory)
    // ---------------------------------------------------------------------

    @Test
    void mapContactgegeven_maptAlleVeldenEnScopes() {
        DienstverlenerDienst link = link("Gemeente Amsterdam", "Verhuizen");
        Contactgegeven cg = contactgegeven(ContactType.Email, "test@example.com");
        cg.setIsGeverifieerd(true);
        cg.setIsDefault(true);
        cg.addScope(new ScopeContactgegeven(cg, link));

        ContactgegevenResponse response = partijMapper.mapContactgegeven(cg);

        Assertions.assertEquals(cg.id, response.getId());
        Assertions.assertEquals(ContactType.Email, response.getType());
        Assertions.assertEquals("test@example.com", response.getWaarde());
        Assertions.assertTrue(response.getIsGeverifieerd());
        Assertions.assertTrue(response.getIsDefault());
        Assertions.assertEquals(1, response.getScopes().size());
        Assertions.assertEquals("Gemeente Amsterdam", response.getScopes().get(0).getDienstverlenerNaam());
        Assertions.assertEquals("Verhuizen", response.getScopes().get(0).getDienstNaam());
    }

    @Test
    void mapContactgegeven_scopeZonderDienst_dienstNaamIsNull() {
        DienstverlenerDienst link = new DienstverlenerDienst(dienstverlener("Gemeente Utrecht"), null);
        Contactgegeven cg = contactgegeven(ContactType.Email, "geen-dienst@example.com");
        cg.addScope(new ScopeContactgegeven(cg, link));

        ContactgegevenResponse response = partijMapper.mapContactgegeven(cg);

        Assertions.assertEquals("Gemeente Utrecht", response.getScopes().get(0).getDienstverlenerNaam());
        Assertions.assertNull(response.getScopes().get(0).getDienstNaam());
    }

    @Test
    void mapVoorkeur_maptAlleVeldenEnScopes() {
        DienstverlenerDienst link = link("Gemeente Rotterdam", "Parkeren");
        Voorkeur voorkeur = voorkeur(VoorkeurType.WebsiteTaal, "nl");
        voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));

        VoorkeurResponse response = partijMapper.mapVoorkeur(voorkeur);

        Assertions.assertEquals(voorkeur.id, response.getId());
        Assertions.assertEquals(VoorkeurType.WebsiteTaal, response.getVoorkeurType());
        Assertions.assertEquals("nl", response.getWaarde());
        Assertions.assertEquals(1, response.getScopes().size());
        Assertions.assertEquals("Gemeente Rotterdam", response.getScopes().get(0).getDienstverlenerNaam());
        Assertions.assertEquals("Parkeren", response.getScopes().get(0).getDienstNaam());
    }

    @Test
    void toResponse_maptPartijMetAlleCollecties() {
        UUID partijId = persistPartijMetContactgegevenEnVoorkeur();

        AtomicReference<PartijResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            response.set(partijMapper.toResponse(partij, Contactgegeven.find(partij), Voorkeur.find(partij)));
        });

        Assertions.assertEquals(partijId, response.get().getPartijId());
        Assertions.assertEquals(1, response.get().getIdentificaties().size());
        Assertions.assertEquals(IdentificatieType.BSN, response.get().getIdentificaties().get(0).getIdentificatieType());
        Assertions.assertEquals("123456789", response.get().getIdentificaties().get(0).getIdentificatieNummer());
        Assertions.assertEquals(1, response.get().getContactgegevens().size());
        Assertions.assertEquals("a@example.com", response.get().getContactgegevens().get(0).getWaarde());
        Assertions.assertEquals(1, response.get().getVoorkeuren().size());
        Assertions.assertEquals("nl", response.get().getVoorkeuren().get(0).getWaarde());
    }

    @Test
    void toResponse_legeCollecties_gevenLegeLijsten() {
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();

        PartijResponse response = partijMapper.toResponse(partij, List.of(), List.of());

        Assertions.assertTrue(response.getIdentificaties().isEmpty());
        Assertions.assertTrue(response.getContactgegevens().isEmpty());
        Assertions.assertTrue(response.getVoorkeuren().isEmpty());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Dienstverlener dienstverlener(String naam) {
        Dienstverlener dv = new Dienstverlener();
        dv.id = UUID.randomUUID();
        dv.setNaam(naam);
        return dv;
    }

    private static DienstverlenerDienst link(String dienstverlenerNaam, String dienstNaam) {
        Dienst dienst = new Dienst();
        dienst.id = UUID.randomUUID();
        dienst.setNaam(dienstNaam);
        return new DienstverlenerDienst(dienstverlener(dienstverlenerNaam), dienst);
    }

    /** Bouwt een niet-persistent contactgegeven voor mapping-tests. */
    private static Contactgegeven contactgegeven(ContactType type, String waarde) {
        Contactgegeven cg = new Contactgegeven();
        cg.id = UUID.randomUUID();
        cg.setType(type);
        cg.setWaarde(waarde);
        return cg;
    }

    /** Bouwt een niet-persistente voorkeur voor mapping-tests. */
    private static Voorkeur voorkeur(VoorkeurType type, String waarde) {
        Voorkeur voorkeur = new Voorkeur();
        voorkeur.id = UUID.randomUUID();
        voorkeur.setVoorkeurType(type);
        voorkeur.setWaarde(waarde);
        return voorkeur;
    }

    private UUID persistPartijMetContactgegevenEnVoorkeur() {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Email);
            cg.setWaarde("a@example.com");
            cg.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(partij);
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.persist();

            id.set(partij.id);
        });
        return id.get();
    }
}
