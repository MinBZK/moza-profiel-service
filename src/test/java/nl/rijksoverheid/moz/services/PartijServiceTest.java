package nl.rijksoverheid.moz.services;

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
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicLong;

@QuarkusTest
public class PartijServiceTest {

    @Inject
    PartijService partijService;

    @InjectMock
    EmailVerificatieService emailVerificatieService;

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
        request.afdelingId = 0L;

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
        request.afdelingId = 0L;

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
        request.afdelingId = 0L;

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
    void addVoorkeur_NewPartij() {
        VoorkeurRequest request = new VoorkeurRequest();
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "en";

        partijService.addVoorkeur(IdentificatieType.BSN, "123456789", request);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Assertions.assertNotNull(partij);
            Assertions.assertEquals(1, partij.getVoorkeuren().size());
        });
    }

    @Test
    void updateContactgegeven_Success() {
        AtomicLong contactId = new AtomicLong();
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
        request.afdelingId = 0L;

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
        request.id = 1L;
        request.type = ContactType.Email;
        request.waarde = "test@test.com";
        request.afdelingId = 0L;

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "999999999", request);

        Assertions.assertFalse(result);
    }

    @Test
    void updateContactgegeven_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        ContactgegevenUpdateRequest request = new ContactgegevenUpdateRequest();
        request.id = 999L;
        request.type = ContactType.Email;
        request.waarde = "test@test.com";
        request.afdelingId = 0L;

        boolean result = partijService.updateContactgegeven(IdentificatieType.BSN, "123456789", request);

        Assertions.assertFalse(result);
    }

    @Test
    void updateVoorkeur_Success() {
        AtomicLong voorkeurId = new AtomicLong();
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
    void updateVoorkeur_PartijNotFound() {
        VoorkeurUpdateRequest request = new VoorkeurUpdateRequest();
        request.id = 1L;
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "en";

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "999999999", request);

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
        request.id = 999L;
        request.voorkeurType = VoorkeurType.WebsiteTaal;
        request.waarde = "en";

        boolean result = partijService.updateVoorkeur(IdentificatieType.BSN, "123456789", request);

        Assertions.assertFalse(result);
    }

    @Test
    void deleteContactgegeven_Success() {
        AtomicLong contactId = new AtomicLong();
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
        boolean result = partijService.deleteContactgegeven(IdentificatieType.BSN, "999999999", 1L);
        Assertions.assertFalse(result);
    }

    @Test
    void deleteContactgegeven_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        boolean result = partijService.deleteContactgegeven(IdentificatieType.BSN, "123456789", 999L);
        Assertions.assertFalse(result);
    }

    @Test
    void deleteVoorkeur_Success() {
        AtomicLong voorkeurId = new AtomicLong();
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
    void deleteVoorkeur_PartijNotFound() {
        boolean result = partijService.deleteVoorkeur(IdentificatieType.BSN, "999999999", 1L);
        Assertions.assertFalse(result);
    }

    @Test
    void deleteVoorkeur_VoorkeurNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        boolean result = partijService.deleteVoorkeur(IdentificatieType.BSN, "123456789", 999L);
        Assertions.assertFalse(result);
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
    void getPartijResponse_NotFound() {
        PartijRequest request = new PartijRequest();
        PartijResponse result = partijService.getPartijResponse(IdentificatieType.BSN, "999999999", request);

        Assertions.assertNull(result);
    }

    @Test
    void getPartijResponse_WithFilters() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();

            Afdeling afdeling = new Afdeling();
            afdeling.setBeschrijving("TestAfdeling");
            afdeling.setDienstverlener(dienstverlener);
            afdeling.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.setAfdeling(afdeling);
            contact.persist();
        });

        PartijRequest request = new PartijRequest();
        request.dienstverlener = "TestDV";
        PartijResponse result = partijService.getPartijResponse(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.contactgegevens.size());
    }

    @Test
    void getPartijFiltered_Found() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.persist();
        });

        PartijRequest request = new PartijRequest();
        Partij result = partijService.getPartijFiltered(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getContactgegevens().size());
    }

    @Test
    void getPartijFiltered_NotFound() {
        PartijRequest request = new PartijRequest();
        Partij result = partijService.getPartijFiltered(IdentificatieType.BSN, "999999999", request);

        Assertions.assertNull(result);
    }

    @Test
    void getPartijFiltered_WithDienstverlenerFilter() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();

            Afdeling afdeling = new Afdeling();
            afdeling.setBeschrijving("TestAfdeling");
            afdeling.setDienstverlener(dienstverlener);
            afdeling.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.setAfdeling(afdeling);
            contact.persist();
        });

        PartijRequest request = new PartijRequest();
        request.dienstverlener = "TestDV";
        Partij result = partijService.getPartijFiltered(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getContactgegevens().size());
    }

    @Test
    void getPartijFiltered_WithDienstverlenerOinFilter() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();

            Afdeling afdeling = new Afdeling();
            afdeling.setBeschrijving("TestAfdeling");
            afdeling.setDienstverlener(dienstverlener);
            afdeling.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.setAfdeling(afdeling);
            contact.persist();
        });

        PartijRequest request = new PartijRequest();
        request.dienstverlenerOin = "12345";
        Partij result = partijService.getPartijFiltered(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getContactgegevens().size());
    }

    @Test
    void getPartijFiltered_WithAfdelingBeschrijvingFilter() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("TestDV");
            dienstverlener.setOin("12345");
            dienstverlener.persist();

            Afdeling afdeling = new Afdeling();
            afdeling.setBeschrijving("TestAfdeling");
            afdeling.setDienstverlener(dienstverlener);
            afdeling.persist();

            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setPartij(partij);
            contact.setAfdeling(afdeling);
            contact.persist();
        });

        PartijRequest request = new PartijRequest();
        request.afdelingBeschrijving = "TestAfdeling";
        Partij result = partijService.getPartijFiltered(IdentificatieType.BSN, "123456789", request);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getContactgegevens().size());
    }

}
