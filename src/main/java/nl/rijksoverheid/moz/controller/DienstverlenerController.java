
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.AfdelingRequest;
import nl.rijksoverheid.moz.dto.request.DienstverlenerRequest;
import nl.rijksoverheid.moz.dto.response.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.services.DienstverlenerService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;


@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dienstverlener", description = "Endpoints voor het beheren van dienstverleners en afdelingen")
public class DienstverlenerController {

    @Inject
    DienstverlenerService dienstverlenerService;

    @GET
    @Path("/dienstverlener/{naam}")
    public Response getAfdelingenDienstverlener(@PathParam("naam") String naam) {

        Dienstverlener dv = dienstverlenerService.getAfdelingenVoorDienstverlener(naam);

        if (dv == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        DienstverlenerResponse response = new DienstverlenerResponse(dv);
        return Response.ok(response).build();
    }

    @POST
    @Path("/dienstverlener/")
    @Transactional
    public Response addDienstverlener(
            DienstverlenerRequest dienstverlenerRequest) {

        dienstverlenerService.addDienstverlener(dienstverlenerRequest);

        URI uri = URI.create(String.format("/dienstverlener/%s", dienstverlenerRequest.naam));
        return Response.created(uri).build();
    }

    @POST
    @Path("/dienstverlener/{DienstverlenerNaam}/afdelingen")
    @Operation(
            summary = "Voegt een afdeling toe aan een dienstverlener",
            description = "Voegt een nieuwe afdeling toe met beschrijving"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Afdeling succesvol toegevoegd"),
            @APIResponse(responseCode = "404", description = "Dienstverlener niet gevonden")
    })
    public Response addAfdelingToDienstverlener(
            @PathParam("DienstverlenerNaam") String dienstverlenerNaam,
            AfdelingRequest request
    ) {
        var afdeling = dienstverlenerService.addAfdelingToDienstverlener(dienstverlenerNaam, request);
        return Response.status(Response.Status.CREATED).entity(afdeling).build();
    }


}
