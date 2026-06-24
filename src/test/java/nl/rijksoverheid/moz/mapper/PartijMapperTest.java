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
        cg.setTeVerwijderenOp(Instant.now().plus(Duration.ofDays(30)));
        cg.addScope(new ScopeContactgegeven(cg, link));

        ContactgegevenResponse response = partijMapper.toContactgegevensResponse(cg);

        Assertions.assertEquals(cg.id, response.getId());
        Assertions.assertEquals(ContactType.Email, response.getType());
        Assertions.assertEquals("test@example.com", response.getWaarde());
        Assertions.assertTrue(response.getIsGeverifieerd());
        Assertions.assertTrue(response.getIsDefault());
        Assertions.assertEquals(cg.getTeVerwijderenOp(), response.getTeVerwijderenOp());
        Assertions.assertEquals(1, response.getScopes().size());
        Assertions.assertEquals("Gemeente Amsterdam", response.getScopes().get(0).getDienstverlenerNaam());
        Assertions.assertEquals("Verhuizen", response.getScopes().get(0).getDienstNaam());
    }

    @Test
    void toContactgegevensResponse_scopeZonderDienst_dienstNaamIsNull() {
        DienstverlenerDienst link = new DienstverlenerDienst(dienstverlener("Gemeente Utrecht"), null);
        Contactgegeven cg = contactgegeven(ContactType.Email, "geen-dienst@example.com");
        cg.addScope(new ScopeContactgegeven(cg, link));

        ContactgegevenResponse response = partijMapper.toContactgegevensResponse(cg);

        Assertions.assertEquals("Gemeente Utrecht", response.getScopes().get(0).getDienstverlenerNaam());
        Assertions.assertNull(response.getScopes().get(0).getDienstNaam());
    }

    @Test
    void toVoorkeurResponse_maptAlleVeldenEnScopes() {
        DienstverlenerDienst link = link("Gemeente Rotterdam", "Parkeren");
        Voorkeur voorkeur = voorkeur(VoorkeurType.WebsiteTaal, "nl");
        voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));

        VoorkeurResponse response = partijMapper.toVoorkeurResponse(voorkeur);

        Assertions.assertEquals(voorkeur.id, response.getId());
        Assertions.assertEquals(VoorkeurType.WebsiteTaal, response.getVoorkeurType());
        Assertions.assertEquals("nl", response.getWaarde());
        Assertions.assertEquals(1, response.getScopes().size());
        Assertions.assertEquals("Gemeente Rotterdam", response.getScopes().get(0).getDienstverlenerNaam());
        Assertions.assertEquals("Parkeren", response.getScopes().get(0).getDienstNaam());
    }

    @Test
    void toResponse_maptPartijMetAlleCollecties() {
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();
        partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
        partij.setContactgegevens(List.of(contactgegeven(ContactType.Email, "a@example.com")));
        partij.setVoorkeuren(List.of(voorkeur(VoorkeurType.WebsiteTaal, "nl")));

        PartijResponse response = partijMapper.toResponse(partij);

        Assertions.assertEquals(partij.id, response.getPartijId());
        Assertions.assertEquals(1, response.getIdentificaties().size());
        Assertions.assertEquals(IdentificatieType.BSN, response.getIdentificaties().get(0).getIdentificatieType());
        Assertions.assertEquals("123456789", response.getIdentificaties().get(0).getIdentificatieNummer());
        Assertions.assertEquals(1, response.getContactgegevens().size());
        Assertions.assertEquals("a@example.com", response.getContactgegevens().get(0).getWaarde());
        Assertions.assertEquals(1, response.getVoorkeuren().size());
        Assertions.assertEquals("nl", response.getVoorkeuren().get(0).getWaarde());
    }

    @Test
    void toResponse_legeCollecties_gevenLegeLijsten() {
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();

        PartijResponse response = partijMapper.toResponse(partij);

        Assertions.assertTrue(response.getIdentificaties().isEmpty());
        Assertions.assertTrue(response.getContactgegevens().isEmpty());
        Assertions.assertTrue(response.getVoorkeuren().isEmpty());
    }

    // ---------------------------------------------------------------------
    // Retentie-logica ("touch on read") die in de map-methodes de response overschrijft
    // ---------------------------------------------------------------------

    @Test
    void toContactgegevensResponse_staleEnAutomatisch_responseHeeftVerwijderdatumTeruggedraaid() {
        UUID cgId = persistContactgegeven(ouder(), Instant.now().plus(Duration.ofDays(30)), true);
        Instant voorMapping = Instant.now();

        AtomicReference<ContactgegevenResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toContactgegevensResponse(Contactgegeven.findById(cgId))));

        Assertions.assertNull(response.get().getTeVerwijderenOp(),
                "Een automatisch gezette verwijderdatum moet in de response zijn teruggedraaid");
        Assertions.assertNotNull(response.get().getLastUpdated());
        Assertions.assertFalse(response.get().getLastUpdated().isBefore(voorMapping),
                "lastUpdated moet het moment van teruggedraaien weerspiegelen");

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven cg = Contactgegeven.findById(cgId);
            Assertions.assertNull(cg.getTeVerwijderenOp());
            Assertions.assertFalse(cg.isTeVerwijderenOpAutomatisch());
        });
    }

    @Test
    void toContactgegevensResponse_staleEnHandmatig_behoudtVerwijderdatum() {
        Instant handmatig = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS);
        UUID cgId = persistContactgegeven(ouder(), handmatig, false);

        AtomicReference<ContactgegevenResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toContactgegevensResponse(Contactgegeven.findById(cgId))));

        Assertions.assertEquals(handmatig, response.get().getTeVerwijderenOp(),
                "Een handmatig gezette verwijderdatum mag niet worden teruggedraaid");

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven cg = Contactgegeven.findById(cgId);
            Assertions.assertEquals(handmatig, cg.getTeVerwijderenOp());
            Assertions.assertNotNull(cg.getLastUsedAt());
        });
    }

    @Test
    void toContactgegevensResponse_versEnAutomatisch_wordtNietAangeraakt() {
        // Binnen de drempel van 24 uur mag een leesactie geen schrijfactie veroorzaken:
        // anders zou elke GET de verwijderdatum van een net gebruikte rij wissen.
        Instant lastUsedAt = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS);
        UUID cgId = persistContactgegeven(lastUsedAt, teVerwijderenOp, true);

        AtomicReference<ContactgegevenResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toContactgegevensResponse(Contactgegeven.findById(cgId))));

        Assertions.assertEquals(teVerwijderenOp, response.get().getTeVerwijderenOp());

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven cg = Contactgegeven.findById(cgId);
            Assertions.assertTrue(cg.isTeVerwijderenOpAutomatisch());
            Assertions.assertEquals(lastUsedAt, cg.getLastUsedAt(), "lastUsedAt mag niet zijn aangeraakt");
        });
    }

    @Test
    void toVoorkeurResponse_staleEnAutomatisch_responseHeeftVerwijderdatumTeruggedraaid() {
        UUID voorkeurId = persistVoorkeur(ouder(), Instant.now().plus(Duration.ofDays(30)), true);
        Instant voorMapping = Instant.now();

        AtomicReference<VoorkeurResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toVoorkeurResponse(Voorkeur.findById(voorkeurId))));

        Assertions.assertNull(response.get().getTeVerwijderenOp(),
                "Een automatisch gezette verwijderdatum moet in de response zijn teruggedraaid");
        Assertions.assertFalse(response.get().getLastUpdated().isBefore(voorMapping),
                "lastUpdated moet het moment van teruggedraaien weerspiegelen");

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNull(voorkeur.getTeVerwijderenOp());
            Assertions.assertFalse(voorkeur.isTeVerwijderenOpAutomatisch());
            Assertions.assertFalse(voorkeur.getLastUsedAt().isBefore(voorMapping),
                    "lastUsedAt moet zijn bijgewerkt bij het lezen van een stale voorkeur");
        });
    }

    @Test
    void toVoorkeurResponse_staleEnHandmatig_behoudtVerwijderdatum() {
        Instant handmatig = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS);
        UUID voorkeurId = persistVoorkeur(ouder(), handmatig, false);
        Instant voorMapping = Instant.now();

        AtomicReference<VoorkeurResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toVoorkeurResponse(Voorkeur.findById(voorkeurId))));

        Assertions.assertEquals(handmatig, response.get().getTeVerwijderenOp(),
                "Een handmatig gezette verwijderdatum mag niet worden teruggedraaid");

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertEquals(handmatig, voorkeur.getTeVerwijderenOp());
            // De verwijderdatum blijft staan, maar de touch-on-read moet wél zijn uitgevoerd:
            // lastUsedAt gaat van 48 uur oud naar nu.
            Assertions.assertFalse(voorkeur.getLastUsedAt().isBefore(voorMapping),
                    "lastUsedAt moet zijn bijgewerkt bij het lezen van een stale voorkeur");
        });
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

    private UUID persistContactgegeven(Instant lastUsedAt, Instant teVerwijderenOp, boolean automatisch) {
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
            cg.setTeVerwijderenOp(teVerwijderenOp);
            cg.setTeVerwijderenOpAutomatisch(automatisch);
            cg.persist();
            id.set(cg.id);
        });
        return id.get();
    }

    private UUID persistVoorkeur(Instant lastUsedAt, Instant teVerwijderenOp, boolean automatisch) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(partij);
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setLastUsedAt(lastUsedAt);
            voorkeur.setTeVerwijderenOp(teVerwijderenOp);
            voorkeur.setTeVerwijderenOpAutomatisch(automatisch);
            voorkeur.persist();
            id.set(voorkeur.id);
        });
        return id.get();
    }
}
