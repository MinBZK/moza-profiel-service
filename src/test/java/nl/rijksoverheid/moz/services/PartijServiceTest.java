package nl.rijksoverheid.moz.services;

import nl.rijksoverheid.moz.exception.BusinessException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionalException;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.api.generated.model.ScopeRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
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
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@QuarkusTest
public class PartijServiceTest {

    @Inject
    PartijService partijService;

    @Inject
    DienstverlenerService dienstverlenerService;

    @InjectMock
    EmailVerificatieService emailVerificatieService;

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

    private String createTestDienstverlenerWithDienst() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();
            Dienst d = new Dienst();
            d.setNaam("TestDienst");
            d.persist();
            DienstverlenerDienst link = new DienstverlenerDienst(dv, d);
            link.persist();
        });
        return "TestDV";
    }

    private void setupPartijWithScopedContact() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();

            Dienst dienst = new Dienst();
            dienst.setNaam("TestDienst");
            dienst.persist();

            DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
            link.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.persist();

            ScopeContactgegeven scope = new ScopeContactgegeven(contact, link);
            scope.persist();
            contact.addScope(scope);
        });
    }

    @Test
    void getPartij_Found() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        Partij result = partijService.getPartij(IdentificatieType.BSN, "123456789");
        Assertions.assertNotNull(result);
    }

    @Test
    void getPartij_NotFound() {
        Partij result = partijService.getPartij(IdentificatieType.BSN, "999999999");
        Assertions.assertNull(result);
    }

    @Test
    void addContactgegeven_ExistingPartij() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Email);
        request.setWaarde("test@test.com");

        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertNotNull(partij);
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Assertions.assertEquals("test@test.com", partij.getContactgegevens().get(0).getWaarde());
            Assertions.assertEquals("test-ref-id", partij.getContactgegevens().get(0).getVerificatieReferentieId());
        });
    }

    @Test
    void addContactgegeven_NewPartij() {
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");

        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertNotNull(partij);
            Assertions.assertEquals(1, partij.getContactgegevens().size());
        });
    }

    @Test
    void addContactgegeven_EmailType_CallsVerificationService() {
        Mockito.doReturn("test-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Email);
        request.setWaarde("test@test.com");

        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        Mockito.verify(emailVerificatieService).requestEmailVerificationCode("test@test.com");
    }

    @Test
    void addVoorkeur_ExistingPartij() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        VoorkeurRequest request = new VoorkeurRequest();
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("nl");

        partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertNotNull(partij);
            Assertions.assertEquals(1, partij.getVoorkeuren().size());
            Assertions.assertEquals("nl", partij.getVoorkeuren().get(0).getWaarde());
        });
    }

    @Test
    void updateContactgegeven_Success() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("old@test.com");
            contact.setPartij(partij);
            contact.persist();
            contactId.set(contact.id);
        });

        Mockito.doReturn("new-ref-id").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(contactId.get());
        request.setType(ContactType.Email);
        request.setWaarde("new@test.com");

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId.get());
            Assertions.assertEquals("new@test.com", contact.getWaarde());
            Assertions.assertEquals("new-ref-id", contact.getVerificatieReferentieId());
        });
    }

    @Test
    void updateContactgegeven_PartijNotFound() {
        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setType(ContactType.Email);
        request.setWaarde("test@test.com");

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "999999999", request);

        Assertions.assertFalse(result);
    }

    @Test
    void updateVoorkeur_Success() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.setId(voorkeurId.get());
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("en");

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertEquals("en", voorkeur.getWaarde());
        });
    }

    @Test
    void getPartijResponse_Found() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        PartijRequest request = new PartijRequest();
        PartijResponse result = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
    }

    @Test
    void getPartijResponse_ContactgegevenIsStale_WerktLastUsedAtBij() {
        AtomicReference<UUID> cgId = new AtomicReference<>();
        Instant ouder = Instant.now().minus(Duration.ofHours(48));
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Email);
            cg.setWaarde("touch@example.com");
            cg.setLastUsedAt(ouder);
            cg.persist();
            cgId.set(cg.id);
        });

        Instant voorLezen = Instant.now();
        partijService.getPartijResponse(IdentificatieType.BSN, "123456789", new PartijRequest());

        QuarkusTransaction.requiringNew().run(() -> {
            Instant lastUsedAt = Contactgegeven.<Contactgegeven>findById(cgId.get()).getLastUsedAt();
            Assertions.assertNotNull(lastUsedAt);
            Assertions.assertFalse(lastUsedAt.isBefore(voorLezen), "lastUsedAt moet het moment van lezen weerspiegelen");
        });
    }

    @Test
    void getPartijResponse_ContactgegevenNietStale_LaatLastUsedAtOngemoeid() {
        AtomicReference<UUID> cgId = new AtomicReference<>();
        Instant recent = Instant.now().truncatedTo(ChronoUnit.MICROS);
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Email);
            cg.setWaarde("touch@example.com");
            cg.setLastUsedAt(recent);
            cg.persist();
            cgId.set(cg.id);
        });

        partijService.getPartijResponse(IdentificatieType.BSN, "123456789", new PartijRequest());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(recent, Contactgegeven.<Contactgegeven>findById(cgId.get()).getLastUsedAt()));
    }

    /**
     * touchIfStale rechtstreeks aangeroepen (package-private) i.p.v. via getPartijResponse: de
     * race die dit dekt — de rij is soft-deleted tussen het ophalen en deze touch — kan niet
     * deterministisch via de publieke servicemethoden opgewekt worden, want die selecteren zelf
     * al op verwijderdOp IS NULL. Hier wordt de rij rechtstreeks (buiten de service om) soft-
     * deleted vóórdat touchIfStale draait, wat exact simuleert wat een concurrente
     * retentiescheduler-run zou doen.
     */
    @Test
    void touchIfStale_RijInmiddelsSoftDeleted_GeeftFalseTerug() {
        AtomicReference<UUID> cgId = new AtomicReference<>();
        Instant ouder = Instant.now().minus(Duration.ofHours(48));
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven cg = new Contactgegeven();
            cg.setPartij(partij);
            cg.setType(ContactType.Email);
            cg.setWaarde("touch@example.com");
            cg.setLastUsedAt(ouder);
            cg.persist();
            cgId.set(cg.id);
        });

        QuarkusTransaction.requiringNew().run(() ->
                Contactgegeven.update("verwijderdOp = ?1 WHERE id = ?2", Instant.now(), cgId.get()));

        AtomicReference<Boolean> nogActief = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven cg = Contactgegeven.findById(cgId.get());
            nogActief.set(partijService.touchIfStale(cg));
        });

        Assertions.assertFalse(nogActief.get());
    }

    @Test
    void addContactgegeven_Duplicate_ThrowsConflict() {
        createTestDienstverlenerWithDienst();

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest scoped = new ContactgegevenRequest();
        scoped.setType(ContactType.Email);
        scoped.setWaarde("match@test.com");
        scoped.setScope(new ScopeRequest());
        scoped.getScope().setDienstverlenerNaam("TestDV");
        scoped.getScope().setDienstNaam("TestDienst");

        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", scoped);

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, "123456789", scoped));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());
        Mockito.verify(emailVerificatieService, Mockito.times(1)).requestEmailVerificationCode(Mockito.anyString());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Assertions.assertEquals(1, partij.getContactgegevens().get(0).getScopes().size());
        });
    }

    @Test
    void addContactgegeven_Duplicate_DifferentScope_ThrowsConflictAndLeavesExistingScopeUnchanged() {
        createTestDienstverlenerWithDienst();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("OtherDV");
            dv.persist();
            Dienst d = new Dienst();
            d.setNaam("OtherDienst");
            d.persist();
            new DienstverlenerDienst(dv, d).persist();
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest first = new ContactgegevenRequest();
        first.setType(ContactType.Email);
        first.setWaarde("differentscope@test.com");
        first.setScope(new ScopeRequest());
        first.getScope().setDienstverlenerNaam("TestDV");
        first.getScope().setDienstNaam("TestDienst");
        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", first);

        // Zelfde (type, waarde), maar een andere scope: Contactgegeven.find() negeert scope volledig,
        // dus dit moet ook een conflict opleveren en de bestaande scope niet aanvullen.
        ContactgegevenRequest duplicateWithOtherScope = new ContactgegevenRequest();
        duplicateWithOtherScope.setType(ContactType.Email);
        duplicateWithOtherScope.setWaarde("differentscope@test.com");
        duplicateWithOtherScope.setScope(new ScopeRequest());
        duplicateWithOtherScope.getScope().setDienstverlenerNaam("OtherDV");
        duplicateWithOtherScope.getScope().setDienstNaam("OtherDienst");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, "123456789", duplicateWithOtherScope));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Contactgegeven cg = partij.getContactgegevens().get(0);
            Assertions.assertEquals(1, cg.getScopes().size(), "de scope van OtherDV mag niet toegevoegd worden bij een conflict");
            Assertions.assertEquals("TestDV", cg.getScopes().get(0).getDienstverlenerDienst().getDienstverlener().getNaam());
        });
    }

    @Test
    void addVoorkeur_Duplicate_ThrowsConflict() {
        VoorkeurRequest request = new VoorkeurRequest();
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("nl");

        partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        VoorkeurRequest duplicate = new VoorkeurRequest();
        duplicate.setVoorkeurType(VoorkeurType.WebsiteTaal);
        duplicate.setWaarde("de");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addVoorkeur(IdentificatieType.BSN, "123456789", duplicate));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getVoorkeuren().size());
            Assertions.assertEquals("nl", partij.getVoorkeuren().get(0).getWaarde());
        });
    }

    @Test
    void updateContactgegeven_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setType(ContactType.Email);
        request.setWaarde("test@test.com");

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(result);
    }

    @Test
    void updateContactgegeven_AlreadyVerwijderd_ReturnsFalse() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.verwijder(Instant.now());
            contact.persist();
            contactId.set(contact.id);
        });

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(contactId.get());
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0687654321");

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(result, "an already soft-deleted contactgegeven must be treated as not found");
    }

    @Test
    void updateVoorkeur_VoorkeurNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("en");

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(result);
    }

    @Test
    void getPartijResponse_WithDienstNaamFilter_PreservesRowsAndDoesNotDelete() {
        // Regression: getPartijFiltered previously called partij.setContactgegevens(filtered) on a
        // managed Partij with orphanRemoval=true, which silently deleted the filtered-out rows.
        // To actually exercise the bug we need a contact the filter EXCLUDES, otherwise the old
        // buggy code would never have orphan-removed anything (filtered.size() == collection.size()).
        // We add a third contact scoped to a different DV so the filter excludes it.
        setupPartijWithScopedContact();

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");

            // unscoped contact: matches via "s IS NULL"
            Contactgegeven unscoped = new Contactgegeven();
            unscoped.setType(ContactType.Telefoonnummer);
            unscoped.setWaarde("0612345678");
            unscoped.setPartij(partij);
            unscoped.persist();

            // contact scoped to a DIFFERENT DV+dienst: must be excluded by the filter.
            Dienstverlener otherDv = new Dienstverlener();
            otherDv.setNaam("OtherDV");
            otherDv.persist();
            Dienst otherDienst = new Dienst();
            otherDienst.setNaam("OtherDienst");
            otherDienst.persist();
            DienstverlenerDienst otherLink = new DienstverlenerDienst(otherDv, otherDienst);
            otherLink.persist();

            Contactgegeven excluded = new Contactgegeven();
            excluded.setType(ContactType.Telefoonnummer);
            excluded.setWaarde("0699999999");
            excluded.setPartij(partij);
            excluded.persist();
            ScopeContactgegeven excludingScope = new ScopeContactgegeven(excluded, otherLink);
            excludingScope.persist();
            excluded.addScope(excludingScope);
        });

        PartijRequest request = new PartijRequest();
        request.setDienstNaam("TestDienst");
        PartijResponse result = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        // Returned: scoped-TestDienst contact + unscoped phone. Excluded: phone scoped to OtherDienst.
        Assertions.assertEquals(2, result.getContactgegevens().size());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(3, partij.getContactgegevens().size(),
                    "Filtered read must not delete contactgegevens that were excluded by the filter");
        });
    }

    @Test
    void resolveDienstverlenerDienst_DienstNaamWithoutDienstverlenerNaam_ThrowsBadRequest() {
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Email);
        request.setWaarde("test@test.com");
        request.setScope(new ScopeRequest());
        request.getScope().setDienstNaam("TestDienst");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request));
        Assertions.assertEquals(BusinessException.Kind.BAD_REQUEST, ex.getKind());
    }

    @Test
    void resolveDienstverlenerDienst_DienstNotLinkedToRequestedDV_Throws404() {
        // Seed two DVs each with their own Dienst. Request DV-A with DV-B's Dienst name.
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dvA = new Dienstverlener();
            dvA.setNaam("DV-A");
            dvA.persist();
            Dienst dienstA = new Dienst();
            dienstA.setNaam("A-Vergunning");
            dienstA.persist();
            new DienstverlenerDienst(dvA, dienstA).persist();

            Dienstverlener dvB = new Dienstverlener();
            dvB.setNaam("DV-B");
            dvB.persist();
            Dienst dienstB = new Dienst();
            dienstB.setNaam("B-Vergunning");
            dienstB.persist();
            new DienstverlenerDienst(dvB, dienstB).persist();
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Email);
        request.setWaarde("cross@test.com");
        request.setScope(new ScopeRequest());
        request.getScope().setDienstverlenerNaam("DV-A");
        request.getScope().setDienstNaam("B-Vergunning");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request));
        Assertions.assertEquals(BusinessException.Kind.NOT_FOUND, ex.getKind());
    }

    @Test
    void updateContactgegeven_isDefaultTrue_demotesPreviousDefault() {
        AtomicReference<UUID> firstId = new AtomicReference<>();
        AtomicReference<UUID> secondId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven first = new Contactgegeven();
            first.setType(ContactType.Email);
            first.setWaarde("a@test.com");
            first.setIsDefault(true);
            first.setPartij(partij);
            first.persist();
            firstId.set(first.id);

            Contactgegeven second = new Contactgegeven();
            second.setType(ContactType.Email);
            second.setWaarde("b@test.com");
            second.setPartij(partij);
            second.persist();
            secondId.set(second.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(secondId.get());
        request.setType(ContactType.Email);
        request.setWaarde("b@test.com");
        request.setIsDefault(true);

        Assertions.assertTrue(partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request));

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertFalse(((Contactgegeven) Contactgegeven.findById(firstId.get())).isIsDefault(),
                    "previous default must be demoted");
            Assertions.assertTrue(((Contactgegeven) Contactgegeven.findById(secondId.get())).isIsDefault(),
                    "new default must be set");
        });
    }

    @Test
    void updateContactgegeven_isDefaultTrue_LaatDefaultMetSoftDeleteOngemoeid() {
        AtomicReference<UUID> verwijderdId = new AtomicReference<>();
        AtomicReference<UUID> secondId = new AtomicReference<>();
        AtomicReference<Instant> verwijderdLastUpdated = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven verwijderd = new Contactgegeven();
            verwijderd.setType(ContactType.Email);
            verwijderd.setWaarde("a@test.com");
            verwijderd.setIsDefault(true);
            verwijderd.verwijder(Instant.now());
            verwijderd.setPartij(partij);
            verwijderd.persist();
            verwijderdId.set(verwijderd.id);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            // Pas na een verse read teruglezen: de DB-kolom rondt tijdstempels af op een grovere
            // eenheid dan Java's in-memory Instant (zie soortgelijke toelichting elders in dit bestand).
            verwijderdLastUpdated.set(Contactgegeven.<Contactgegeven>findById(verwijderdId.get()).getLastUpdated());

            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven second = new Contactgegeven();
            second.setType(ContactType.Email);
            second.setWaarde("b@test.com");
            second.setPartij(partij);
            second.persist();
            secondId.set(second.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(secondId.get());
        request.setType(ContactType.Email);
        request.setWaarde("b@test.com");
        request.setIsDefault(true);

        Assertions.assertTrue(partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request));

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven verwijderd = Contactgegeven.findById(verwijderdId.get());
            Assertions.assertTrue(verwijderd.isIsDefault(),
                    "een rij met een soft delete zit al buiten de partiële index en mag niet gedemote worden");
            Assertions.assertEquals(verwijderdLastUpdated.get(), verwijderd.getLastUpdated(),
                    "de rij met een soft delete mag door de demote-update niet aangeraakt worden");
        });
    }

    @Test
    void updateContactgegeven_isDefaultNull_leavesDefaultUnchanged() {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("a@test.com");
            c.setIsDefault(true);
            c.setPartij(partij);
            c.persist();
            id.set(c.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(id.get());
        request.setType(ContactType.Email);
        request.setWaarde("a@test.com");
        // isDefault explicitly omitted -> null -> no change

        partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertTrue(((Contactgegeven) Contactgegeven.findById(id.get())).isIsDefault());
        });
    }

    @Test
    void updateContactgegeven_isDefaultFalse_unsetsDefault() {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("a@test.com");
            c.setIsDefault(true);
            c.setPartij(partij);
            c.persist();
            id.set(c.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(id.get());
        request.setType(ContactType.Email);
        request.setWaarde("a@test.com");
        request.setIsDefault(false);

        partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertFalse(((Contactgegeven) Contactgegeven.findById(id.get())).isIsDefault());
        });
    }

    @Test
    void updateContactgegeven_OnlyIsDefaultFlipped_DoesNotReVerifyEmail() {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("user@test.com");
            c.setIsGeverifieerd(true);
            c.setGeverifieerdAt(java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
            c.setPartij(partij);
            c.persist();
            id.set(c.id);
        });

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(id.get());
        request.setType(ContactType.Email);
        request.setWaarde("user@test.com");
        request.setIsDefault(true);

        partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);

        Mockito.verify(emailVerificatieService, Mockito.never())
                .requestEmailVerificationCode(Mockito.anyString());

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven c = Contactgegeven.findById(id.get());
            Assertions.assertTrue(c.isIsGeverifieerd(),
                    "isDefault-only update mag verificatiestatus niet resetten");
            Assertions.assertNotNull(c.getGeverifieerdAt());
        });
    }

    @Test
    void updateContactgegeven_DuplicateWaardeForPartij_Throws409() {
        AtomicReference<UUID> targetId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven a = new Contactgegeven();
            a.setType(ContactType.Email);
            a.setWaarde("a@test.com");
            a.setPartij(partij);
            a.persist();

            Contactgegeven b = new Contactgegeven();
            b.setType(ContactType.Email);
            b.setWaarde("b@test.com");
            b.setPartij(partij);
            b.persist();
            targetId.set(b.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(targetId.get());
        request.setType(ContactType.Email);
        request.setWaarde("a@test.com");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());
    }

    @Test
    void updateContactgegeven_DuplicateIsSoftDeleted_DoesNotThrowConflict() {
        // uk_contactgegeven_dedup is partieel (WHERE verwijderd_op IS NULL): een botsing met een
        // rij met een soft delete is geen conflict meer, noch op applicatie- noch op DB-niveau.
        AtomicReference<UUID> targetId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven verwijderd = new Contactgegeven();
            verwijderd.setType(ContactType.Email);
            verwijderd.setWaarde("a@test.com");
            verwijderd.setPartij(partij);
            verwijderd.verwijder(Instant.now());
            verwijderd.persist();

            Contactgegeven target = new Contactgegeven();
            target.setType(ContactType.Email);
            target.setWaarde("b@test.com");
            target.setPartij(partij);
            target.persist();
            targetId.set(target.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(targetId.get());
        request.setType(ContactType.Email);
        request.setWaarde("a@test.com");

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertTrue(result);
    }

    @Test
    void addContactgegeven_EmailIsNormalisedToLowercase() {
        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest first = new ContactgegevenRequest();
        first.setType(ContactType.Email);
        first.setWaarde("User@Test.COM");
        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", first);

        // Een tweede POST met afwijkende hoofdletters moet als duplicaat worden afgewezen.
        ContactgegevenRequest second = new ContactgegevenRequest();
        second.setType(ContactType.Email);
        second.setWaarde("USER@TEST.COM");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addContactgegeven(IdentificatieType.BSN, "123456789", second));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Assertions.assertEquals("user@test.com", partij.getContactgegevens().get(0).getWaarde());
        });
    }

    @Test
    void verwijderVoorkeur_SetsVerwijderdOp() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        // Kleine marge (i.p.v. exact "voor"): de DB-kolom rondt tijdstempels af op een grovere
        // eenheid dan Java's Instant, dus een in-memory "voor" kan net na afronding groter lijken
        // dan de teruggelezen waarde. Nog altijd 100x strenger dan de oude plusSeconds(5)-marge.
        Instant voor = Instant.now().minusMillis(50);
        Voorkeur result = partijService.verwijderVoorkeur(IdentificatieType.BSN, "123456789", voorkeurId.get());

        Assertions.assertNotNull(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertNotNull(voorkeur.getVerwijderdOp());
            Assertions.assertFalse(voorkeur.getVerwijderdOp().isBefore(voor));
        });
    }

    @Test
    void verwijderVoorkeur_VoorkeurNotFound_ReturnsNull() {
        Voorkeur result = partijService.verwijderVoorkeur(IdentificatieType.BSN, "123456789", UUID.randomUUID());
        Assertions.assertNull(result);
    }

    // Bewijst dat Voorkeur.find(partij, id) — de partij-scoped, soft-delete-veilige lookup die
    // verwijderVoorkeur gebruikt — ook echt eigenaarschap afdwingt, niet alleen soft-delete
    // filtert. Zou dit ooit naar een ongescopte findById(id) verschuiven, dan blijft elke andere
    // bestaande test groen; alleen dit scenario merkt het.
    @Test
    void verwijderVoorkeur_IdBehoortTotAnderePartij_ReturnsNull() {
        AtomicReference<UUID> voorkeurIdVanA = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partijA = new Partij();
            partijA.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111140"));
            partijA.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partijA);
            voorkeur.persist();
            voorkeurIdVanA.set(voorkeur.id);

            Partij partijB = new Partij();
            partijB.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111141"));
            partijB.persist();
        });

        Voorkeur result = partijService.verwijderVoorkeur(IdentificatieType.BSN, "111111141", voorkeurIdVanA.get());

        Assertions.assertNull(result, "een voorkeur-id van een andere partij mag niet gevonden worden");
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Voorkeur.<Voorkeur>findById(voorkeurIdVanA.get()).getVerwijderdOp(),
                        "de voorkeur van partij A mag ongemoeid blijven"));
    }

    @Test
    void verwijderVoorkeur_AlreadyVerwijderd_ReturnsNull() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        AtomicReference<Instant> origineelVerwijderdOp = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.verwijder(Instant.now().truncatedTo(ChronoUnit.MICROS));
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
            origineelVerwijderdOp.set(voorkeur.getVerwijderdOp());
        });

        Voorkeur result = partijService.verwijderVoorkeur(IdentificatieType.BSN, "123456789", voorkeurId.get());

        Assertions.assertNull(result, "een al-verwijderde voorkeur wordt behandeld als niet gevonden");
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(origineelVerwijderdOp.get(),
                        Voorkeur.<Voorkeur>findById(voorkeurId.get()).getVerwijderdOp(),
                        "een al gezette verwijderdOp mag niet worden overschreven"));
    }

    @Test
    void deleteLegePartij_ZonderTransactie_ThrowsTransactionalException() {
        // MANDATORY vereist een lopende transactie. De test roept deleteLegePartij rechtstreeks
        // aan zonder @Transactional en zonder QuarkusTransaction-wrapper, dus de interceptor moet
        // de aanroep al blokkeren vóórdat de methode-body (en dus de entity zelf) ertoe doet.
        Partij partij = new Partij();
        partij.id = UUID.randomUUID();

        Assertions.assertThrows(TransactionalException.class,
                () -> partijService.deleteLegePartij(partij, Instant.now()));
    }

    @Test
    void verwijderVoorkeur_LaatsteActieveKind_VerwijdertOokPartij() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        partijService.verwijderVoorkeur(IdentificatieType.BSN, "123456789", voorkeurId.get());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId.get());
            Assertions.assertNotNull(partij.getVerwijderdOp(), "partij zonder actieve children meer moet ook soft-deleted worden");
            Assertions.assertTrue(partij.getIdentificaties().stream().allMatch(i -> i.getVerwijderdOp() != null),
                    "identificaties van een gecascadete partij moeten ook mee-cascaden (uk_identificatie is partieel)");
        });
    }

    @Test
    void verwijderVoorkeur_AndereContactgegevenActief_PartijBlijftActief() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.persist();
        });

        partijService.verwijderVoorkeur(IdentificatieType.BSN, "123456789", voorkeurId.get());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Partij.<Partij>findById(partijId.get()).getVerwijderdOp(),
                        "partij met nog een ander actief contactgegeven mag niet verwijderd worden"));
    }

    @Test
    void verwijderVoorkeur_AndereVoorkeurActief_PartijBlijftActief() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> teVerwijderenId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Voorkeur teVerwijderen = new Voorkeur();
            teVerwijderen.setVoorkeurType(VoorkeurType.WebsiteTaal);
            teVerwijderen.setWaarde("nl");
            teVerwijderen.setPartij(partij);
            teVerwijderen.persist();
            teVerwijderenId.set(teVerwijderen.id);

            Voorkeur blijftActief = new Voorkeur();
            blijftActief.setVoorkeurType(VoorkeurType.MagGebeldWorden);
            blijftActief.setWaarde("ja");
            blijftActief.setPartij(partij);
            blijftActief.persist();
        });

        partijService.verwijderVoorkeur(IdentificatieType.BSN, "123456789", teVerwijderenId.get());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Partij.<Partij>findById(partijId.get()).getVerwijderdOp(),
                        "partij met nog een andere actieve voorkeur mag niet verwijderd worden"));
    }

    @Test
    void verwijderContactgegeven_SetsVerwijderdOp() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.persist();
            contactId.set(contact.id);
        });

        // Kleine marge (i.p.v. exact "voor"): de DB-kolom rondt tijdstempels af op een grovere
        // eenheid dan Java's Instant, dus een in-memory "voor" kan net na afronding groter lijken
        // dan de teruggelezen waarde. Nog altijd 100x strenger dan de oude plusSeconds(5)-marge.
        Instant voor = Instant.now().minusMillis(50);
        Contactgegeven result = partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", contactId.get());

        Assertions.assertNotNull(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId.get());
            Assertions.assertNotNull(contact.getVerwijderdOp());
            Assertions.assertFalse(contact.getVerwijderdOp().isBefore(voor));
        });
    }

    @Test
    void verwijderContactgegeven_ContactNotFound_ReturnsNull() {
        Contactgegeven result = partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", UUID.randomUUID());
        Assertions.assertNull(result);
    }

    // Zie verwijderVoorkeur_IdBehoortTotAnderePartij_ReturnsNull voor de motivatie.
    @Test
    void verwijderContactgegeven_IdBehoortTotAnderePartij_ReturnsNull() {
        AtomicReference<UUID> contactIdVanA = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partijA = new Partij();
            partijA.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111142"));
            partijA.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partijA);
            contact.persist();
            contactIdVanA.set(contact.id);

            Partij partijB = new Partij();
            partijB.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111143"));
            partijB.persist();
        });

        Contactgegeven result = partijService.verwijderContactgegeven(IdentificatieType.BSN, "111111143", contactIdVanA.get());

        Assertions.assertNull(result, "een contactgegeven-id van een andere partij mag niet gevonden worden");
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Contactgegeven.<Contactgegeven>findById(contactIdVanA.get()).getVerwijderdOp(),
                        "het contactgegeven van partij A mag ongemoeid blijven"));
    }

    @Test
    void verwijderContactgegeven_LaatsteActieveKind_VerwijdertOokPartij() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.persist();
            contactId.set(contact.id);
        });

        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", contactId.get());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId.get());
            Assertions.assertNotNull(partij.getVerwijderdOp(), "partij zonder actieve children meer moet ook soft-deleted worden");
            Assertions.assertTrue(partij.getIdentificaties().stream().allMatch(i -> i.getVerwijderdOp() != null),
                    "identificaties van een gecascadete partij moeten ook mee-cascaden (uk_identificatie is partieel)");
        });
    }

    @Test
    void verwijderContactgegeven_AndereVoorkeurActief_PartijBlijftActief() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.persist();
            contactId.set(contact.id);

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
        });

        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", contactId.get());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Partij.<Partij>findById(partijId.get()).getVerwijderdOp(),
                        "partij met nog een andere actieve voorkeur mag niet verwijderd worden"));
    }

    @Test
    void verwijderContactgegeven_AndereContactgegevenActief_PartijBlijftActief() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> teVerwijderenId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Contactgegeven teVerwijderen = new Contactgegeven();
            teVerwijderen.setType(ContactType.Telefoonnummer);
            teVerwijderen.setWaarde("0612345678");
            teVerwijderen.setPartij(partij);
            teVerwijderen.persist();
            teVerwijderenId.set(teVerwijderen.id);

            Contactgegeven blijftActief = new Contactgegeven();
            blijftActief.setType(ContactType.Email);
            blijftActief.setWaarde("blijft@test.com");
            blijftActief.setPartij(partij);
            blijftActief.persist();
        });

        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", teVerwijderenId.get());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Partij.<Partij>findById(partijId.get()).getVerwijderdOp(),
                        "partij met nog een ander actief contactgegeven mag niet verwijderd worden"));
    }

    @Test
    void verwijderContactgegeven_AndereContactgegevenAlVerwijderd_VerwijdertOokPartij() {
        AtomicReference<UUID> partijId = new AtomicReference<>();
        AtomicReference<UUID> teVerwijderenId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
            partijId.set(partij.id);

            Contactgegeven alVerwijderd = new Contactgegeven();
            alVerwijderd.setType(ContactType.Email);
            alVerwijderd.setWaarde("oud@test.com");
            alVerwijderd.setPartij(partij);
            alVerwijderd.verwijder(Instant.now().truncatedTo(ChronoUnit.MICROS));
            alVerwijderd.persist();

            Contactgegeven teVerwijderen = new Contactgegeven();
            teVerwijderen.setType(ContactType.Telefoonnummer);
            teVerwijderen.setWaarde("0612345678");
            teVerwijderen.setPartij(partij);
            teVerwijderen.persist();
            teVerwijderenId.set(teVerwijderen.id);
        });

        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", teVerwijderenId.get());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNotNull(Partij.<Partij>findById(partijId.get()).getVerwijderdOp(),
                        "een al eerder verwijderd contactgegeven mag niet meetellen als actieve child"));
    }

    @Test
    void verwijderContactgegeven_AlreadyVerwijderd_ReturnsNull() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        AtomicReference<Instant> origineelVerwijderdOp = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.verwijder(Instant.now().truncatedTo(ChronoUnit.MICROS));
            contact.persist();
            contactId.set(contact.id);
            origineelVerwijderdOp.set(contact.getVerwijderdOp());
        });

        Contactgegeven result = partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", contactId.get());

        Assertions.assertNull(result, "een al-verwijderd contactgegeven wordt behandeld als niet gevonden");
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(origineelVerwijderdOp.get(),
                        Contactgegeven.<Contactgegeven>findById(contactId.get()).getVerwijderdOp(),
                        "een al gezette verwijderdOp mag niet worden overschreven"));
    }

    @Test
    void verwijderContactgegeven_LaatIsDefaultOngemoeid() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.setIsDefault(true);
            contact.persist();
            contactId.set(contact.id);
        });

        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", contactId.get());

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertTrue(Contactgegeven.<Contactgegeven>findById(contactId.get()).isIsDefault(),
                        "isDefault blijft staan zoals het was op het moment van verwijderen"));
    }

    @Test
    void getPartijResponse_HidesVerwijderdeVoorkeurEnContactgegeven() {
        createTestDienstverlenerWithDienst();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.verwijder(Instant.now());
            voorkeur.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.verwijder(Instant.now());
            contact.persist();
        });

        PartijResponse result = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", new PartijRequest());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getVoorkeuren().isEmpty());
        Assertions.assertTrue(result.getContactgegevens().isEmpty());

        // Het ongefilterde pad (isEmpty() == true) raakt findFilteredContactgegevens/-Voorkeuren
        // niet. Diezelfde verwijderdOp-filter zit ook in die twee methodes (PartijService.java) —
        // hier expliciet geraakt zodat een weggehaalde filter daar niet stil groen blijft.
        PartijRequest metDienstverlener = new PartijRequest();
        metDienstverlener.setIdentificatieType(IdentificatieType.BSN);
        metDienstverlener.setIdentificatieNummer("123456789");
        metDienstverlener.setDienstverlener("TestDV");

        PartijResponse viaDienstverlener = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", metDienstverlener);
        Assertions.assertTrue(viaDienstverlener.getVoorkeuren().isEmpty());
        Assertions.assertTrue(viaDienstverlener.getContactgegevens().isEmpty());

        PartijRequest metDienstNaam = new PartijRequest();
        metDienstNaam.setIdentificatieType(IdentificatieType.BSN);
        metDienstNaam.setIdentificatieNummer("123456789");
        metDienstNaam.setDienstNaam("TestDienst");

        PartijResponse viaDienstNaam = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", metDienstNaam);
        Assertions.assertTrue(viaDienstNaam.getVoorkeuren().isEmpty());
        Assertions.assertTrue(viaDienstNaam.getContactgegevens().isEmpty());
    }

    @Test
    void getPartijResponseBulk_HidesVerwijderdePartijEnKinderen() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij verwijderdePartij = new Partij();
            Identificatie verwijderdeIdentificatie = new Identificatie(IdentificatieType.BSN, "111111150");
            verwijderdePartij.addIdentificatie(verwijderdeIdentificatie);
            Instant verwijderdOp = Instant.now();
            verwijderdePartij.verwijder(verwijderdOp);
            verwijderdeIdentificatie.verwijder(verwijderdOp);
            verwijderdePartij.persist();

            Partij actievePartij = new Partij();
            actievePartij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111151"));
            actievePartij.persist();

            Voorkeur actief = new Voorkeur();
            actief.setVoorkeurType(VoorkeurType.WebsiteTaal);
            actief.setWaarde("nl");
            actief.setPartij(actievePartij);
            actief.persist();

            Voorkeur verwijderd = new Voorkeur();
            verwijderd.setVoorkeurType(VoorkeurType.MagGebeldWorden);
            verwijderd.setWaarde("ja");
            verwijderd.setPartij(actievePartij);
            verwijderd.verwijder(Instant.now());
            verwijderd.persist();
        });

        PartijIdentificatieRequest id1 = new PartijIdentificatieRequest();
        id1.setIdentificatieType(IdentificatieType.BSN);
        id1.setIdentificatieNummer("111111150");
        PartijIdentificatieRequest id2 = new PartijIdentificatieRequest();
        id2.setIdentificatieType(IdentificatieType.BSN);
        id2.setIdentificatieNummer("111111151");

        List<PartijResponse> result = partijService.getPartijResponseBulk(List.of(id1, id2));

        Assertions.assertEquals(1, result.size(), "de soft deleted partij hoort niet in de bulk-response te staan");
        PartijResponse response = result.get(0);
        Assertions.assertEquals(1, response.getVoorkeuren().size(), "de soft deleted voorkeur van de actieve partij hoort niet mee te tellen");
        Assertions.assertEquals("nl", response.getVoorkeuren().get(0).getWaarde());
    }

    @Test
    void addContactgegeven_ExistingIsVerwijderd_CreatesNewRow() {
        AtomicReference<UUID> verwijderdId = new AtomicReference<>();
        AtomicReference<Instant> verwijderdOp = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.verwijder(Instant.now().truncatedTo(ChronoUnit.MICROS));
            contact.persist();
            verwijderdId.set(contact.id);
            verwijderdOp.set(contact.getVerwijderdOp());
        });

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");

        Contactgegeven result =
                partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotEquals(verwijderdId.get(), result.id, "een rij met een soft delete mag niet hersteld worden, er moet een nieuwe komen");
        Assertions.assertNull(result.getVerwijderdOp());
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(verwijderdOp.get(), Contactgegeven.<Contactgegeven>findById(verwijderdId.get()).getVerwijderdOp(),
                        "de oude rij met de soft delete moet ongemoeid blijven"));
    }

    @Test
    void addContactgegeven_OpVolledigVerwijderdePartij_MaaktNieuwePartij() {
        AtomicReference<UUID> oudePartijId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            Identificatie identificatie = new Identificatie(IdentificatieType.BSN, "123456789");
            partij.addIdentificatie(identificatie);
            Instant verwijderdOp = Instant.now().truncatedTo(ChronoUnit.MICROS);
            // Zoals deleteLegePartij het echt doet: partij én haar identificatie(s) samen
            // gecascadet. Dit toetst de servicelaag (findOrCreatePartij mag de rij niet
            // herstellen), niet de partiële unique index zelf — H2's testschema kent uk_identificatie
            // niet (zie de klasse-comment op Identificatie). Dat de index in productie ook echt
            // partieel is, verifieert MigrationValidationTest tegen echte Postgres.
            partij.verwijder(verwijderdOp);
            identificatie.verwijder(verwijderdOp);
            partij.persist();
            oudePartijId.set(partij.id);
        });

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");

        Contactgegeven result = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotEquals(oudePartijId.get(), result.getPartij().id,
                "een verwijderde partij mag niet hersteld worden, er moet een nieuwe partij komen");
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNotNull(Partij.<Partij>findById(oudePartijId.get()).getVerwijderdOp(),
                        "de oude, verwijderde partij moet ongemoeid blijven"));
    }

    /**
     * Herhaald toevoegen/verwijderen van dezelfde (partij, type, waarde) mag niet vastlopen op de
     * find-lookup in addContactgegeven: elke cyclus moet een nieuwe rij aanmaken, ook als er
     * al meerdere soft deleted rijen met dezelfde waarde bestaan.
     * <p>
     * De partij houdt via een altijd-actieve voorkeur een actieve child, zodat verwijderContactgegeven
     * de partij niet tussentijds cascadet (zie PartijService.deleteLegePartij) — anders zou elke
     * cyclus op een verse partij draaien in plaats van herhaaldelijk op dezelfde, en zou deze test
     * de bedoelde regressie (find-lookup die vastloopt op een soft deleted rij van dezelfde partij)
     * niet meer dekken.
     * <p>
     * Dit toetst de servicelaag, niet de partiële unique index zelf (uk_contactgegeven_dedup,
     * WHERE verwijderd_op IS NULL): Contactgegeven heeft bewust geen {@code @UniqueConstraint}
     * (zie de klasse-comment daar), dus H2's testschema kent die index niet. Dat de index in
     * productie ook echt partieel is, verifieert MigrationValidationTest tegen echte Postgres.
     */
    @Test
    void addContactgegeven_MeerdereCyclusVanToevoegenEnVerwijderen_LaatMeerdereVerwijderdeRijenToe() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur altijdActief = new Voorkeur();
            altijdActief.setVoorkeurType(VoorkeurType.WebsiteTaal);
            altijdActief.setWaarde("nl");
            altijdActief.setPartij(partij);
            altijdActief.persist();
        });

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0612345678");

        UUID eersteId = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request).id;
        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", eersteId);

        UUID tweedeId = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request).id;
        partijService.verwijderContactgegeven(IdentificatieType.BSN, "123456789", tweedeId);

        UUID derdeId = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request).id;

        Assertions.assertNotEquals(eersteId, tweedeId);
        Assertions.assertNotEquals(tweedeId, derdeId);

        QuarkusTransaction.requiringNew().run(() -> {
            List<Contactgegeven> alleRijen = Contactgegeven.list("waarde", "0612345678");
            Assertions.assertEquals(3, alleRijen.size(), "elke cyclus voegt een nieuwe rij toe, geen hergebruik");

            long actief = alleRijen.stream().filter(c -> c.getVerwijderdOp() == null).count();
            Assertions.assertEquals(1, actief, "precies één rij mag actief zijn");
            Assertions.assertNull(Contactgegeven.<Contactgegeven>findById(derdeId).getVerwijderdOp());
            Assertions.assertNotNull(Contactgegeven.<Contactgegeven>findById(eersteId).getVerwijderdOp());
            Assertions.assertNotNull(Contactgegeven.<Contactgegeven>findById(tweedeId).getVerwijderdOp());

            Assertions.assertEquals(
                    Contactgegeven.<Contactgegeven>findById(eersteId).getPartij().id,
                    Contactgegeven.<Contactgegeven>findById(derdeId).getPartij().id,
                    "alle cycli moeten op dezelfde partij plaatsvinden, anders dekt dit niet meer de find-lookup binnen één partij");
        });
    }

    @Test
    void addContactgegeven_EmailExistingIsVerwijderd_CreatesNewRowNeedingVerification() {
        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@example.com");
            contact.setPartij(partij);
            contact.setIsGeverifieerd(true);
            contact.setGeverifieerdAt(Instant.now());
            contact.verwijder(Instant.now());
            contact.persist();
        });

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(ContactType.Email);
        request.setWaarde("test@example.com");

        Contactgegeven result =
                partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        Assertions.assertFalse(result.isIsGeverifieerd(),
                "een nieuwe rij moet opnieuw geverifieerd worden, ongeacht de verificatiestatus van de oude");
        Mockito.verify(emailVerificatieService).requestEmailVerificationCode("test@example.com");
    }

    @Test
    void addVoorkeur_ExistingIsVerwijderd_CreatesNewRow() {
        AtomicReference<UUID> verwijderdId = new AtomicReference<>();
        AtomicReference<Instant> verwijderdOp = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.verwijder(Instant.now().truncatedTo(ChronoUnit.MICROS));
            voorkeur.persist();
            verwijderdId.set(voorkeur.id);
            verwijderdOp.set(voorkeur.getVerwijderdOp());
        });

        VoorkeurRequest request = new VoorkeurRequest();
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("nl");

        Voorkeur result = partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotEquals(verwijderdId.get(), result.id, "een rij met een soft delete mag niet hersteld worden, er moet een nieuwe komen");
        Assertions.assertNull(result.getVerwijderdOp());
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertEquals(verwijderdOp.get(), Voorkeur.<Voorkeur>findById(verwijderdId.get()).getVerwijderdOp(),
                        "de oude rij met de soft delete moet ongemoeid blijven"));
    }

    @Test
    void addVoorkeur_OpVolledigVerwijderdePartij_MaaktNieuwePartij() {
        AtomicReference<UUID> oudePartijId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            Identificatie identificatie = new Identificatie(IdentificatieType.BSN, "123456789");
            partij.addIdentificatie(identificatie);
            Instant verwijderdOp = Instant.now().truncatedTo(ChronoUnit.MICROS);
            // Zoals deleteLegePartij het echt doet: partij én haar identificatie(s) samen
            // gecascadet. Dit toetst de servicelaag (findOrCreatePartij mag de rij niet
            // herstellen), niet de partiële unique index zelf — H2's testschema kent uk_identificatie
            // niet (zie de klasse-comment op Identificatie). Dat de index in productie ook echt
            // partieel is, verifieert MigrationValidationTest tegen echte Postgres.
            partij.verwijder(verwijderdOp);
            identificatie.verwijder(verwijderdOp);
            partij.persist();
            oudePartijId.set(partij.id);
        });

        VoorkeurRequest request = new VoorkeurRequest();
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("nl");

        Voorkeur result = partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotEquals(oudePartijId.get(), result.getPartij().id,
                "een verwijderde partij mag niet hersteld worden, er moet een nieuwe partij komen");
        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNotNull(Partij.<Partij>findById(oudePartijId.get()).getVerwijderdOp(),
                        "de oude, verwijderde partij moet ongemoeid blijven"));
    }

    @Test
    void updateVoorkeur_AlreadyVerwijderd_ReturnsFalse() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.verwijder(Instant.now());
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.setId(voorkeurId.get());
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("en");

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(result, "an already soft-deleted voorkeur must be treated as not found");
    }

    @Test
    void updateVoorkeur_CollisionIsSoftDeleted_DoesNotThrowConflict() {
        // Een soft deleted voorkeur met dezelfde (type, scope) mag de update niet blokkeren: ze is
        // onzichtbaar voor de aanroeper, dus telt niet mee als actieve botsing.
        AtomicReference<UUID> targetId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur verwijderd = new Voorkeur();
            verwijderd.setVoorkeurType(VoorkeurType.WebsiteTaal);
            verwijderd.setWaarde("nl");
            verwijderd.setPartij(partij);
            verwijderd.verwijder(Instant.now());
            verwijderd.persist();

            Voorkeur target = new Voorkeur();
            target.setVoorkeurType(VoorkeurType.MagGebeldWorden);
            target.setWaarde("ja");
            target.setPartij(partij);
            target.persist();
            targetId.set(target.id);
        });

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.setId(targetId.get());
        request.setVoorkeurType(VoorkeurType.WebsiteTaal);
        request.setWaarde("nl");

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);
        Assertions.assertTrue(result);
    }

    @Test
    void updateVoorkeurThenAddVoorkeur_DoesNotCreateDuplicateActiveVoorkeur() {
        // updateVoorkeur negeert een soft deleted botsing terecht, waardoor de soft deleted rij en
        // de nieuw-bijgewerkte rij dezelfde sleutel kunnen delen. Een latere addVoorkeur op die
        // sleutel moet afgewezen worden als conflict, nooit een tweede actieve rij invoegen.
        AtomicReference<UUID> targetId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur verwijderd = new Voorkeur();
            verwijderd.setVoorkeurType(VoorkeurType.WebsiteTaal);
            verwijderd.setWaarde("nl");
            verwijderd.setPartij(partij);
            verwijderd.verwijder(Instant.now());
            verwijderd.persist();

            Voorkeur target = new Voorkeur();
            target.setVoorkeurType(VoorkeurType.MagGebeldWorden);
            target.setWaarde("ja");
            target.setPartij(partij);
            target.persist();
            targetId.set(target.id);
        });

        VoorkeurUpdateRequest updateRequest = new VoorkeurUpdateRequest();
        updateRequest.setId(targetId.get());
        updateRequest.setVoorkeurType(VoorkeurType.WebsiteTaal);
        updateRequest.setWaarde("nl");
        Assertions.assertTrue(partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", updateRequest));

        VoorkeurRequest addRequest = new VoorkeurRequest();
        addRequest.setVoorkeurType(VoorkeurType.WebsiteTaal);
        addRequest.setWaarde("de");

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addVoorkeur(IdentificatieType.BSN, "123456789", addRequest));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());

        QuarkusTransaction.requiringNew().run(() -> {
            List<Voorkeur> actief = Voorkeur.list("voorkeurType = ?1 AND verwijderdOp IS NULL", VoorkeurType.WebsiteTaal);
            Assertions.assertEquals(1, actief.size(),
                    "must never end up with two active voorkeuren on the same (partij, type, scope) key");
            Assertions.assertEquals("nl", actief.get(0).getWaarde(), "de bestaande actieve rij mag niet overschreven worden");
        });
    }

    @Test
    void findOrCreateDienstverlenerDienst_DvBroadScope_DeduplicatesOnRepeat() {
        // Regression: a `dienst = ?` JPQL with a null parameter never matched in SQL,
        // so each call with dienst==null inserted a fresh row and the UNIQUE(dv,dienst)
        // constraint did not deduplicate (NULL != NULL). The fix splits the query and a
        // partial unique index covers the DV-broad slot.
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("DV-broad");
            dv.persist();
        });

        DienstverlenerDienst first = dienstverlenerService.findOrCreateDienstverlenerDienst(
                dienstverlenerService.getDienstverlener("DV-broad"), null);
        DienstverlenerDienst second = dienstverlenerService.findOrCreateDienstverlenerDienst(
                dienstverlenerService.getDienstverlener("DV-broad"), null);

        Assertions.assertEquals(first.id, second.id);

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertEquals(1, DienstverlenerDienst.count("dienst IS NULL"));
        });
    }

    @Test
    void updateContactgegeven_typeChangeWhileDefault_demotesOldDefaultForNewType() {
        // Regression for HIGH bug #9: when type changes while isDefault stays true, the demote
        // must run for the NEW type, otherwise the partial unique index violates on flush.
        AtomicReference<UUID> emailDefaultId = new AtomicReference<>();
        AtomicReference<UUID> phoneDefaultId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven emailDefault = new Contactgegeven();
            emailDefault.setType(ContactType.Email);
            emailDefault.setWaarde("a@test.com");
            emailDefault.setIsDefault(true);
            emailDefault.setPartij(partij);
            emailDefault.persist();
            emailDefaultId.set(emailDefault.id);

            Contactgegeven phoneDefault = new Contactgegeven();
            phoneDefault.setType(ContactType.Telefoonnummer);
            phoneDefault.setWaarde("0612345678");
            phoneDefault.setIsDefault(true);
            phoneDefault.setPartij(partij);
            phoneDefault.persist();
            phoneDefaultId.set(phoneDefault.id);
        });

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        // Change the Email-default to a Telefoonnummer while keeping isDefault=true.
        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.setId(emailDefaultId.get());
        request.setType(ContactType.Telefoonnummer);
        request.setWaarde("0699999999");
        request.setIsDefault(true);

        Assertions.assertTrue(partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request));

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven oldPhoneDefault = Contactgegeven.findById(phoneDefaultId.get());
            Contactgegeven updated = Contactgegeven.findById(emailDefaultId.get());
            Assertions.assertFalse(oldPhoneDefault.isIsDefault(),
                    "pre-existing Telefoonnummer default must be demoted when the row morphed into a Telefoonnummer default");
            Assertions.assertTrue(updated.isIsDefault());
            Assertions.assertEquals(ContactType.Telefoonnummer, updated.getType());
        });
    }
}
