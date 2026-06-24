
package nl.rijksoverheid.moz.controller;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieRequest;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.jboss.logging.Logger;

/**
 * REST controller voor e-mailverificatie. Contract-first (#651): DTO's gegenereerd uit
 * META-INF/openapi.yaml. Concrete resource (Quarkus REST ondersteunt geen interface-resources).
 */
@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmailVerificatieController {

    private static final Logger LOG = Logger.getLogger(EmailVerificatieController.class);

    private final EmailVerificatieService emailVerificatieService;

    public EmailVerificatieController(EmailVerificatieService emailVerificatieService) {
        this.emailVerificatieService = emailVerificatieService;
    }

    @POST
    @Path("/emailverificatie/code")
    @RequireBody
    public Response vraagEmailVerificatieCodeAan(EmailVerificatieCodeAanvraagRequest aanvraag) {
        int result = emailVerificatieService.vraagEmailVerificatieCodeAan(aanvraag);

        if (result == Response.Status.OK.getStatusCode()) {
            LOG.info("Email verificatie code aanvraag succesvol");
            return Response.ok().build();
        } else if (result == Response.Status.NOT_FOUND.getStatusCode()) {
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
    public Response verifieerEmail(EmailVerificatieRequest emailVerificatieRequest) {
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
