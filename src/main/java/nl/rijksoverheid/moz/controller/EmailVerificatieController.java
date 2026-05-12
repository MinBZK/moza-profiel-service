
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
    @Path("/emailverificatie/code")
    @Operation(
            summary = "(Opnieuw) aanvragen voor een code van een (al geverifieerde) mail adres",
            description = "Vraagt een email verificatie code aan. " +
                    "Let op, bij het aanmaken van een profiel wordt al een email verificatie code aangevraagd. " +
                    "Dit is voor het opnieuw aanvragen van een code."
    )
    public Response postEmailVerificatieCodeAanvraag(EmailVerificatieCodeAanvraagRequest aanvraag) {
        boolean succes = emailVerificatieService.vraagEmailVerificatieCodeAan(aanvraag);

        if (succes) {
            LOG.info("Email verificatie code aanvraag succesvol");
            return Response.ok().build();
        } else {
            LOG.warn("Email verificatie code aanvraag mislukt");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @POST
    @Path("/emailverificatie")
    public Response postEmailVerificatie(EmailVerificatieRequest emailVerificatieRequest) {
        boolean succes = emailVerificatieService.verifieerEmail(emailVerificatieRequest);

        if (succes) {
            LOG.info("Email verificatie succesvol");
            return Response.ok().build();
        } else {
            LOG.warn("Email verificatie mislukt");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
