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
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.external.clients.email.api.DefaultApi;
import nl.rijksoverheid.moz.external.clients.email.model.VerificationRequestsPost201Response;
import nl.rijksoverheid.moz.external.clients.email.model.VerificationRequestsVerifyPost200Response;
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
    DefaultApi emailVerificatieApi;

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
    void requestEmailVerificationCode_Success() {
        VerificationRequestsPost201Response response = new VerificationRequestsPost201Response();
        response.setSuccess(true);
        Mockito.doReturn(response).when(emailVerificatieApi).verificationRequestsPost(Mockito.any());

        boolean result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertTrue(result);
    }

    @Test
    void requestEmailVerificationCode_UnsuccessResponse() {
        VerificationRequestsPost201Response response = new VerificationRequestsPost201Response();
        response.setSuccess(false);
        Mockito.doReturn(response).when(emailVerificatieApi).verificationRequestsPost(Mockito.any());

        boolean result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertFalse(result);
    }

    @Test
    void requestEmailVerificationCode_NullResponse() {
        Mockito.doReturn(null).when(emailVerificatieApi).verificationRequestsPost(Mockito.any());

        boolean result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertFalse(result);
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
        Mockito.doThrow(exception).when(emailVerificatieApi).verificationRequestsPost(Mockito.any());

        boolean result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertFalse(result);
    }

    @Test
    void requestEmailVerificationCode_Exception() {
        Mockito.doThrow(new RuntimeException("Error message")).when(emailVerificatieApi).verificationRequestsPost(Mockito.any());
        boolean result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertFalse(result);
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
            contact.setPartij(partij);
            contact.persist();
        });

        VerificationRequestsVerifyPost200Response response = new VerificationRequestsVerifyPost200Response();
        response.setVerified(true);
        Mockito.doReturn(response).when(emailVerificatieApi).verificationRequestsVerifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertTrue(result);

        // Verify that the contact was marked as verified
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElse(null);
            Assertions.assertNotNull(contact);
            Assertions.assertNotNull(contact.getGeverifieerdAt());
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
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doReturn(null).when(emailVerificatieApi).verificationRequestsVerifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        boolean result = service.verifieerEmail(request);
        Assertions.assertFalse(result);
    }

    @Test
    void verifieerEmail_ApiResponseVerifiedFalse() {
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

        VerificationRequestsVerifyPost200Response response = new VerificationRequestsVerifyPost200Response();
        response.setVerified(false);
        Mockito.doReturn(response).when(emailVerificatieApi).verificationRequestsVerifyPost(Mockito.any());

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
        Mockito.doThrow(exception).when(emailVerificatieApi).verificationRequestsVerifyPost(Mockito.any());

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
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doThrow(new RuntimeException("Unexpected error")).when(emailVerificatieApi).verificationRequestsVerifyPost(Mockito.any());

        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";
        request.verificatieCode = "123456";

        WebApplicationException exception = Assertions.assertThrows(WebApplicationException.class, () -> {
            service.verifieerEmail(request);
        });
        Assertions.assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), exception.getResponse().getStatus());
    }

}
