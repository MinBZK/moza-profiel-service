package nl.rijksoverheid.moz.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Scope;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.external.clients.verificatie_service.api.VerificationControllerApi;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

@QuarkusTest
public class EmailVerificatieServiceTest {

    @Inject
    EmailVerificatieService service;

    @InjectMock
    @RestClient
    VerificationControllerApi emailVerificatieApi;

    @Inject
    VerificatieServiceGuard verificatieServiceGuard;

    @BeforeEach
    void resetCircuitBreaker() {
        verificatieServiceGuard.reset();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        Contactgegeven.deleteAll();
        Voorkeur.deleteAll();
        Scope.deleteAll();
        Dienst.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
        Dienstverlener.deleteAll();
    }

    @Test
    void requestEmailVerificationCode_Success() {
        String referenceId = "test-reference-id-123";
        Mockito.doReturn(referenceId).when(emailVerificatieApi).requestPost(Mockito.any());

        String result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertEquals(referenceId, result);
    }

    @Test
    void requestEmailVerificationCode_NullResponse() {
        Mockito.doReturn(null).when(emailVerificatieApi).requestPost(Mockito.any());

        String result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertNull(result);
    }

    @Test
    void requestEmailVerificationCode_WebApplicationException() {
        Mockito.doThrow(createWebApplicationException(400)).when(emailVerificatieApi).requestPost(Mockito.any());

        String result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertNull(result);
    }

    @Test
    void requestEmailVerificationCode_Exception() {
        Mockito.doThrow(new RuntimeException("Error message")).when(emailVerificatieApi).requestPost(Mockito.any());
        String result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertNull(result);
    }

    @Test
    void verifieerEmail_PartijNotFound() {
        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_ContactAlreadyVerified() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setGeverifieerdAt(LocalDateTime.now());
            contact.setPartij(partij);
            contact.persist();
        });

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_Success() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("test-ref-id");
            contact.setPartij(partij);
            contact.persist();
        });

        VerificationResponse response = new VerificationResponse();
        response.setSuccess(true);
        Mockito.doReturn(response).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertTrue(result);

        // Verify that the contact was marked as verified and referenceId cleared
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElse(null);
            Assertions.assertNotNull(contact);
            Assertions.assertNotNull(contact.getGeverifieerdAt());
            Assertions.assertTrue(contact.isIsValid());
            Assertions.assertNull(contact.getVerificatieReferentieId());
        });
    }

    @Test
    void verifieerEmail_ApiResponseNull() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("test-ref-id");
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doReturn(null).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_ApiResponseSuccessFalse() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("test-ref-id");
            contact.setPartij(partij);
            contact.persist();
        });

        VerificationResponse response = new VerificationResponse();
        response.setSuccess(false);
        Mockito.doReturn(response).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_WebApplicationException() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("test-ref-id");
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doThrow(createWebApplicationException(400)).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_GenericException() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("test-ref-id");
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doThrow(new RuntimeException("Unexpected error")).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        WebApplicationException exception = Assertions.assertThrows(WebApplicationException.class, () -> service.verifieerEmail(request));
        Assertions.assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), exception.getResponse().getStatus());
    }

    @Test
    void vraagEmailVerificatieCodeAan_PartijNotFound() {
        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result);
        Mockito.verify(emailVerificatieApi, Mockito.never()).requestPost(Mockito.any());
    }

    @Test
    void vraagEmailVerificatieCodeAan_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();
        });

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result);
        Mockito.verify(emailVerificatieApi, Mockito.never()).requestPost(Mockito.any());
    }

    @Test
    void vraagEmailVerificatieCodeAan_Success() {
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

        Mockito.doReturn("new-reference-id").when(emailVerificatieApi).requestPost(Mockito.any());

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.OK.getStatusCode(), result);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertEquals("new-reference-id", contact.getVerificatieReferentieId());
            Assertions.assertNull(contact.getGeverifieerdAt());
            Assertions.assertFalse(contact.isIsValid());
        });
    }

    @Test
    void vraagEmailVerificatieCodeAan_AlreadyVerifiedResetsState() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setGeverifieerdAt(LocalDateTime.now());
            contact.setIsValid(true);
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doReturn("new-reference-id").when(emailVerificatieApi).requestPost(Mockito.any());

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.OK.getStatusCode(), result);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertEquals("new-reference-id", contact.getVerificatieReferentieId());
            Assertions.assertNull(contact.getGeverifieerdAt());
            Assertions.assertFalse(contact.isIsValid());
        });
    }

    @Test
    void vraagEmailVerificatieCodeAan_ExternalServiceFails() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("old-reference-id");
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doReturn(null).when(emailVerificatieApi).requestPost(Mockito.any());

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), result);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertEquals("old-reference-id", contact.getVerificatieReferentieId());
        });
    }

    @Test
    void circuitBreaker_OpensAfterThresholdExceeded() {
        Mockito.doThrow(createWebApplicationException(500)).when(emailVerificatieApi).requestPost(Mockito.any());

        for (int i = 0; i < 5; i++) {
            service.requestEmailVerificationCode("email@email.com");
        }

        // Reset mock to success � if the circuit is open the API should not be called
        Mockito.doReturn("reference-id").when(emailVerificatieApi).requestPost(Mockito.any());

        String result = service.requestEmailVerificationCode("email@email.com");

        Assertions.assertNull(result);
        Mockito.verify(emailVerificatieApi, Mockito.times(5)).requestPost(Mockito.any());
    }

    @Test
    void circuitBreaker_SharedBetweenMethods_RequestCodeFailureBlocksVerifieerEmail() {
        Mockito.doThrow(createWebApplicationException(500)).when(emailVerificatieApi).requestPost(Mockito.any());

        for (int i = 0; i < 5; i++) {
            service.requestEmailVerificationCode("email@email.com");
        }

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("test-ref-id");
            contact.setPartij(partij);
            contact.persist();
        });

        VerificationResponse response = new VerificationResponse();
        response.setSuccess(true);
        Mockito.doReturn(response).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);

        Assertions.assertFalse(result);
        Mockito.verify(emailVerificatieApi, Mockito.never()).verifyPost(Mockito.any());
    }

    @Test
    void circuitBreaker_StaysClosedWithInsufficientFailures() {
        Mockito.doThrow(createWebApplicationException(500)).when(emailVerificatieApi).requestPost(Mockito.any());

        // 4 failures below requestVolumeThreshold of 5, circuit not yet evaluated
        for (int i = 0; i < 4; i++) {
            service.requestEmailVerificationCode("email@email.com");
        }

        // 5th call succeeds, window now has 5 calls (80% failure ratio, below 100% threshold), circuit stays closed
        Mockito.doReturn("reference-id").when(emailVerificatieApi).requestPost(Mockito.any());
        service.requestEmailVerificationCode("email@email.com");

        // 6th call still reaches the API, confirms circuit is closed even after window evaluation
        String result = service.requestEmailVerificationCode("email@email.com");

        Assertions.assertEquals("reference-id", result);
        Mockito.verify(emailVerificatieApi, Mockito.times(6)).requestPost(Mockito.any());
    }

    private WebApplicationException createWebApplicationException(int status) {
        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(status);
        Mockito.when(mockResponse.getStatusInfo()).thenReturn(Response.Status.fromStatusCode(status));
        Mockito.when(mockResponse.readEntity(String.class)).thenReturn("Error message");
        return new WebApplicationException(mockResponse);
    }
}
