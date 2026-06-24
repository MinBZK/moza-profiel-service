package nl.rijksoverheid.moz.controller;

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
import nl.rijksoverheid.moz.api.generated.model.DienstRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.mapper.DienstMapper;
import nl.rijksoverheid.moz.services.DienstverlenerService;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;

/**
 * REST controller voor dienstverleners. Contract-first (#651): DTO's gegenereerd uit
 * META-INF/openapi.yaml. Concrete resource (Quarkus REST ondersteunt geen interface-resources).
 */
@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DienstverlenerController {

    private static final Logger LOG = Logger.getLogger(DienstverlenerController.class);

    private final DienstverlenerService dienstverlenerService;
    private final DienstMapper dienstMapper;

    public DienstverlenerController(DienstverlenerService dienstverlenerService, DienstMapper dienstMapper) {
        this.dienstverlenerService = dienstverlenerService;
        this.dienstMapper = dienstMapper;
    }

    @GET
    @Path("/dienstverlener/{naam}")
    public Response getDienstenDienstverlener(@PathParam("naam") String naam) {
        Dienstverlener dv = dienstverlenerService.getDienstverlener(naam);

        if (dv == null) {
            LOG.warn("Dienstverlener niet gevonden");
            throw Problems.notFound("Dienstverlener niet gevonden", "Geen dienstverlener gevonden met de opgegeven naam.");
        }

        DienstverlenerResponse response = dienstMapper.toDienstverlenerResponse(dv, dienstverlenerService.getDienstenVoorDienstverlener(dv));
        LOG.info("Dienstverlener opgehaald");
        return Response.ok(response).build();
    }

    @POST
    @Path("/dienstverlener/")
    @Transactional
    @RequireBody
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
        DienstverlenerResponse body = dienstMapper.toDienstverlenerResponse(created, List.of());
        return Response.created(uri).entity(body).build();
    }

    @POST
    @Path("/dienstverlener/{dienstverlenerNaam}/diensten")
    @Transactional
    @RequireBody
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
        return Response.created(uri).entity(dienstMapper.toDienstResponse(created)).build();
    }
}
