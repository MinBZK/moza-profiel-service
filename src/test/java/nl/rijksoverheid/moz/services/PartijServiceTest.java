package nl.rijksoverheid.moz.services;

import nl.rijksoverheid.moz.exception.AuthorizationException;
import nl.rijksoverheid.moz.exception.BusinessException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.ScopeRequest;
import nl.rijksoverheid.moz.dto.request.TeVerwijderenOpRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
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
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
        request.type = ContactType.Email;
        request.waarde = "test@test.com";

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
        request.type = ContactType.Telefoonnummer;
        request.waarde = "0612345678";

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
        request.type = ContactType.Email;
        request.waarde = "test@test.com";

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
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";

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
        request.id = contactId.get();
        request.type = ContactType.Email;
        request.waarde = "new@test.com";

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
        request.id = UUID.randomUUID();
        request.type = ContactType.Email;
        request.waarde = "test@test.com";

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
        request.id = voorkeurId.get();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "en";

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertEquals("en", voorkeur.getWaarde());
        });
    }

    @Test
    void deleteContactgegeven_Success() {
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.persist();
            contactId.set(contact.id);
        });

        boolean result = partijService.deleteContactgegeven(IdentificatieType.BSN, "123456789", contactId.get());

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId.get());
            Assertions.assertNull(contact);
        });
    }

    @Test
    void deleteContactgegeven_PartijNotFound() {
        boolean result = partijService.deleteContactgegeven(IdentificatieType.BSN, "999999999", UUID.randomUUID());
        Assertions.assertFalse(result);
    }

    @Test
    void deleteVoorkeur_Success() {
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

        boolean result = partijService.deleteVoorkeur(IdentificatieType.BSN, "123456789", voorkeurId.get());

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertNull(voorkeur);
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
    void addContactgegeven_Duplicate_NewScope_AddsScopeToExisting() {
        createTestDienstverlenerWithDienst();

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest unscoped = new ContactgegevenRequest();
        unscoped.type = ContactType.Email;
        unscoped.waarde = "scope@test.com";
        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", unscoped);

        ContactgegevenRequest scoped = new ContactgegevenRequest();
        scoped.type = ContactType.Email;
        scoped.waarde = "scope@test.com";
        scoped.scope = new ScopeRequest();
        scoped.scope.dienstverlenerNaam = "TestDV";
        scoped.scope.dienstNaam = "TestDienst";

        PartijService.AddContactgegevenResult result = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", scoped);

        Assertions.assertFalse(result.wasCreated(), "no new contactgegeven row was created");
        Assertions.assertTrue(result.scopeAdded(), "a new scope was attached to the existing row");

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Contactgegeven cg = partij.getContactgegevens().get(0);
            Assertions.assertEquals(1, cg.getScopes().size());
        });
    }

    @Test
    void addContactgegeven_Duplicate_MatchingScope_ReturnsExisting() {
        createTestDienstverlenerWithDienst();

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest scoped = new ContactgegevenRequest();
        scoped.type = ContactType.Email;
        scoped.waarde = "match@test.com";
        scoped.scope = new ScopeRequest();
        scoped.scope.dienstverlenerNaam = "TestDV";
        scoped.scope.dienstNaam = "TestDienst";

        PartijService.AddContactgegevenResult first =
                partijService.addContactgegeven(IdentificatieType.BSN, "123456789", scoped);
        PartijService.AddContactgegevenResult second =
                partijService.addContactgegeven(IdentificatieType.BSN, "123456789", scoped);

        Assertions.assertTrue(first.wasCreated());
        Assertions.assertFalse(second.wasCreated());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Assertions.assertEquals(1, partij.getContactgegevens().get(0).getScopes().size());
        });
    }

    @Test
    void addContactgegeven_Duplicate_UnverifiedEmail_ResendsVerificationCode() {
        Mockito.doReturn("first-ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.type = ContactType.Email;
        request.waarde = "unverified@test.com";

        PartijService.AddContactgegevenResult first = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertTrue(first.wasCreated());

        Mockito.doReturn("second-ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        PartijService.AddContactgegevenResult second = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(second.wasCreated());
        Mockito.verify(emailVerificatieService, Mockito.times(2)).requestEmailVerificationCode("unverified@test.com");

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().get(0);
            Assertions.assertEquals("second-ref", contact.getVerificatieReferentieId());
            Assertions.assertNull(contact.getGeverifieerdAt());
            Assertions.assertFalse(contact.isIsGeverifieerd());
        });
    }

    @Test
    void addContactgegeven_Duplicate_VerifiedEmail_DoesNotResendCode() {
        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.type = ContactType.Email;
        request.waarde = "verified@test.com";

        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().get(0);
            contact.setGeverifieerdAt(java.time.Instant.now());
            contact.setIsGeverifieerd(true);
        });

        PartijService.AddContactgegevenResult second = partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(second.wasCreated());
        Mockito.verify(emailVerificatieService, Mockito.times(1)).requestEmailVerificationCode(Mockito.anyString());
    }

    @Test
    void addVoorkeur_Duplicate_NoScope_ReturnsExisting() {
        VoorkeurRequest request = new VoorkeurRequest();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";

        PartijService.AddVoorkeurResult first =
                partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);
        PartijService.AddVoorkeurResult second =
                partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        Assertions.assertTrue(first.wasCreated());
        Assertions.assertFalse(second.wasCreated());
        Assertions.assertEquals(first.voorkeur().id, second.voorkeur().id);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getVoorkeuren().size());
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
        request.id = UUID.randomUUID();
        request.type = ContactType.Email;
        request.waarde = "test@test.com";

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(result);
    }

    @Test
    void updateVoorkeur_VoorkeurNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.id = UUID.randomUUID();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "en";

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);
        Assertions.assertFalse(result);
    }

    @Test
    void deleteContactgegeven_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        boolean result = partijService.deleteContactgegeven(IdentificatieType.BSN, "123456789", UUID.randomUUID());
        Assertions.assertFalse(result);
    }

    @Test
    void deleteVoorkeur_VoorkeurNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        boolean result = partijService.deleteVoorkeur(IdentificatieType.BSN, "123456789", UUID.randomUUID());
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
        request.dienstNaam = "TestDienst";
        PartijResponse result = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        // Returned: scoped-TestDienst contact + unscoped phone. Excluded: phone scoped to OtherDienst.
        Assertions.assertEquals(2, result.contactgegevens.size());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(3, partij.getContactgegevens().size(),
                    "Filtered read must not delete contactgegevens that were excluded by the filter");
        });
    }

    @Test
    void resolveDienstverlenerDienst_DienstNaamWithoutDienstverlenerNaam_ThrowsBadRequest() {
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.type = ContactType.Email;
        request.waarde = "test@test.com";
        request.scope = new ScopeRequest();
        request.scope.dienstNaam = "TestDienst";

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
        request.type = ContactType.Email;
        request.waarde = "cross@test.com";
        request.scope = new ScopeRequest();
        request.scope.dienstverlenerNaam = "DV-A";
        request.scope.dienstNaam = "B-Vergunning";

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
        request.id = secondId.get();
        request.type = ContactType.Email;
        request.waarde = "b@test.com";
        request.isDefault = true;

        Assertions.assertTrue(partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request));

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertFalse(((Contactgegeven) Contactgegeven.findById(firstId.get())).isIsDefault(),
                    "previous default must be demoted");
            Assertions.assertTrue(((Contactgegeven) Contactgegeven.findById(secondId.get())).isIsDefault(),
                    "new default must be set");
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
        request.id = id.get();
        request.type = ContactType.Email;
        request.waarde = "a@test.com";
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
        request.id = id.get();
        request.type = ContactType.Email;
        request.waarde = "a@test.com";
        request.isDefault = false;

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
        request.id = id.get();
        request.type = ContactType.Email;
        request.waarde = "user@test.com";
        request.isDefault = true;

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
        request.id = targetId.get();
        request.type = ContactType.Email;
        request.waarde = "a@test.com";

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request));
        Assertions.assertEquals(BusinessException.Kind.CONFLICT, ex.getKind());
    }

    @Test
    void addContactgegeven_EmailIsNormalisedToLowercase() {
        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest first = new ContactgegevenRequest();
        first.type = ContactType.Email;
        first.waarde = "User@Test.COM";
        partijService.addContactgegeven(IdentificatieType.BSN, "123456789", first);

        // A second POST with different case should be treated as duplicate (no new row).
        ContactgegevenRequest second = new ContactgegevenRequest();
        second.type = ContactType.Email;
        second.waarde = "USER@TEST.COM";
        PartijService.AddContactgegevenResult result =
                partijService.addContactgegeven(IdentificatieType.BSN, "123456789", second);
        Assertions.assertFalse(result.wasCreated());

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertEquals(1, partij.getContactgegevens().size());
            Assertions.assertEquals("user@test.com", partij.getContactgegevens().get(0).getWaarde());
        });
    }

    @Test
    void addVoorkeur_WithValidTeVerwijderenOp_SetsField() {
        // A date in the future, within the 7-year window.
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);

        VoorkeurRequest request = new VoorkeurRequest();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";
        request.teVerwijderenOp = teVerwijderenOp;

        PartijService.AddVoorkeurResult result = partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(result.voorkeur().id);
            Assertions.assertEquals(teVerwijderenOp, voorkeur.getTeVerwijderenOp());
        });
    }

    @Test
    void addVoorkeur_WithPastTeVerwijderenOp_ThrowsBadRequest() {
        VoorkeurRequest request = new VoorkeurRequest();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";
        request.teVerwijderenOp = Instant.now().minus(Duration.ofDays(1));

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request));
        Assertions.assertEquals(BusinessException.Kind.BAD_REQUEST, ex.getKind());
    }

    @Test
    void addVoorkeur_WithTeVerwijderenOpBeyond7Years_ThrowsBadRequest() {
        Instant beyondMax = Instant.now().atZone(ZoneOffset.UTC).plus(Period.ofYears(7)).plusDays(1).toInstant();

        VoorkeurRequest request = new VoorkeurRequest();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";
        request.teVerwijderenOp = beyondMax;

        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request));
        Assertions.assertEquals(BusinessException.Kind.BAD_REQUEST, ex.getKind());
    }

    @Test
    void updateVoorkeur_WithValidTeVerwijderenOp_SetsField() {
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

        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.id = voorkeurId.get();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";
        request.teVerwijderenOp = teVerwijderenOp;

        partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertEquals(teVerwijderenOp, voorkeur.getTeVerwijderenOp());
        });
    }

    @Test
    void updateVoorkeur_WithNullTeVerwijderenOp_LeavesExistingValueUnchanged() {
        // teVerwijderenOp already set on the entity; a PUT without the field must not clear it.
        Instant existing = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setTeVerwijderenOp(existing);
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);
        });

        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.id = voorkeurId.get();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "nl";
        // teVerwijderenOp intentionally omitted

        partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertEquals(existing, voorkeur.getTeVerwijderenOp());
        });
    }

    @Test
    void addContactgegeven_WithValidTeVerwijderenOp_SetsField() {
        // Wiring check: confirms applyTeVerwijderenOp is also called for Contactgegeven.
        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);

        Mockito.doReturn("ref").when(emailVerificatieService).requestEmailVerificationCode(Mockito.anyString());

        ContactgegevenRequest request = new ContactgegevenRequest();
        request.type = ContactType.Telefoonnummer;
        request.waarde = "0612345678";
        request.teVerwijderenOp = teVerwijderenOp;

        PartijService.AddContactgegevenResult result =
                partijService.addContactgegeven(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(result.contactgegeven().id);
            Assertions.assertEquals(teVerwijderenOp, contact.getTeVerwijderenOp());
        });
    }

    @Test
    void updateVoorkeurTeVerwijderenOpByDienstverlener_WithMatchingScope_SetsField() {
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);

            DienstverlenerDienst link = new DienstverlenerDienst(dv, null);
            link.persist();
            ScopeVoorkeur scope = new ScopeVoorkeur(voorkeur, link);
            scope.persist();
        });

        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);

        TeVerwijderenOpRequest request = new TeVerwijderenOpRequest();
        request.id = voorkeurId.get();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.dienstverlenerNaam = "TestDV";
        request.teVerwijderenOp = teVerwijderenOp;

        boolean result = partijService.updateVoorkeurTeVerwijderenOpByDienstverlener(request);

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId.get());
            Assertions.assertEquals(teVerwijderenOp, voorkeur.getTeVerwijderenOp());
        });
    }

    @Test
    void updateVoorkeurTeVerwijderenOpByDienstverlener_WithoutScope_ThrowsForbidden() {
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

        TeVerwijderenOpRequest request = new TeVerwijderenOpRequest();
        request.id = voorkeurId.get();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.dienstverlenerNaam = "OnbekendeDV";
        request.teVerwijderenOp = Instant.now().plus(Duration.ofDays(365));

        AuthorizationException ex = Assertions.assertThrows(
                AuthorizationException.class,
                () -> partijService.updateVoorkeurTeVerwijderenOpByDienstverlener(request));
        Assertions.assertEquals("Forbidden", ex.getTitle());
    }

    @Test
    void updateVoorkeurTeVerwijderenOpByDienstverlener_VoorkeurNotFound_ReturnsFalse() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        TeVerwijderenOpRequest request = new TeVerwijderenOpRequest();
        request.id = UUID.randomUUID();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.dienstverlenerNaam = "TestDV";
        request.teVerwijderenOp = Instant.now().plus(Duration.ofDays(365));

        boolean result = partijService.updateVoorkeurTeVerwijderenOpByDienstverlener(request);
        Assertions.assertFalse(result);
    }

    @Test
    void updateVoorkeurTeVerwijderenOpByDienstverlener_WithWrongDienstNaam_ThrowsForbidden() {
        // DV has a scope for "TestDienst", but the request asks for "AndereDienst" → 403.
        AtomicReference<UUID> voorkeurId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();

            Dienst dienst = new Dienst();
            dienst.setNaam("TestDienst");
            dienst.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.persist();
            voorkeurId.set(voorkeur.id);

            DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
            link.persist();
            new ScopeVoorkeur(voorkeur, link).persist();
        });

        TeVerwijderenOpRequest request = new TeVerwijderenOpRequest();
        request.id = voorkeurId.get();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.dienstverlenerNaam = "TestDV";
        request.dienstNaam = "AndereDienst";
        request.teVerwijderenOp = Instant.now().plus(Duration.ofDays(365));

        AuthorizationException ex = Assertions.assertThrows(
                AuthorizationException.class,
                () -> partijService.updateVoorkeurTeVerwijderenOpByDienstverlener(request));
        Assertions.assertEquals("Forbidden", ex.getTitle());
    }

    @Test
    void updateContactgegevenTeVerwijderenOpByDienstverlener_WithMatchingScope_SetsField() {
        // Wiring check: confirms scope authorization also works for Contactgegeven.
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("TestDV");
            dv.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.persist();
            contactId.set(contact.id);

            DienstverlenerDienst link = new DienstverlenerDienst(dv, null);
            link.persist();
            ScopeContactgegeven scope = new ScopeContactgegeven(contact, link);
            scope.persist();
        });

        Instant teVerwijderenOp = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);

        TeVerwijderenOpRequest request = new TeVerwijderenOpRequest();
        request.id = contactId.get();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.dienstverlenerNaam = "TestDV";
        request.teVerwijderenOp = teVerwijderenOp;

        boolean result = partijService.updateContactgegevenTeVerwijderenOpByDienstverlener(request);

        Assertions.assertTrue(result);
        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId.get());
            Assertions.assertEquals(teVerwijderenOp, contact.getTeVerwijderenOp());
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
        request.id = emailDefaultId.get();
        request.type = ContactType.Telefoonnummer;
        request.waarde = "0699999999";
        request.isDefault = true;

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
