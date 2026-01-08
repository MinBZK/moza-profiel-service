
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "EmailVerificatie", description = "Endpoints voor het verifieren van emails")
public class EmailVerificatieController {

    @Inject
    EmailVerificatieService emailVerificatieService;

    @POST
    @Path("/emailverificatie")
    public Response postEmailVerificatie(EmailVerificatieRequest emailVerificatieRequest) {
        boolean succes = emailVerificatieService.verifieerEmail(emailVerificatieRequest);

        if (succes) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
