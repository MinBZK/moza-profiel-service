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
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.external.clients.verificatie_service.api.VerificationControllerApi;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

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

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "123456789");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElse(null);
            Assertions.assertNotNull(contact);
            Assertions.assertNotNull(contact.getGeverifieerdAt());
            Assertions.assertTrue(contact.isIsGeverifieerd());
            Assertions.assertNull(contact.getVerificatieReferentieId());
        });
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
            contact.setGeverifieerdAt(Instant.now());
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
            Assertions.assertFalse(contact.isIsGeverifieerd());
        });
    }

    @Test
    void vraagEmailVerificatieCodeAan_PartijNotFound() {
        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "123456789";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result);
    }

    @Test
    void requestEmailVerificationCode_WebApplicationException() {
        WebApplicationException exception = new WebApplicationException(mockErrorResponse(400, "Error message"));
        Mockito.doThrow(exception).when(emailVerificatieApi).requestPost(Mockito.any());

        String result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertNull(result);
    }

    @Test
    void requestEmailVerificationCode_GenericException() {
        Mockito.doThrow(new RuntimeException("boom")).when(emailVerificatieApi).requestPost(Mockito.any());
        String result = service.requestEmailVerificationCode("email@email.com");
        Assertions.assertNull(result);
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
    void verifieerEmail_ApiResponseNull() {
        seedPartijWithUnverifiedContact("111111101");
        Mockito.doReturn(null).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = makeVerifyRequest("111111101");
        Assertions.assertFalse(service.verifieerEmail(request));
    }

    @Test
    void verifieerEmail_ApiResponseSuccessFalse() {
        seedPartijWithUnverifiedContact("111111102");
        nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse response =
                new nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse();
        response.setSuccess(false);
        Mockito.doReturn(response).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = makeVerifyRequest("111111102");
        Assertions.assertFalse(service.verifieerEmail(request));
    }

    @Test
    void verifieerEmail_WebApplicationException() {
        seedPartijWithUnverifiedContact("111111103");
        Mockito.doThrow(new WebApplicationException(mockErrorResponse(400, "Error")))
                .when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = makeVerifyRequest("111111103");
        Assertions.assertFalse(service.verifieerEmail(request));
    }

    @Test
    void verifieerEmail_GenericException_Throws500() {
        seedPartijWithUnverifiedContact("111111104");
        Mockito.doThrow(new RuntimeException("boom")).when(emailVerificatieApi).verifyPost(Mockito.any());

        EmailVerificatieRequest request = makeVerifyRequest("111111104");
        WebApplicationException ex = Assertions.assertThrows(WebApplicationException.class,
                () -> service.verifieerEmail(request));
        Assertions.assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void vraagEmailVerificatieCodeAan_ContactNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111105"));
            partij.persist();
        });

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "111111105";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result);
    }

    @Test
    void vraagEmailVerificatieCodeAan_AlreadyVerifiedResetsState() {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "111111106"));
            partij.persist();
            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setGeverifieerdAt(Instant.now());
            contact.setIsGeverifieerd(true);
            contact.setPartij(partij);
            contact.persist();
        });

        Mockito.doReturn("new-ref-id").when(emailVerificatieApi).requestPost(Mockito.any());

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "111111106";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.OK.getStatusCode(), result);

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findByIdentificatie(IdentificatieType.BSN, "111111106");
            Contactgegeven contact = partij.getContactgegevens().stream()
                    .filter(c -> c.getWaarde().equals("test@test.com"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertNull(contact.getGeverifieerdAt());
            Assertions.assertFalse(contact.isIsGeverifieerd());
        });
    }

    @Test
    void vraagEmailVerificatieCodeAan_ExternalServiceFails() {
        seedPartijWithUnverifiedContact("111111107");
        Mockito.doReturn(null).when(emailVerificatieApi).requestPost(Mockito.any());

        EmailVerificatieCodeAanvraagRequest request = new EmailVerificatieCodeAanvraagRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = "111111107";
        request.email = "test@test.com";

        int result = service.vraagEmailVerificatieCodeAan(request);
        Assertions.assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), result);
    }

    private static Response mockErrorResponse(int status, String body) {
        Response mockResponse = Mockito.mock(Response.class);
        Response.StatusType mockStatusType = Mockito.mock(Response.StatusType.class);
        Mockito.when(mockStatusType.getStatusCode()).thenReturn(status);
        Mockito.when(mockResponse.getStatusInfo()).thenReturn(mockStatusType);
        Mockito.when(mockResponse.getStatus()).thenReturn(status);
        Mockito.when(mockResponse.readEntity(String.class)).thenReturn(body);
        return mockResponse;
    }

    private void seedPartijWithUnverifiedContact(String bsn) {
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, bsn));
            partij.persist();
            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Email);
            contact.setWaarde("test@test.com");
            contact.setVerificatieReferentieId("ref");
            contact.setPartij(partij);
            contact.persist();
        });
    }

    private EmailVerificatieRequest makeVerifyRequest(String bsn) {
        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieType = IdentificatieType.BSN;
        request.identificatieNummer = bsn;
        request.email = "test@test.com";
        request.verificatieCode = "123456";
        return request;
    }
}
