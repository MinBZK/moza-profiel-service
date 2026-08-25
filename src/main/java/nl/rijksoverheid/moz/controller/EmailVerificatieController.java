
package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.StatusCode;
import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.Logboek;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieRequest;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.HashHelper;
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
    private final LogboekContext logboekContext;
    private final HashHelper hashHelper;

    public EmailVerificatieController(
            EmailVerificatieService emailVerificatieService,
            LogboekContext logboekContext,
            HashHelper hashHelper) {
        this.emailVerificatieService = emailVerificatieService;
        this.logboekContext = logboekContext;
        this.hashHelper = hashHelper;
    }

    @POST
    @Path("/emailverificatie/code")
    @RequireBody
    @Logboek(name = "vraagEmailVerificatieCodeAan", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-400")
    public Response vraagEmailVerificatieCodeAan(@Valid EmailVerificatieCodeAanvraagRequest aanvraag) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(aanvraag.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(aanvraag.getIdentificatieType()));

        int result = emailVerificatieService.vraagEmailVerificatieCodeAan(aanvraag);

        if (result == Response.Status.OK.getStatusCode()) {
            logboekContext.setStatus(StatusCode.OK);
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
    @Logboek(name = "verifieerEmail", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-410")
    public Response verifieerEmail(@Valid EmailVerificatieRequest emailVerificatieRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(emailVerificatieRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(emailVerificatieRequest.getIdentificatieType()));

        boolean succes = emailVerificatieService.verifieerEmail(emailVerificatieRequest);

        if (succes) {
            logboekContext.setStatus(StatusCode.OK);
            LOG.info("Email verificatie succesvol");
            return Response.ok().build();
        } else {
            LOG.warn("Email verificatie mislukt");
            throw HttpProblem.valueOf(Response.Status.BAD_REQUEST, "Email verificatie mislukt");
        }
    }
}
