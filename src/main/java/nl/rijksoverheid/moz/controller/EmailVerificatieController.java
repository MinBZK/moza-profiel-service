
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.external.clients.basisprofiel.api.BasisprofielApi;
import nl.rijksoverheid.moz.external.clients.email.api.DefaultApi;
import nl.rijksoverheid.moz.external.clients.email.model.VerificationRequestsPostRequest;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "EmailVerificatie", description = "Endpoints voor het verifieren van emails")
public class EmailVerificatieController {

    @Inject
    @RestClient
    BasisprofielApi basisprofielApi;

    @Inject
    @RestClient
    DefaultApi emailVerificatie;


    @POST
    @Path("/emailverificatie/{email}/{verificatiecode}")
    public Response getAfdelingenDienstverlener(@PathParam("email") String email,
                                                @PathParam("verificatiecode") String verificatiecode) {
        //Todo Zet email verified in database
        //Todo Maak connectie met NotifyNL en verifieer email.
        VerificationRequestsPostRequest verificationRequestsPostRequest = new VerificationRequestsPostRequest();
        verificationRequestsPostRequest.setEmail(email);
//        verificationRequestsPostRequest.setApiKey();
//        verificationRequestsPostRequest.setPhoneNumber();
//        verificationRequestsPostRequest.setTemplateId();
//        verificationRequestsPostRequest.setReference();
        emailVerificatie.verificationRequestsPost(verificationRequestsPostRequest);
        return Response.ok(true).build();
    }

}
