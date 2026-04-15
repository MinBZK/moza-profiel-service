package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.clients.verificatie_service.api.VerificationControllerApi;
import nl.rijksoverheid.moz.clients.verificatie_service.model.VerificationApplicationRequest;
import nl.rijksoverheid.moz.clients.verificatie_service.model.VerificationRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

@ApplicationScoped
public class EmailVerificatieService {

    private static final org.jboss.logging.Logger LOG = Logger.getLogger(EmailVerificatieService.class);

    @Inject
    @RestClient
    VerificationControllerApi emailVerificatieApi;

    @ConfigProperty(name = "notifynl.emailverificatie.api-key")
    String apiKey;

    @ConfigProperty(name = "notifynl.emailverificatie.template-id")
    String templateId;

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

        VerificationRequest request = new VerificationRequest();
        request.setReferenceId(contact.getVerificatieReferentieId());
        request.setCode(emailVerificatieRequest.verificatieCode);
        try {
            var response = emailVerificatieApi.verifyPost(request);

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                contact.setGeverifieerdAt(LocalDateTime.now());
                contact.setVerificatieReferentieId(null);
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

    public String requestEmailVerificationCode(String email) {

        VerificationApplicationRequest verificationApplicationRequest = new VerificationApplicationRequest();
        verificationApplicationRequest.setApiKey(apiKey);
        verificationApplicationRequest.setTemplateId(templateId);
        verificationApplicationRequest.setEmail(email);


        try {
            String referenceId = emailVerificatieApi.requestPost(verificationApplicationRequest);
            if (referenceId != null) {
                return referenceId;
            }
            LOG.errorf("Email verificatie verzoek mislukt voor email: %s. Response success was false.", email);
        } catch (WebApplicationException e) {
            String errorBody = e.getResponse().readEntity(String.class);
            LOG.errorf("NotifyNL API Error (%d) voor %s: %s", e.getResponse().getStatus(), email, errorBody);
        } catch (RuntimeException e) {
            LOG.error("Onverwachte fout tijdens aanvragen email verificatie: " + e.getMessage(), e);
        }
        return null;
    }
}
