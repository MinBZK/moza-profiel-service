package nl.rijksoverheid.moz.controller;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.api.generated.api.EmailVerificatieApi;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieRequest;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.jboss.logging.Logger;

/**
 * REST controller voor e-mailverificatie. Contract-first: implementeert de uit
 * META-INF/openapi.yaml gegenereerde {@link EmailVerificatieApi}, die de paden, HTTP-methodes,
 * mediatypes en de validatie van de body-parameter draagt. Herhaal ze hier niet: één
 * JAX-RS-annotatie op een implementatiemethode laat álle annotaties van de interface voor
 * die methode vervallen (JAX-RS 3.1 §3.6), inclusief {@code @Path}. De gedocumenteerde route
 * geeft dan een 404 die niet te onderscheiden is van "niet gevonden", en de methode herbindt
 * zich stilzwijgend aan het pad op klasseniveau. Een parameterconstraint opnieuw declareren
 * is een harde fout: dan start de applicatie niet meer (HV000151).
 */
public class EmailVerificatieController implements EmailVerificatieApi {

    private static final Logger LOG = Logger.getLogger(EmailVerificatieController.class);

    private final EmailVerificatieService emailVerificatieService;

    public EmailVerificatieController(EmailVerificatieService emailVerificatieService) {
        this.emailVerificatieService = emailVerificatieService;
    }

    @Override
    public Response vraagEmailVerificatieCodeAan(EmailVerificatieCodeAanvraagRequest emailVerificatieCodeAanvraagRequest) {
        int result = emailVerificatieService.vraagEmailVerificatieCodeAan(emailVerificatieCodeAanvraagRequest);

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

    @Override
    public Response verifieerEmail(EmailVerificatieRequest emailVerificatieRequest) {
        boolean succes = emailVerificatieService.verifieerEmail(emailVerificatieRequest);

        if (succes) {
            LOG.info("Email verificatie succesvol");
            return Response.ok().build();
        }

        LOG.warn("Email verificatie mislukt");
        throw HttpProblem.valueOf(Response.Status.BAD_REQUEST, "Email verificatie mislukt");
    }
}
