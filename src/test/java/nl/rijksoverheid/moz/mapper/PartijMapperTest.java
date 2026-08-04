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

/**
 * De mapper is niet alleen een vertaallaag: bij het uitlezen van een stale rij schrijft hij
 * terug naar de database (lastUsedAt bijwerken en, als teVerwijderenOpAutomatisch aanstaat,
 * de automatisch gezette bewaartermijn opruimen). Dat neveneffect raakt bewaartermijnen en
 * werd nog nergens afgedekt.
 */
@QuarkusTest
class PartijMapperTest {

    private static final Duration OUDER_DAN_DREMPEL = Duration.ofHours(25);
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

        Assertions.assertEquals(cg.id, response.id);
        Assertions.assertEquals(ContactType.Email, response.type);
        Assertions.assertEquals("test@example.com", response.waarde);
        Assertions.assertTrue(response.isGeverifieerd);
        Assertions.assertTrue(response.isDefault);
        Assertions.assertEquals(cg.getTeVerwijderenOp(), response.teVerwijderenOp);
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
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();
        partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
        partij.setContactgegevens(List.of(contactgegeven(ContactType.Email, "a@example.com")));
        partij.setVoorkeuren(List.of(voorkeur(VoorkeurType.WebsiteTaal, "nl")));

        PartijResponse response = partijMapper.toResponse(partij);

