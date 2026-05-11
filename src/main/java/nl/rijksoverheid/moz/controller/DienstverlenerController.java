
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.dto.response.DienstverlenerResponse;
import nl.rijksoverheid.moz.dto.response.ProblemDetail;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.services.DienstverlenerService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;


@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dienstverlener", description = "Endpoints voor het beheren van dienstverleners en diensten")
public class DienstverlenerController {

    private static final Logger LOG = Logger.getLogger(DienstverlenerController.class);

    @Inject
    DienstverlenerService dienstverlenerService;

    @GET
    @Path("/dienstverleners/{naam}")
    @Operation(
            summary = "Ophalen diensten van een dienstverlener",
            description = "Haalt de diensten op die door een dienstverlener worden aangeboden"
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Dienstverlener gevonden",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DienstverlenerResponse.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Dienstverlener niet gevonden",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public Response getDienstenDienstverlener(@PathParam("naam") String naam) {

        Dienstverlener dv = dienstverlenerService.getDienstenVoorDienstverlener(naam);

        if (dv == null) {
            LOG.warn("Dienstverlener niet gevonden");
            throw new NotFoundException("Dienstverlener met naam '" + naam + "' niet gevonden");
        }

        DienstverlenerResponse response = new DienstverlenerResponse(dv);
        LOG.info("Dienstverlener opgehaald");
        return Response.ok(response).build();
    }

    @POST
    @Path("/dienstverleners")
    @Transactional
    @Operation(
            summary = "Toevoegen nieuwe dienstverlener",
            description = "Maakt een nieuwe dienstverlener aan"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Dienstverlener succesvol aangemaakt"),
            @APIResponse(
                    responseCode = "400",
                    description = "Ongeldige request body",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public Response addDienstverlener(
            DienstverlenerRequest dienstverlenerRequest) {
        if (dienstverlenerRequest == null) {
            LOG.warn("Request body mag niet leeg zijn bij addDienstverlener");
            throw new BadRequestException("Request body mag niet leeg zijn");
        }
        dienstverlenerService.addDienstverlener(dienstverlenerRequest);

        LOG.info("Dienstverlener toegevoegd");
        URI uri = URI.create(String.format("/dienstverleners/%s", dienstverlenerRequest.naam));
        return Response.created(uri).build();
    }

    @POST
    @Path("/dienstverleners/{dienstverlener-naam}/diensten")
    @Operation(
            summary = "Voegt een dienst toe aan een dienstverlener",
            description = "Voegt een nieuwe dienst toe met beschrijving"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Dienst succesvol toegevoegd"),
            @APIResponse(
                    responseCode = "404",
                    description = "Dienstverlener niet gevonden",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Ongeldige request body",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public Response addDienstToDienstverlener(
            @PathParam("dienstverlener-naam") String dienstverlenerNaam,
            DienstRequest request
    ) {
        if (request == null) {
            LOG.warn("Request body mag niet leeg zijn bij addDienstToDienstverlener");
            throw new BadRequestException("Request body mag niet leeg zijn");
        }
        dienstverlenerService.addDienstToDienstverlener(dienstverlenerNaam, request);
        LOG.info("Dienst toegevoegd aan dienstverlener");
        URI uri = URI.create(String.format("/dienstverleners/%s", dienstverlenerNaam));
        return Response.created(uri).build();
    }
}
