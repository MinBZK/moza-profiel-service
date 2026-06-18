
package nl.rijksoverheid.moz.controller;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import nl.rijksoverheid.moz.common.ApiResponseDescriptions;
import nl.rijksoverheid.moz.common.MediaTypes;
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.dto.response.DienstResponse;
import nl.rijksoverheid.moz.dto.response.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.services.DienstverlenerService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;


@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dienstverlener", description = "Endpoints voor het beheren van dienstverleners en diensten")
@APIResponse(
        responseCode = "500",
        description = ApiResponseDescriptions.INTERNAL_SERVER_ERROR,
        content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
)
public class DienstverlenerController {

    private static final Logger LOG = Logger.getLogger(DienstverlenerController.class);

    @Inject
    DienstverlenerService dienstverlenerService;

    @GET
    @Path("/dienstverlener/{naam}")
    @Operation(
            summary = "Vraagt gegevens van dienstverlener",
            description = "Geeft gegevens van gevraagde dienstverlener terug, inclusief de aangesloten diensten."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Dienstverlener succesvol opgehaald",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DienstverlenerResponse.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Dienstverlener niet gevonden",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    public Response getDienstenDienstverlener(@PathParam("naam") String naam) {
        Dienstverlener dv = dienstverlenerService.getDienstverlener(naam);

        if (dv == null) {
            LOG.warn("Dienstverlener niet gevonden");
            throw Problems.notFound("Dienstverlener niet gevonden", "Geen dienstverlener gevonden met de opgegeven naam.");
        }

        DienstverlenerResponse response = new DienstverlenerResponse(dv, dienstverlenerService.getDienstenVoorDienstverlener(dv));
        LOG.info("Dienstverlener opgehaald");
        return Response.ok(response).build();
    }

    @POST
    @Path("/dienstverlener/")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Voegt een dienstverlener toe",
            description = "Voegt een nieuwe dienstverlener toe met optionele beschrijving."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Dienstverlener succesvol toegevoegd",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DienstverlenerResponse.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = ApiResponseDescriptions.BAD_REQUEST_BODY,
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            ),
            @APIResponse(
                    responseCode = "409",
                    description = "Dienstverlener bestaat al met conflicterende waarden",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    public Response addDienstverlener(
            @Valid DienstverlenerRequest dienstverlenerRequest) {
        if (dienstverlenerRequest == null) {
            LOG.warn("Request body mag niet leeg zijn bij addDienstverlener");
            throw Problems.missingBody();
        }
        Dienstverlener created = dienstverlenerService.addDienstverlener(dienstverlenerRequest);

        LOG.info("Dienstverlener toegevoegd");
        URI uri = UriBuilder.fromResource(DienstverlenerController.class)
                .path("dienstverlener").path("{naam}")
                .build(created.getNaam());
        DienstverlenerResponse body = new DienstverlenerResponse(created, List.of());
        return Response.created(uri).entity(body).build();
    }

    @POST
    @Path("/dienstverlener/{dienstverlenerNaam}/diensten")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Voegt een dienst toe aan een dienstverlener",
            description = "Voegt een nieuwe dienst toe met beschrijving aan een bestaande dienstverlener."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Dienst succesvol toegevoegd",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DienstResponse.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = ApiResponseDescriptions.BAD_REQUEST_BODY,
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            ),
            @APIResponse(
                    responseCode = "409",
                    description = "Dienst bestaat al met een andere beschrijving",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    public Response addDienstToDienstverlener(
            @PathParam("dienstverlenerNaam") String dienstverlenerNaam,
            @Valid DienstRequest request
    ) {
        if (request == null) {
            LOG.warn("Request body mag niet leeg zijn bij addDienstToDienstverlener");
            throw Problems.missingBody();
        }
        Dienst created = dienstverlenerService.addDienstToDienstverlener(dienstverlenerNaam, request);
        LOG.info("Dienst toegevoegd aan dienstverlener");
        URI uri = UriBuilder.fromResource(DienstverlenerController.class)
                .path("dienstverlener").path("{dienstverlenerNaam}").path("diensten").path("{id}")
                .build(dienstverlenerNaam, created.id);
        return Response.created(uri).entity(new DienstResponse(created)).build();
    }
}
