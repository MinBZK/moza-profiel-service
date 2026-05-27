
package nl.rijksoverheid.moz.controller;

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
import nl.rijksoverheid.moz.dto.request.DienstRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.dto.response.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.services.DienstverlenerService;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
    @Path("/dienstverlener/{naam}")
    @Operation(
            summary = "Vraagt gegevens van dienstverlener",
            description = "Geeft gegevens van gevraagde Dienstverlener terug"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Dienstverlener succesvol opgehaald"),
            @APIResponse(responseCode = "404", description = "Dienstverlener niet gevonden")
    })
    public Response getDienstenDienstverlener(@PathParam("naam") String naam) {

        Dienstverlener dv = dienstverlenerService.getDienstverlener(naam);

        if (dv == null) {
            LOG.warn("Dienstverlener niet gevonden");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DienstverlenerResponse response = new DienstverlenerResponse(dv, dienstverlenerService.getDienstenVoorDienstverlener(dv));
        LOG.info("Dienstverlener opgehaald");
        return Response.ok(response).build();
    }

    @POST
    @Path("/dienstverlener/")
    @Transactional
    @Operation(
            summary = "Voegt een dienstverlener toe",
            description = "Voegt een nieuwe dienstverlener toe met optionele beschrijving"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Dienstverlener succesvol toegevoegd"),
            @APIResponse(responseCode = "400", description = "Request body mag niet leeg zijn")
    })
    public Response addDienstverlener(
            @Valid DienstverlenerRequest dienstverlenerRequest) {
        if (dienstverlenerRequest == null) {
            LOG.warn("Request body mag niet leeg zijn bij addDienstverlener");
            return Response.status(Response.Status.BAD_REQUEST).entity("Request body mag niet leeg zijn").build();
        }
        dienstverlenerService.addDienstverlener(dienstverlenerRequest);

        LOG.info("Dienstverlener toegevoegd");
        URI uri = URI.create(String.format("/dienstverlener/%s", dienstverlenerRequest.naam));
        return Response.created(uri).build();
    }

    @POST
    @Path("/dienstverlener/{dienstverlenerNaam}/diensten")
    @Operation(
            summary = "Voegt een dienst toe aan een dienstverlener",
            description = "Voegt een nieuwe dienst toe met beschrijving"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Dienst succesvol toegevoegd"),
            @APIResponse(responseCode = "400", description = "Request body mag niet leeg zijn")
    })
    public Response addDienstToDienstverlener(
            @PathParam("dienstverlenerNaam") String dienstverlenerNaam,
            @Valid DienstRequest request
    ) {
        if (request == null) {
            LOG.warn("Request body mag niet leeg zijn bij addDienstToDienstverlener");
            return Response.status(Response.Status.BAD_REQUEST).entity("Request body mag niet leeg zijn").build();
        }
        dienstverlenerService.addDienstToDienstverlener(dienstverlenerNaam, request);
        LOG.info("Dienst toegevoegd aan dienstverlener");
        URI uri = URI.create(String.format("/dienstverlener/%s", dienstverlenerNaam));
        return Response.created(uri).build();
    }
}
