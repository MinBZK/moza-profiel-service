package nl.rijksoverheid.moz.external.clients.verificatie_service.mock;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import nl.rijksoverheid.moz.external.clients.verificatie_service.api.VerificationControllerApi;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationApplicationRequest;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationRequest;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.quarkus.arc.profile.IfBuildProfile;
import org.jboss.logging.Logger;

import java.util.UUID;

@Alternative
@Priority(1)
@ApplicationScoped
@RestClient
@IfBuildProfile("test")
public class VerificationControllerMock implements VerificationControllerApi {

    private static final org.jboss.logging.Logger LOG = Logger.getLogger(VerificationControllerMock.class);

    @Override
    public String requestPost(VerificationApplicationRequest verificationApplicationRequest) {
        LOG.debug("Mocked request to verification service: " + verificationApplicationRequest);
        return UUID.randomUUID().toString();
    }

    @Override
    public VerificationResponse verifyPost(VerificationRequest verificationRequest) {
        LOG.debug("Mocked verification request: " + verificationRequest);
        VerificationResponse response = new VerificationResponse();
        response.setSuccess(true);
        return response;
    }
}