        Assertions.assertEquals(partij.id, response.partijId);
        Assertions.assertEquals(1, response.identificaties.size());
        Assertions.assertEquals(IdentificatieType.BSN, response.identificaties.get(0).identificatieType);
        Assertions.assertEquals("123456789", response.identificaties.get(0).identificatieNummer);
        Assertions.assertEquals(1, response.contactgegevens.size());
        Assertions.assertEquals("a@example.com", response.contactgegevens.get(0).waarde);
        Assertions.assertEquals(1, response.voorkeuren.size());
        Assertions.assertEquals("nl", response.voorkeuren.get(0).waarde);
    }

    @Test
    void toResponse_legeCollecties_gevenLegeLijsten() {
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();

        PartijResponse response = partijMapper.toResponse(partij);

        Assertions.assertTrue(response.identificaties.isEmpty());
        Assertions.assertTrue(response.contactgegevens.isEmpty());
        Assertions.assertTrue(response.voorkeuren.isEmpty());
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

    private Partij nieuwePartij() {
        Partij partij = new Partij();
        partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
        partij.persist();
        return partij;
    }

    private UUID contactgegevenMetBewaartermijn(Instant lastUsedAt, Instant teVerwijderenOp, boolean automatisch) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(nieuwePartij());
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

    @Test
    void staleContactgegevenMetAutomatischeBewaartermijn_WordtOpgeruimd() {
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(30));
        UUID id = contactgegevenMetBewaartermijn(
                Instant.now().minus(OUDER_DAN_DREMPEL), teVerwijderenOp, true);

        AtomicReference<ContactgegevenResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toContactgegevensResponse(Contactgegeven.findById(id))));

        // Het antwoord toont de opgeruimde stand, niet de stand van vóór de mapping.
        Assertions.assertNull(response.get().teVerwijderenOp,
                "Automatisch gezette bewaartermijn hoort bij hergebruik te vervallen");

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven herladen = Contactgegeven.findById(id);
            Assertions.assertNull(herladen.getTeVerwijderenOp(), "Opruiming moet ook zijn weggeschreven");
            Assertions.assertFalse(herladen.isTeVerwijderenOpAutomatisch(),
                    "De automatisch-vlag moet uit zodat de termijn niet opnieuw wordt opgeruimd");
            Assertions.assertTrue(herladen.getLastUsedAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))),
                    "lastUsedAt moet zijn bijgewerkt naar nu");
        });
    }

    @Test
    void staleContactgegevenMetHandmatigeBewaartermijn_BehoudtDeTermijn() {
        // Een door een dienstverlener gezette termijn is een bewuste keuze en mag niet
        // sneuvelen doordat de partij toevallig wordt opgehaald.
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(30));
        UUID id = contactgegevenMetBewaartermijn(
                Instant.now().minus(OUDER_DAN_DREMPEL), teVerwijderenOp, false);

        AtomicReference<ContactgegevenResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toContactgegevensResponse(Contactgegeven.findById(id))));

        Assertions.assertNotNull(response.get().teVerwijderenOp);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven herladen = Contactgegeven.findById(id);
            Assertions.assertNotNull(herladen.getTeVerwijderenOp());
            Assertions.assertTrue(herladen.getLastUsedAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))));
        });
    }

    @Test
    void versContactgegeven_WordtNietAangeraakt() {
        // Binnen de drempel van 24 uur mag een GET geen schrijfactie veroorzaken.
        Instant lastUsedAt = Instant.now().minus(Duration.ofHours(1));
        UUID id = contactgegevenMetBewaartermijn(lastUsedAt, Instant.now().plus(Duration.ofDays(30)), true);

        AtomicReference<ContactgegevenResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toContactgegevensResponse(Contactgegeven.findById(id))));

        Assertions.assertNotNull(response.get().teVerwijderenOp,
                "Binnen de drempel blijft de bewaartermijn staan");

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven herladen = Contactgegeven.findById(id);
            Assertions.assertTrue(herladen.isTeVerwijderenOpAutomatisch());
            Assertions.assertEquals(lastUsedAt.toEpochMilli(), herladen.getLastUsedAt().toEpochMilli(),
                    "lastUsedAt mag niet zijn aangeraakt");
        });
    }

    @Test
    void staleVoorkeurMetAutomatischeBewaartermijn_WordtOpgeruimd() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(nieuwePartij());
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setLastUsedAt(Instant.now().minus(OUDER_DAN_DREMPEL));
            voorkeur.setTeVerwijderenOp(Instant.now().plus(Duration.ofDays(30)));
            voorkeur.setTeVerwijderenOpAutomatisch(true);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        AtomicReference<VoorkeurResponse> response = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() ->
                response.set(partijMapper.toVoorkeurResponse(Voorkeur.findById(voorkeurId.get()))));

        Assertions.assertNull(response.get().teVerwijderenOp);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur herladen = Voorkeur.findById(voorkeurId.get());
            Assertions.assertNull(herladen.getTeVerwijderenOp());
            Assertions.assertFalse(herladen.isTeVerwijderenOpAutomatisch());
        });
    }

    @Test
    void scopesVanVoorkeurEnContactgegeven_WordenVolledigGemapt() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();
            Dienst dienst = new Dienst();
            dienst.setNaam("TestDienst");
            dienst.persist();
            DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
            link.persist();

            Partij partij = nieuwePartij();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Telefoonnummer);
            cg.setWaarde("0612345678");
            cg.addScope(new ScopeContactgegeven(cg, link));
            cg.persist();
            contactId.set(cg.id);

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(partij);
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            ContactgegevenResponse cr = partijMapper.toContactgegevensResponse(Contactgegeven.findById(contactId.get()));
            Assertions.assertEquals(1, cr.scopes.size());
            Assertions.assertEquals("TestDV", cr.scopes.getFirst().dienstverlenerNaam);
            Assertions.assertEquals("TestDienst", cr.scopes.getFirst().dienstNaam);

            VoorkeurResponse vr = partijMapper.toVoorkeurResponse(Voorkeur.findById(voorkeurId.get()));
            Assertions.assertEquals(1, vr.scopes.size());
            Assertions.assertEquals("TestDV", vr.scopes.getFirst().dienstverlenerNaam);
            Assertions.assertEquals("TestDienst", vr.scopes.getFirst().dienstNaam);
        });
    }

    @Test
    void dienstverlenerBredeScope_LevertDienstNaamNull() {
        // Een DienstverlenerDienst zonder dienst betekent "alle diensten van deze dienstverlener".
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();
            DienstverlenerDienst link = new DienstverlenerDienst(dv, null);
            link.persist();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(nieuwePartij());
            cg.setType(ContactType.Telefoonnummer);
            cg.setWaarde("0612345678");
            cg.addScope(new ScopeContactgegeven(cg, link));
            cg.persist();
            contactId.set(cg.id);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            ContactgegevenResponse cr = partijMapper.toContactgegevensResponse(Contactgegeven.findById(contactId.get()));
            Assertions.assertEquals(1, cr.scopes.size());
            Assertions.assertEquals("TestDV", cr.scopes.getFirst().dienstverlenerNaam);
            Assertions.assertNull(cr.scopes.getFirst().dienstNaam);
        });
    }
}
