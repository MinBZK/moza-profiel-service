package nl.rijksoverheid.moz.mapper;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
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
import java.time.temporal.ChronoUnit;
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
    // Zuivere mapping (in-memory, niet-stale zodat de retentie-touch niet triggert)
    // ---------------------------------------------------------------------

    @Test
    void toContactgegevensResponse_maptAlleVeldenEnScopes() {
        DienstverlenerDienst link = link("Gemeente Amsterdam", "Verhuizen");
        Contactgegeven cg = contactgegeven(ContactType.Email, "test@example.com");
        cg.setIsGeverifieerd(true);
        cg.setIsDefault(true);
        cg.addScope(new ScopeContactgegeven(cg, link));

        ContactgegevenResponse response = partijMapper.toContactgegevensResponse(cg);

        Assertions.assertEquals(cg.id, response.id);
        Assertions.assertEquals(ContactType.Email, response.type);
        Assertions.assertEquals("test@example.com", response.waarde);
        Assertions.assertTrue(response.isGeverifieerd);
        Assertions.assertTrue(response.isDefault);
        Assertions.assertEquals(1, response.scopes.size());
        Assertions.assertEquals("Gemeente Amsterdam", response.scopes.get(0).dienstverlenerNaam);
        Assertions.assertEquals("Verhuizen", response.scopes.get(0).dienstNaam);
    }

    @Test
    void toContactgegevensResponse_scopeZonderDienst_dienstNaamIsNull() {
        DienstverlenerDienst link = new DienstverlenerDienst(dienstverlener("Gemeente Utrecht"), null);
        Contactgegeven cg = contactgegeven(ContactType.Email, "geen-dienst@example.com");
        cg.addScope(new ScopeContactgegeven(cg, link));

        ContactgegevenResponse response = partijMapper.toContactgegevensResponse(cg);

        Assertions.assertEquals("Gemeente Utrecht", response.scopes.get(0).dienstverlenerNaam);
        Assertions.assertNull(response.scopes.get(0).dienstNaam);
    }

    @Test
    void toVoorkeurResponse_maptAlleVeldenEnScopes() {
        DienstverlenerDienst link = link("Gemeente Rotterdam", "Parkeren");
        Voorkeur voorkeur = voorkeur(VoorkeurType.WebsiteTaal, "nl");
        voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));

        VoorkeurResponse response = partijMapper.toVoorkeurResponse(voorkeur);

        Assertions.assertEquals(voorkeur.id, response.id);
        Assertions.assertEquals(VoorkeurType.WebsiteTaal, response.voorkeurType);
        Assertions.assertEquals("nl", response.waarde);
        Assertions.assertEquals(1, response.scopes.size());
        Assertions.assertEquals("Gemeente Rotterdam", response.scopes.get(0).dienstverlenerNaam);
        Assertions.assertEquals("Parkeren", response.scopes.get(0).dienstNaam);
    }

    @Test
    void toResponse_maptPartijMetAlleCollecties() {
        UUID partijId = persistPartijMetContactgegevenEnVoorkeur();

        AtomicReference<PartijResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            response.set(partijMapper.toResponse(partij, Contactgegeven.findActief(partij), Voorkeur.findActief(partij)));
        });

        Assertions.assertEquals(partijId, response.get().partijId);
        Assertions.assertEquals(1, response.get().identificaties.size());
        Assertions.assertEquals(IdentificatieType.BSN, response.get().identificaties.get(0).identificatieType);
        Assertions.assertEquals("123456789", response.get().identificaties.get(0).identificatieNummer);
        Assertions.assertEquals(1, response.get().contactgegevens.size());
        Assertions.assertEquals("a@example.com", response.get().contactgegevens.get(0).waarde);
        Assertions.assertEquals(1, response.get().voorkeuren.size());
        Assertions.assertEquals("nl", response.get().voorkeuren.get(0).waarde);
    }

    @Test
    void toResponse_legeCollecties_gevenLegeLijsten() {
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();

        PartijResponse response = partijMapper.toResponse(partij, List.of(), List.of());

        Assertions.assertTrue(response.identificaties.isEmpty());
        Assertions.assertTrue(response.contactgegevens.isEmpty());
        Assertions.assertTrue(response.voorkeuren.isEmpty());
    }

    // ---------------------------------------------------------------------
    // Retentie-logica ("touch on read") die in de map-methodes lastUsedAt bijwerkt
    // ---------------------------------------------------------------------

    @Test
    void toContactgegevensResponse_stale_werktLastUsedAtBij() {
        UUID cgId = persistContactgegeven(ouder());
        Instant voorMapping = Instant.now();

        QuarkusTransaction.requiringNew().run(() ->
                partijMapper.toContactgegevensResponse(Contactgegeven.findById(cgId)));

        QuarkusTransaction.requiringNew().run(() -> {
            Instant lastUsedAt = Contactgegeven.<Contactgegeven>findById(cgId).getLastUsedAt();
            Assertions.assertNotNull(lastUsedAt);
            Assertions.assertFalse(lastUsedAt.isBefore(voorMapping),
                    "lastUsedAt moet het moment van lezen weerspiegelen");
        });
    }

    @Test
    void toContactgegevensResponse_nietStale_laatLastUsedAtOngemoeid() {
        Instant recent = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID cgId = persistContactgegeven(recent);

        QuarkusTransaction.requiringNew().run(() ->
                partijMapper.toContactgegevensResponse(Contactgegeven.findById(cgId)));

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(recent, Contactgegeven.<Contactgegeven>findById(cgId).getLastUsedAt()));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Instant ouder() {
        return Instant.now().minus(Duration.ofHours(48));
    }

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

    /** Bouwt een niet-persistent, niet-stale contactgegeven zodat de retentie-touch niet triggert. */
    private static Contactgegeven contactgegeven(ContactType type, String waarde) {
        Contactgegeven cg = new Contactgegeven();
        cg.id = UUID.randomUUID();
        cg.setType(type);
        cg.setWaarde(waarde);
        cg.setLastUsedAt(Instant.now());
        return cg;
    }

    /** Bouwt een niet-persistente, niet-stale voorkeur zodat de retentie-touch niet triggert. */
    private static Voorkeur voorkeur(VoorkeurType type, String waarde) {
        Voorkeur voorkeur = new Voorkeur();
        voorkeur.id = UUID.randomUUID();
        voorkeur.setVoorkeurType(type);
        voorkeur.setWaarde(waarde);
        voorkeur.setLastUsedAt(Instant.now());
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

    private UUID persistContactgegeven(Instant lastUsedAt) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Email);
            cg.setWaarde("touch@example.com");
            cg.setLastUsedAt(lastUsedAt);
            cg.persist();
            id.set(cg.id);
        });
        return id.get();
    }
}
