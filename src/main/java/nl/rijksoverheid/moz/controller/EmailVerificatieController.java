
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.dto.response.ProblemDetail;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "EmailVerificatie", description = "Endpoints voor het verifiëren van emails")
public class EmailVerificatieController {

    private static final Logger LOG = Logger.getLogger(EmailVerificatieController.class);

    @Inject
    EmailVerificatieService emailVerificatieService;

    @POST
    @Path("/email-verificaties")
    @Operation(
            summary = "Verifieer een email-adres",
            description = "Verifieert een email-adres aan de hand van een eerder verstuurde verificatiecode"
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Email succesvol geverifieerd"),
            @APIResponse(
                    responseCode = "400",
                    description = "Email verificatie mislukt",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public Response postEmailVerificatie(EmailVerificatieRequest emailVerificatieRequest) {
        boolean succes = emailVerificatieService.verifieerEmail(emailVerificatieRequest);

        if (succes) {
            LOG.info("Email verificatie succesvol");
            return Response.ok().build();
        }
        LOG.warn("Email verificatie mislukt");
        throw new BadRequestException("Email verificatie mislukt");
    }
}
