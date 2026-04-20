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
        Response mockResponse = Mockito.mock(Response.class);
        Response.StatusType mockStatusType = Mockito.mock(Response.StatusType.class);
        Mockito.when(mockStatusType.getStatusCode()).thenReturn(400);
        Mockito.when(mockResponse.getStatusInfo()).thenReturn(mockStatusType);
        Mockito.when(mockResponse.getStatus()).thenReturn(400);
        Mockito.when(mockResponse.readEntity(String.class)).thenReturn("Error message");

        WebApplicationException exception = new WebApplicationException(mockResponse);
        Mockito.doThrow(exception).when(emailVerificatieApi).requestPost(Mockito.any());

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

        Response mockResponse = Mockito.mock(Response.class);
        Response.StatusType mockStatusType = Mockito.mock(Response.StatusType.class);
        Mockito.when(mockStatusType.getStatusCode()).thenReturn(400);
        Mockito.when(mockResponse.getStatusInfo()).thenReturn(mockStatusType);
        Mockito.when(mockResponse.getStatus()).thenReturn(400);
        Mockito.when(mockResponse.readEntity(String.class)).thenReturn("Error message");

        WebApplicationException exception = new WebApplicationException(mockResponse);
        Mockito.doThrow(exception).when(emailVerificatieApi).verifyPost(Mockito.any());

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

}
