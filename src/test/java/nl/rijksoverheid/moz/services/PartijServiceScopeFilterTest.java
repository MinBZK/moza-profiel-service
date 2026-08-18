package nl.rijksoverheid.moz.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.ScopeRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.DatabaseCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scope-filtering bepaalt welke gegevens een dienstverlener van een partij te zien krijgt.
 * De filtertakken in findFilteredContactgegevens/findFilteredVoorkeuren werden nooit geraakt:
 * alle bestaande tests halen de partij ongefilterd op. Datzelfde geldt voor de scope-varianten
 * in resolveDienstverlenerDienst. Dit is autorisatiegevoelig gedrag, dus het hoort vast te liggen.
 */
@QuarkusTest
class PartijServiceScopeFilterTest {

    private static final String BSN_NUMMER = "123456789";

    @Inject
    PartijService partijService;

    @Inject
    EntityManager entityManager;

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    @BeforeEach
    void setup() {
        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());
    }

    @AfterEach
    void tearDown() {
        DatabaseCleanup.wipe();
    }

    private DienstverlenerDienst maakLink(String dvNaam, String dienstNaam) {
        Dienstverlener dv = Dienstverlener.find("naam", dvNaam).firstResult();

        if (dv == null) {
            dv = new Dienstverlener();
            dv.setNaam(dvNaam);
            dv.persist();
        }

        Dienst dienst = null;

        if (dienstNaam != null) {
            dienst = Dienst.find("naam", dienstNaam).firstResult();

            if (dienst == null) {
                dienst = new Dienst();
                dienst.setNaam(dienstNaam);
                dienst.persist();
            }
        }

        DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
        link.persist();

        return link;
    }

    /**
     * Partij met drie contactgegevens en drie voorkeuren: één zonder scope, één voor DV-A/Dienst-A
     * en één voor DV-B/Dienst-B.
     */
    private void scenarioMetDrieScopes() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, BSN_NUMMER));
            partij.persist();

            DienstverlenerDienst linkA = maakLink("DV-A", "Dienst-A");
            DienstverlenerDienst linkB = maakLink("DV-B", "Dienst-B");

            maakContact(partij, "0600000000", null);
            maakContact(partij, "0611111111", linkA);
            maakContact(partij, "0622222222", linkB);

            maakVoorkeur(partij, VoorkeurType.WebsiteTaal, "nl", null);
            maakVoorkeur(partij, VoorkeurType.WebsiteThema, "licht", linkA);
            maakVoorkeur(partij, VoorkeurType.Aanhef, "formeel", linkB);
        });
    }

    private void maakContact(Partij partij, String waarde, DienstverlenerDienst link) {
        Contactgegeven cg = new Contactgegeven();
        cg.setPartij(partij);
        cg.setType(ContactType.Telefoonnummer);
        cg.setWaarde(waarde);

        if (link != null) {
            cg.addScope(new ScopeContactgegeven(cg, link));
        }

        cg.persist();
    }

    private void maakVoorkeur(Partij partij, VoorkeurType type, String waarde, DienstverlenerDienst link) {
        Voorkeur v = new Voorkeur();
        v.setPartij(partij);
        v.setVoorkeurType(type);
        v.setWaarde(waarde);

        if (link != null) {
            v.addScope(new ScopeVoorkeur(v, link));
        }

        v.persist();
    }

    private PartijRequest partijRequest(String dienstverlener, String dienstNaam) {
        PartijRequest request = new PartijRequest();
        request.setIdentificatieType(IdentificatieType.BSN);
        request.setIdentificatieNummer(BSN_NUMMER);
        request.setDienstverlener(dienstverlener);
        request.setDienstNaam(dienstNaam);

        return request;
    }

    private List<String> contactWaardes(PartijResponse response) {
        return response.getContactgegevens().stream().map(c -> c.getWaarde()).sorted().toList();
    }

    @Test
    void filterOpDienstverlener_GeeftEigenScopeEnOngescopteRijen() {
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", null));

        // De ongescopte rij is de standaard voor alle dienstverleners en hoort er dus bij;
        // de rij van DV-B mag DV-A nooit te zien krijgen.
        Assertions.assertEquals(List.of("0600000000", "0611111111"), contactWaardes(response));
        Assertions.assertEquals(2, response.getVoorkeuren().size());
        Assertions.assertTrue(response.getVoorkeuren().stream().noneMatch(v -> v.getVoorkeurType() == VoorkeurType.Aanhef),
                "Voorkeur van DV-B mag niet lekken naar DV-A");
    }

    @Test
    void filterOpDienstverlener_IsHoofdletterongevoelig() {
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("dv-a", null));

        Assertions.assertEquals(List.of("0600000000", "0611111111"), contactWaardes(response));
    }

    @Test
    void filterOpOnbekendeDienstverlener_GeeftAlleenOngescopteRijen() {
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-Onbekend", null));

        Assertions.assertEquals(List.of("0600000000"), contactWaardes(response));
        Assertions.assertEquals(1, response.getVoorkeuren().size());
    }

    @Test
    void filterOpDienstverlenerEnDienst_CombineertBeideVoorwaarden() {
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", "Dienst-A"));

        Assertions.assertEquals(List.of("0600000000", "0611111111"), contactWaardes(response));
    }

    @Test
    void filterOpAndereDienstVanZelfdeDienstverlener_LaatDieScopeWeg() {
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", "Dienst-B"));

        // De scope van DV-A hangt aan Dienst-A; met dienstNaam=Dienst-B valt die af.
        Assertions.assertEquals(List.of("0600000000"), contactWaardes(response));
    }

    @Test
    void rijMetTweeMatchendeScopes_KomtSlechtsEenmaalTerug() {
        // Beide scopes van deze rij matchen het filter, dus de join levert twee resultaatrijen op.
        // De query draait zonder 'distinct'; Hibernate ontdubbelt entity-resultaten zelf. Deze
        // test legt dat gedrag vast: zou een Hibernate-upgrade het loslaten, dan staan het
        // contactgegeven en de voorkeur ineens dubbel in de response en valt deze test om.
        AtomicReference<UUID> partijId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, BSN_NUMMER));
            partij.persist();
            partijId.set(partij.id);

            DienstverlenerDienst linkA = maakLink("DV-A", "Dienst-A");
            DienstverlenerDienst linkB = maakLink("DV-A", "Dienst-B");

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Telefoonnummer);
            cg.setWaarde("0644444444");
            cg.addScope(new ScopeContactgegeven(cg, linkA));
            cg.addScope(new ScopeContactgegeven(cg, linkB));
            cg.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(partij);
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, linkA));
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, linkB));
            voorkeur.persist();
        });

        // Zonder deze controle zou de test stilletjes niets meer aantonen zodra de opzet
        // verandert en er nog maar één scope matcht: de join moet echt uitwaaieren.
        QuarkusTransaction.requiringNew().run(() -> {
            long joinRijen = entityManager.createQuery(
                            "select count(c.id) from Contactgegeven c "
                                    + "left join c.scopes s "
                                    + "left join s.dienstverlenerDienst dd "
                                    + "left join dd.dienstverlener dv "
                                    + "where c.partij.id = :partijId "
                                    + "AND (s IS NULL OR lower(dv.naam) = lower(:dvNaam))", Long.class)
                    .setParameter("partijId", partijId.get())
                    .setParameter("dvNaam", "DV-A")
                    .getSingleResult();

            Assertions.assertEquals(2L, joinRijen,
                    "De join hoort twee rijen op te leveren, anders toont deze test niets aan");
        });

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", null));

        Assertions.assertEquals(List.of("0644444444"), contactWaardes(response));
        Assertions.assertEquals(1, response.getVoorkeuren().size());
        Assertions.assertEquals(2, response.getContactgegevens().getFirst().getScopes().size(),
                "Beide scopes horen wel in de response te staan, alleen de rij zelf niet dubbel");
    }

    @Test
    void rijMetDienstBredeEnDienstSpecifiekeScope_KomtSlechtsEenmaalTerug() {
        // Tweede ontdubbel-scenario, nu via het dienstNaam-filter: een DV-brede scope
        // (dienst IS NULL) en een scope op Dienst-A matchen allebei, dus ook hier levert
        // de join twee rijen op voor hetzelfde contactgegeven.
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, BSN_NUMMER));
            partij.persist();

            DienstverlenerDienst breed = maakLink("DV-A", null);
            DienstverlenerDienst specifiek = maakLink("DV-A", "Dienst-A");

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Telefoonnummer);
            cg.setWaarde("0655555555");
            cg.addScope(new ScopeContactgegeven(cg, breed));
            cg.addScope(new ScopeContactgegeven(cg, specifiek));
            cg.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setPartij(partij);
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, breed));
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, specifiek));
            voorkeur.persist();
        });

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", "Dienst-A"));

        Assertions.assertEquals(List.of("0655555555"), contactWaardes(response));
        Assertions.assertEquals(1, response.getVoorkeuren().size());
    }

    @Test
    void ongescopteRijNaastGescopteRij_LevertGeenDubbeleTreffers() {
        // De left join maakt voor een rij zonder scopes één rij met s IS NULL. Samen met een
        // tweede, wél gescopte rij dekt dit het pad waarop beide OR-takken van het filter
        // tegelijk raak zijn.
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, BSN_NUMMER));
            partij.persist();

            DienstverlenerDienst link = maakLink("DV-A", "Dienst-A");
            maakContact(partij, "0600000000", null);
            maakContact(partij, "0611111111", link);
        });

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", "Dienst-A"));

        Assertions.assertEquals(List.of("0600000000", "0611111111"), contactWaardes(response));
    }

    @Test
    void leegFilter_GeeftAllesZonderFilterquery() {
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest(null, null));

        Assertions.assertEquals(List.of("0600000000", "0611111111", "0622222222"), contactWaardes(response));
        Assertions.assertEquals(3, response.getVoorkeuren().size());
    }

    @Test
    void alleenDienstNaamZonderDienstverlener_FiltertOpDienst() {
        // getPartijResponse mag de ongefilterde tak niet kiezen als alléén dienstNaam is gezet.
        scenarioMetDrieScopes();

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest(null, "Dienst-A"));

        Assertions.assertEquals(List.of("0600000000", "0611111111"), contactWaardes(response));
    }

    @Test
    void dienstverlenerBredeScope_ValtBinnenElkeDienstFilter() {
        // Een DienstverlenerDienst zonder dienst betekent "alle diensten van deze dienstverlener"
        // en moet daarom ook opduiken als er op een specifieke dienstnaam wordt gefilterd.
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, BSN_NUMMER));
            partij.persist();
            maakContact(partij, "0633333333", maakLink("DV-A", null));
        });

        PartijResponse response = partijService.getPartijResponse(
                IdentificatieType.BSN, BSN_NUMMER, partijRequest("DV-A", "WillekeurigeDienst"));

        Assertions.assertEquals(List.of("0633333333"), contactWaardes(response));
    }

    @Test
    void scopeZonderDienstverlenerNaam_LevertGeenScope() {
        // Een leeg scope-object is geen fout, maar levert een ongescopte rij op.
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setIdentificatieType(IdentificatieType.BSN);
        request.setIdentificatieNummer(BSN_NUMMER);
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");
        request.setScope(new ScopeRequest());

        var result = partijService.addContactgegeven(IdentificatieType.BSN, BSN_NUMMER, request);

        UUID id = result.contactgegeven().id;
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertTrue(Contactgegeven.<Contactgegeven>findById(id).getScopes().isEmpty()));
    }

    @Test
    void scopeMetDienstNaamZonderDienstverlenerNaam_IsOngeldig() {
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setIdentificatieType(IdentificatieType.BSN);
        request.setIdentificatieNummer(BSN_NUMMER);
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");
        request.setScope(new ScopeRequest());
        request.getScope().setDienstNaam("Dienst-A");

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, BSN_NUMMER, request));
        Assertions.assertEquals(BusinessException.Kind.BAD_REQUEST, ex.getKind());
    }

    @Test
    void scopeZonderDienstNaam_MaaktDienstverlenerBredeKoppeling() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("DV-A");
            dv.persist();
        });

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setIdentificatieType(IdentificatieType.BSN);
        request.setIdentificatieNummer(BSN_NUMMER);
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");
        request.setScope(new ScopeRequest());
        request.getScope().setDienstverlenerNaam("DV-A");

        var result = partijService.addContactgegeven(IdentificatieType.BSN, BSN_NUMMER, request);

        UUID id = result.contactgegeven().id;
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven cg = Contactgegeven.findById(id);
            Assertions.assertEquals(1, cg.getScopes().size());
            DienstverlenerDienst link = cg.getScopes().getFirst().getDienstverlenerDienst();
            Assertions.assertEquals("DV-A", link.getDienstverlener().getNaam());
            Assertions.assertNull(link.getDienst(), "Zonder dienstNaam hoort de koppeling dienstverlener-breed te zijn");
        });
    }

    @Test
    void scopeMetOnbekendeDienstverlener_IsNotFound() {
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setIdentificatieType(IdentificatieType.BSN);
        request.setIdentificatieNummer(BSN_NUMMER);
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");
        request.setScope(new ScopeRequest());
        request.getScope().setDienstverlenerNaam("BestaatNiet");

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, BSN_NUMMER, request));
        Assertions.assertEquals(BusinessException.Kind.NOT_FOUND, ex.getKind());
    }

    @Test
    void gescopteVoorkeurenVanTweeDienstverleners_BlijvenAparteRijen() {
        // De upsert-invariant is (partij, voorkeurType, scope). Twee dienstverleners met
        // dezelfde voorkeurType mogen elkaars waarde dus niet overschrijven.
        QuarkusTransaction.requiringNew().run(() -> {
            maakLink("DV-A", "Dienst-A");
            maakLink("DV-B", "Dienst-B");
        });

        partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER,
                voorkeurRequest(VoorkeurType.WebsiteTaal, "nl", "DV-A", "Dienst-A"));
        partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER,
                voorkeurRequest(VoorkeurType.WebsiteTaal, "en", "DV-B", "Dienst-B"));

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(2, Voorkeur.count(), "Elke scope houdt een eigen rij"));

        // Zelfde scope opnieuw: upsert, geen derde rij.
        var upsert = partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER,
                voorkeurRequest(VoorkeurType.WebsiteTaal, "fy", "DV-A", "Dienst-A"));

        Assertions.assertFalse(upsert.wasCreated());
        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(2, Voorkeur.count());
            Assertions.assertEquals("fy", Voorkeur.<Voorkeur>findById(upsert.voorkeur().id).getWaarde());
        });
    }

    @Test
    void updateVoorkeurNaarBezetteScope_GeeftConflict() {
        QuarkusTransaction.requiringNew().run(() -> {
            maakLink("DV-A", "Dienst-A");
            maakLink("DV-B", "Dienst-B");
        });

        var eerste = partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER,
                voorkeurRequest(VoorkeurType.WebsiteTaal, "nl", "DV-A", "Dienst-A"));
        partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER,
                voorkeurRequest(VoorkeurType.WebsiteTaal, "en", "DV-B", "Dienst-B"));

        // De eerste voorkeur naar de scope van de tweede duwen botst op de invariant.
        VoorkeurUpdateRequest update = new VoorkeurUpdateRequest();
        update.setId(eerste.voorkeur().id);
        update.setIdentificatieType(IdentificatieType.BSN);
        update.setIdentificatieNummer(BSN_NUMMER);
        update.setVoorkeurType(VoorkeurType.WebsiteTaal);
        update.setWaarde("de");
        update.setScope(new ScopeRequest());
        update.getScope().setDienstverlenerNaam("DV-B");
        update.getScope().setDienstNaam("Dienst-B");

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> partijService.updateVoorkeur(IdentificatieType.BSN, BSN_NUMMER, update));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());
    }

    private VoorkeurRequest voorkeurRequest(VoorkeurType type, String waarde, String dvNaam, String dienstNaam) {
        VoorkeurRequest request = new VoorkeurRequest();
        request.setIdentificatieType(IdentificatieType.BSN);
        request.setIdentificatieNummer(BSN_NUMMER);
        request.setVoorkeurType(type);
        request.setWaarde(waarde);
        request.setScope(new ScopeRequest());
        request.getScope().setDienstverlenerNaam(dvNaam);
        request.getScope().setDienstNaam(dienstNaam);

        return request;
    }

    @Test
    void ongescopteEnGescopteVoorkeurVanZelfdeType_BestaanNaastElkaar() {
        QuarkusTransaction.requiringNew().run(() -> maakLink("DV-A", "Dienst-A"));

        VoorkeurRequest zonderScope = new VoorkeurRequest();
        zonderScope.setIdentificatieType(IdentificatieType.BSN);
        zonderScope.setIdentificatieNummer(BSN_NUMMER);
        zonderScope.setVoorkeurType(VoorkeurType.WebsiteTaal);
        zonderScope.setWaarde("nl");

        partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER, zonderScope);
        partijService.addVoorkeur(IdentificatieType.BSN, BSN_NUMMER,
                voorkeurRequest(VoorkeurType.WebsiteTaal, "en", "DV-A", "Dienst-A"));

        AtomicReference<Long> aantal = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> aantal.set(Voorkeur.count()));
        Assertions.assertEquals(2L, aantal.get(),
                "De ongescopte rij en de gescopte rij zijn verschillende sleutels");
    }
}
