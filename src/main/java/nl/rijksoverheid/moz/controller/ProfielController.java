
package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.StatusCode;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.Logboek;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenResponse;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijBulkRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.api.generated.model.TeVerwijderenOpRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurResponse;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.HashHelper;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import nl.rijksoverheid.moz.services.PartijService;
import nl.rijksoverheid.moz.services.PartijService.AddContactgegevenResult;
import nl.rijksoverheid.moz.services.PartijService.AddVoorkeurResult;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller voor partijen. Contract-first (#651): de request/response-DTO's
 * worden uit {@code META-INF/openapi.yaml} gegenereerd ({@code api.generated.model}).
 * Er worden geen JAX-RS interfaces gegenereerd ({@code generateApis=false}): Quarkus REST
 * (RESTEasy Reactive) ondersteunt geen server-resources via een interface, want dan gaat
 * de parameter-binding verloren. Deze controller is dus een concrete resource die de
 * paden uit het contract implementeert.
 */
@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfielController {

    private static final Logger LOG = Logger.getLogger(ProfielController.class);

    private final PartijService partijService;
    private final PartijMapper partijMapper;
    private final LogboekContext logboekContext;
    private final HashHelper hashHelper;

    public ProfielController(
            PartijService partijService,
            PartijMapper partijMapper,
            LogboekContext logboekContext,
            HashHelper hashHelper) {
        this.partijService = partijService;
        this.partijMapper = partijMapper;
        this.logboekContext = logboekContext;
        this.hashHelper = hashHelper;
    }

    @POST
    @Path("/partij")
    @Transactional
    @RequireBody
    @Logboek(name = "getPartij", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028")
    public Response getPartij(@Valid PartijRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        PartijResponse result = partijService.getPartijResponse(request.getIdentificatieType(), request.getIdentificatieNummer(), request);

        if (result == null) {
            LOG.warn("Partij niet gevonden");
            throw Problems.notFound("Partij niet gevonden", "Geen partij gevonden voor het opgegeven identificatienummer.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Partij opgehaald");
        return Response.ok(result).build();
    }

    @POST
    @Path("/partijen/bulk")
    @Transactional
    @RequireBody
    @Logboek(name = "getPartijBulk", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028")
    public Response getPartijBulk(@Valid PartijBulkRequest request) {

        // Subjects before the lookup, so a partij that is not found is logged as
        // looked up too.
        for (var identificatie : request.getIdentificaties()) {
            logboekContext.addSubject(
                    hashHelper.hashIdentifier(identificatie.getIdentificatieNummer()),
                    String.valueOf(identificatie.getIdentificatieType()));
        }

        List<PartijResponse> results = partijService.getPartijResponseBulk(request.getIdentificaties());

        if (results.isEmpty()) {
            LOG.warn("Geen partijen gevonden in bulk request");
            throw Problems.notFound("Partijen niet gevonden", "Geen van de opgegeven partijen is gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);

        if (results.size() < request.getIdentificaties().size()) {
            LOG.info("Bulk partijen gedeeltelijk opgehaald");
            return Response.status(Response.Status.PARTIAL_CONTENT).entity(results).build();
        }

        LOG.info("Bulk partijen opgehaald");
        return Response.ok(results).build();
    }

    @POST
    @Path("/contactgegeven")
    @Transactional
    @RequireBody
    @Logboek(name = "addContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-142")
    public Response addContactgegeven(@Valid ContactgegevenRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        AddContactgegevenResult result = partijService.addContactgegeven(request.getIdentificatieType(), request.getIdentificatieNummer(), request);
        ContactgegevenResponse body = partijMapper.toContactgegevensResponse(result.contactgegeven());

        URI uri = UriBuilder.fromResource(ProfielController.class)
                .path("contactgegeven").path("{id}")
                .build(result.contactgegeven().id);
        logboekContext.setStatus(StatusCode.OK);

        if (result.wasCreated()) {
            LOG.info("Contactgegeven toegevoegd");
            return Response.created(uri).entity(body).build();
        }

        return Response.ok(body).location(uri).build();
    }

    @PUT
    @Path("/contactgegeven")
    @Transactional
    @RequireBody
    @Logboek(name = "updateContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-367")
    public Response updateContactgegeven(@Valid ContactgegevenUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        boolean updated = partijService.updateContactgegeven(request.getIdentificatieType(), request.getIdentificatieNummer(), request);

        if (!updated) {
            LOG.warn("Contactgegeven niet gevonden voor update");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven bijgewerkt");
        return Response.ok().build();
    }

    @DELETE
    @Path("/contactgegeven/{contactgegevenId}")
    @Transactional
    @RequireBody
    @Logboek(name = "deleteContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-591")
    public Response deleteContactgegeven(
            @PathParam("contactgegevenId") UUID contactgegevenId,
            @Valid PartijIdentificatieRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        boolean deleted = partijService.deleteContactgegeven(request.getIdentificatieType(), request.getIdentificatieNummer(), contactgegevenId);

        if (!deleted) {
            LOG.warn("Contactgegeven niet gevonden voor verwijdering");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven verwijderd");
        return Response.noContent().build();
    }

    @POST
    @Path("/voorkeur")
    @Transactional
    @RequireBody
    @Logboek(name = "addVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-824")
    public Response addVoorkeur(@Valid VoorkeurRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        AddVoorkeurResult result = partijService.addVoorkeur(request.getIdentificatieType(), request.getIdentificatieNummer(), request);
        VoorkeurResponse body = partijMapper.toVoorkeurResponse(result.voorkeur());

        logboekContext.setStatus(StatusCode.OK);
        URI uri = UriBuilder.fromResource(ProfielController.class)
                .path("voorkeur").path("{id}")
                .build(result.voorkeur().id);

        if (result.wasCreated()) {
            LOG.info("Voorkeur toegevoegd");
            return Response.created(uri).entity(body).build();
        }

        if (result.scopeAdded()) {
            LOG.info("Scope toegevoegd aan bestaande voorkeur");
        } else {
            LOG.info("Voorkeur al geregistreerd voor deze partij en scope");
        }
        return Response.ok(body).location(uri).build();
    }

    @PUT
    @Path("/voorkeur")
    @Transactional
    @RequireBody
    @Logboek(name = "updateVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-256")
    public Response updateVoorkeur(@Valid VoorkeurUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        boolean updated = partijService.updateVoorkeur(request.getIdentificatieType(), request.getIdentificatieNummer(), request);

        if (!updated) {
            LOG.warn("Voorkeur niet gevonden voor update");
            throw Problems.notFound("Voorkeur niet gevonden", "Voorkeur of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur bijgewerkt");
        return Response.ok().build();
    }

    @DELETE
    @Path("/voorkeur/{voorkeurId}")
    @Transactional
    @RequireBody
    @Logboek(name = "deleteVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-478")
    public Response deleteVoorkeur(
            @PathParam("voorkeurId") UUID voorkeurId,
            @Valid PartijIdentificatieRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        boolean deleted = partijService.deleteVoorkeur(request.getIdentificatieType(), request.getIdentificatieNummer(), voorkeurId);

        if (!deleted) {
            LOG.warn("Voorkeur niet gevonden voor verwijdering");
            throw Problems.notFound("Voorkeur niet gevonden", "Voorkeur of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur verwijderd");
        return Response.noContent().build();
    }

    @PATCH
    @Path("/voorkeur/te-verwijderen-op")
    @Transactional
    @RequireBody
    @Logboek(name = "updateVoorkeurTeVerwijderenOp", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630")
    public Response updateVoorkeurTeVerwijderenOp(@Valid TeVerwijderenOpRequest request) {
        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        boolean updated = partijService.updateVoorkeurTeVerwijderenOpByDienstverlener(request);

        if (!updated) {
            LOG.warn("Voorkeur of partij niet gevonden voor te-verwijderen-op update");
            throw Problems.notFound("Voorkeur niet gevonden", "Voorkeur of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Te-verwijderen-op bijgewerkt voor voorkeur");
        return Response.ok().build();
    }

    @PATCH
    @Path("/contactgegeven/te-verwijderen-op")
    @Transactional
    @RequireBody
    @Logboek(name = "updateContactgegevenTeVerwijderenOp", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631")
    public Response updateContactgegevenTeVerwijderenOp(@Valid TeVerwijderenOpRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        boolean updated = partijService.updateContactgegevenTeVerwijderenOpByDienstverlener(request);

        if (!updated) {
            LOG.warn("Contactgegeven of partij niet gevonden voor te-verwijderen-op update");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Te-verwijderen-op bijgewerkt voor contactgegeven");
        return Response.ok().build();
    }
}
