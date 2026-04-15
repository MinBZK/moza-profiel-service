package nl.rijksoverheid.moz.clients.mock.verificatie_service;

import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.UUID;

import nl.rijksoverheid.moz.external.clients.verificatie_service.api.VerificationControllerApi;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationApplicationRequest;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationRequest;
import nl.rijksoverheid.moz.external.clients.verificatie_service.model.VerificationResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
@RestClient
@Alternative
@Priority(1)
@UnlessBuildProfile("prod")
public class MockVerificationControllerApi implements VerificationControllerApi {

    @Override
    public String requestPost(VerificationApplicationRequest verificationApplicationRequest) {
        return UUID.randomUUID().toString();
    }

    @Override
    public VerificationResponse verifyPost(VerificationRequest verificationRequest) {
        VerificationResponse response = new VerificationResponse();
        response.setSuccess(true);
        return response;
    }

}
