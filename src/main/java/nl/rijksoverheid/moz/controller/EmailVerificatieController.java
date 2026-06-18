
package nl.rijksoverheid.moz.controller;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.common.ApiResponseDescriptions;
import nl.rijksoverheid.moz.common.MediaTypes;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.Problems;
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
@APIResponse(
        responseCode = "500",
        description = ApiResponseDescriptions.INTERNAL_SERVER_ERROR,
        content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
)
public class EmailVerificatieController {

    private static final Logger LOG = Logger.getLogger(EmailVerificatieController.class);

    @Inject
    EmailVerificatieService emailVerificatieService;

    @POST
    @Path("/emailverificatie/code")
    @RequireBody
    @Operation(
            summary = "(Opnieuw) aanvragen voor een code van een (al geverifieerde) mail adres",
            description = "Vraagt een email verificatie code aan. " +
                    "Let op, bij het aanmaken van een profiel wordt al een email verificatie code aangevraagd. " +
                    "Dit is voor het opnieuw aanvragen van een code."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Email verificatie code aanvraag succesvol"),
            @APIResponse(
                    responseCode = "400",
                    description = ApiResponseDescriptions.BAD_REQUEST_BODY,
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Partij of Contactgegeven niet gevonden",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            ),
            @APIResponse(
                    responseCode = "503",
                    description = "Service tijdelijk niet beschikbaar",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    public Response postEmailVerificatieCodeAanvraag(EmailVerificatieCodeAanvraagRequest aanvraag) {
        int result = emailVerificatieService.vraagEmailVerificatieCodeAan(aanvraag);

        if (result == Response.Status.OK.getStatusCode()) {
            LOG.info("Email verificatie code aanvraag succesvol");
            return Response.ok().build();
        }
        else if (result == Response.Status.NOT_FOUND.getStatusCode()) {
            LOG.warn("Partij of Contactgegeven niet gevonden");
            throw Problems.notFound(
                    "Partij of contactgegeven niet gevonden",
                    "Geen partij of contactgegeven gevonden voor de opgegeven gegevens.");
        } else {
            LOG.warn("NotifyNL API onbereikbaar");
            throw HttpProblem.builder()
                    .withStatus(Response.Status.SERVICE_UNAVAILABLE)
                    .withDetail("Service tijdelijk niet beschikbaar. Probeer het later opnieuw.")
                    .withHeader("Retry-After", "30")
                    .build();
        }
    }

    @POST
    @Path("/emailverificatie")
    @RequireBody
    @Operation(
            summary = "Verifieer een email met een verificatie code",
            description = "Verifieert een email adres aan de hand van de eerder aangevraagde verificatie code."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Email verificatie succesvol"),
            @APIResponse(
                    responseCode = "400",
                    description = "Email verificatie mislukt",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    public Response postEmailVerificatie(EmailVerificatieRequest emailVerificatieRequest) {
        boolean succes = emailVerificatieService.verifieerEmail(emailVerificatieRequest);

        if (succes) {
            LOG.info("Email verificatie succesvol");
            return Response.ok().build();
        } else {
            LOG.warn("Email verificatie mislukt");
            throw HttpProblem.valueOf(Response.Status.BAD_REQUEST, "Email verificatie mislukt");
        }
    }
}
