package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.external.clients.email.api.DefaultApi;
import nl.rijksoverheid.moz.external.clients.email.model.VerificationRequestsPost201Response;
import nl.rijksoverheid.moz.external.clients.email.model.VerificationRequestsPostRequest;
import nl.rijksoverheid.moz.external.clients.email.model.VerificationRequestsVerifyPostRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

@ApplicationScoped
public class EmailVerificatieService {

    private static final org.jboss.logging.Logger LOG = Logger.getLogger(EmailVerificatieService.class);

    @Inject
    @RestClient
    DefaultApi emailVerificatieApi;

    @ConfigProperty(name = "notifynl.emailverificatie.api-key")
    String apiKey;

    @ConfigProperty(name = "notifynl.emailverificatie.template-id")
    String templateId;

    @ConfigProperty(name = "notifynl.emailverificatie.reference")
    String reference;

    @Transactional
    public boolean verifieerEmail(EmailVerificatieRequest emailVerificatieRequest) {
        Partij partij = Partij.findByIdentificatie(emailVerificatieRequest.identificatieType, emailVerificatieRequest.identificatieNummer);

        if (partij == null) {
            LOG.warnf("Verificatie mislukt: Partij niet gevonden voor %s", emailVerificatieRequest.identificatieNummer);
            return false;
        }

        Contactgegeven contact = partij.getContactgegevens().stream()
                .filter(c -> c.getWaarde().equals(emailVerificatieRequest.email))
                .findFirst()
                .orElse(null);

        if (contact == null || contact.getGeverifieerdAt() != null) {
            LOG.warnf("Verificatie mislukt: Contact niet gevonden of al geverifieerd voor %s", emailVerificatieRequest.email);
            return false;
        }

        VerificationRequestsVerifyPostRequest request = new VerificationRequestsVerifyPostRequest();
        request.setEmail(emailVerificatieRequest.email);
        request.setCode(emailVerificatieRequest.verificatieCode);
        request.setReference(reference);

        try {
            var response = emailVerificatieApi.verificationRequestsVerifyPost(request);

            if (response != null && Boolean.TRUE.equals(response.getVerified())) {
                contact.setGeverifieerdAt(LocalDateTime.now());
                LOG.infof("Email succesvol geverifieerd voor: %s", emailVerificatieRequest.email);
                return true;
            }

            LOG.warnf("NotifyNL gaf geen succes-bevestiging voor %s", emailVerificatieRequest.email);
            return false;

        } catch (WebApplicationException e) {
            String errorBody = e.getResponse().readEntity(String.class);
            LOG.errorf("NotifyNL Verificatie API Error (%d) voor %s: %s",
                    e.getResponse().getStatus(), emailVerificatieRequest.email, errorBody);
            return false;
        } catch (Exception e) {
            LOG.error("Onverwachte fout tijdens verifiëren van email code: " + e.getMessage(), e);
            throw new WebApplicationException("Interne fout bij verwerken email verificatie", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public boolean requestEmailVerificationCode(String email) {
        VerificationRequestsPostRequest request = new VerificationRequestsPostRequest();
        request.setEmail(email);
        request.setApiKey(apiKey);
        request.setTemplateId(templateId);
        request.setReference(reference);

        try {
            VerificationRequestsPost201Response res = emailVerificatieApi.verificationRequestsPost(request);

            if (res != null) {
                boolean success = res.getSuccess();
                if (!success) {
                    LOG.errorf("Email verificatie verzoek mislukt voor email: %s. Response success was false.", email);
                }
                return success;
            }
        } catch (WebApplicationException e) {
            String errorBody = e.getResponse().readEntity(String.class);
            LOG.errorf("NotifyNL API Error (%d) voor %s: %s", e.getResponse().getStatus(), email, errorBody);
            return false;
        } catch (RuntimeException e) {
            LOG.error("Onverwachte fout tijdens aanvragen email verificatie: " + e.getMessage(), e);
            return false;
        }
        return false;
    }
}
