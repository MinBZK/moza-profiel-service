package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.exception.TechnicalException;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.external.clients.verificatie_service.api.VerificationControllerApi;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationApplicationRequest;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationRequest;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class EmailVerificatieService {

    private static final Logger LOG = Logger.getLogger(EmailVerificatieService.class);

    @Inject
    @RestClient
    VerificationControllerApi emailVerificatieApi;

    @Inject
    VerificatieServiceGuard verificatieServiceGuard;

    @ConfigProperty(name = "notifynl.emailverificatie.api-key")
    String apiKey;

    @ConfigProperty(name = "notifynl.emailverificatie.template-id")
    String templateId;

    @Transactional
    public boolean verifieerEmail(EmailVerificatieRequest emailVerificatieRequest) {
        Partij partij = Partij.findByIdentificatie(emailVerificatieRequest.identificatieType, emailVerificatieRequest.identificatieNummer);

        if (partij == null) {
            LOG.warn("Verificatie mislukt: Partij niet gevonden");
            return false;
        }

        Contactgegeven contact = partij.getContactgegevens().stream()
                .filter(c -> c.getType() == ContactType.Email && c.getWaarde().equalsIgnoreCase(emailVerificatieRequest.email))
                .findFirst()
                .orElse(null);

        if (contact == null || contact.getGeverifieerdAt() != null) {
            LOG.warn("Verificatie mislukt: Contact niet gevonden of al geverifieerd");
            return false;
        }

        VerificationRequest request = new VerificationRequest();
        request.setReferenceId(contact.getVerificatieReferentieId());
        request.setCode(emailVerificatieRequest.verificatieCode);

        try {
            var response = verificatieServiceGuard.get().call(() -> emailVerificatieApi.verifyPost(request), VerificationResponse.class);

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                contact.setGeverifieerdAt(Instant.now());
                contact.setIsGeverifieerd(true);
                contact.setVerificatieReferentieId(null);
                LOG.info("Email succesvol geverifieerd");
                return true;
            }

            LOG.warn("NotifyNL gaf geen succes-bevestiging");
            return false;

        } catch (CircuitBreakerOpenException e) {
            LOG.error("Verificatie-service circuit breaker open, verificatie overgeslagen");
            return false;
        } catch (WebApplicationException e) {
            String errorBody = e.getResponse().readEntity(String.class);
            LOG.errorf("NotifyNL Verificatie API Error (%d): %s",
                    e.getResponse().getStatus(), errorBody);
            return false;
        } catch (Exception e) {
            LOG.error("Onverwachte fout tijdens verifiëren van email code: " + e.getMessage(), e);
            throw new TechnicalException("Interne fout bij verwerken email verificatie", e);
        }
    }

    @Transactional
    public int vraagEmailVerificatieCodeAan(EmailVerificatieCodeAanvraagRequest aanvraag) {
        Partij partij = Partij.findByIdentificatie(aanvraag.identificatieType, aanvraag.identificatieNummer);

        if (partij == null) {
            LOG.warn("Verificatie code aanvraag mislukt: Partij niet gevonden");
            return Response.Status.NOT_FOUND.getStatusCode();
        }

        Contactgegeven contact = partij.getContactgegevens().stream()
                .filter(c -> c.getType() == ContactType.Email && c.getWaarde().equalsIgnoreCase(aanvraag.email))
                .findFirst()
                .orElse(null);

        if (contact == null) {
            LOG.warn("Verificatie code aanvraag mislukt: Contact niet gevonden");
            return Response.Status.NOT_FOUND.getStatusCode();
        }

        String referenceId = requestEmailVerificationCode(aanvraag.email);
        if (referenceId == null) {
            return Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
        }

        contact.setVerificatieReferentieId(referenceId);
        contact.setGeverifieerdAt(null);
        contact.setIsGeverifieerd(false);
        return Response.Status.OK.getStatusCode();
    }

    public String requestEmailVerificationCode(String email) {
        VerificationApplicationRequest verificationApplicationRequest = new VerificationApplicationRequest();
        verificationApplicationRequest.setApiKey(apiKey);
        verificationApplicationRequest.setTemplateId(templateId);
        verificationApplicationRequest.setEmail(email);

        try {
            String referenceId = verificatieServiceGuard.get().call(() -> emailVerificatieApi.requestPost(verificationApplicationRequest), String.class);
            if (referenceId != null) {
                return referenceId;
            }
            LOG.error("Email verificatie verzoek mislukt");
            return null;
        } catch (CircuitBreakerOpenException e) {
            LOG.error("Verificatie-service circuit breaker open, verificatie code aanvraag overgeslagen");
            return null;
        } catch (WebApplicationException e) {
            String errorBody = e.getResponse().readEntity(String.class);
            LOG.errorf("NotifyNL API Error (%d): %s", e.getResponse().getStatus(), errorBody);
            return null;
        } catch (Exception e) {
            LOG.error("Onverwachte fout tijdens aanvragen email verificatie: " + e.getMessage(), e);
            return null;
        }
    }
}
