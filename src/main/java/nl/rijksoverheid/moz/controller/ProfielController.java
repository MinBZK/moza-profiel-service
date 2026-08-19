
package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.Logboek;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenResponse;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijBulkRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurResponse;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.HashHelper;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import nl.rijksoverheid.moz.services.PartijService;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ProcessingHandler processingHandler;

    public ProfielController(
            PartijService partijService,
            PartijMapper partijMapper,
            LogboekContext logboekContext,
            HashHelper hashHelper,
            ProcessingHandler processingHandler) {
        this.partijService = partijService;
        this.partijMapper = partijMapper;
        this.logboekContext = logboekContext;
        this.hashHelper = hashHelper;
        this.processingHandler = processingHandler;
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
            logboekContext.setStatus(StatusCode.ERROR);
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
    public Response getPartijBulk(@Valid PartijBulkRequest request) {

        List<PartijResponse> results = partijService.getPartijResponseBulk(request.getIdentificaties());

        Set<String> foundKeys = results.stream()
                .flatMap(r -> r.getIdentificaties().stream())
                .map(id -> id.getIdentificatieType() + ":" + id.getIdentificatieNummer())
                .collect(Collectors.toSet());

        for (var identificatie : request.getIdentificaties()) {
            boolean found = foundKeys.contains(identificatie.getIdentificatieType() + ":" + identificatie.getIdentificatieNummer());
            LogboekContext ctx = new LogboekContext();
            ctx.setProcessingActivityId("https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028");
            ctx.setDataSubjectId(hashHelper.hashIdentifier(identificatie.getIdentificatieNummer()));
            ctx.setDataSubjectType(String.valueOf(identificatie.getIdentificatieType()));
            ctx.setStatus(found ? StatusCode.OK : StatusCode.UNSET);
            Span span = processingHandler.startSpan("getPartijBulk", Context.current());
            processingHandler.addLogboekContextToSpan(span, ctx);
            span.end();
        }

        if (results.isEmpty()) {
            LOG.warn("Geen partijen gevonden in bulk request");
            throw Problems.notFound("Partijen niet gevonden", "Geen van de opgegeven partijen is gevonden.");
        }

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

        Contactgegeven contactgegeven = partijService.addContactgegeven(request.getIdentificatieType(), request.getIdentificatieNummer(), request);
        ContactgegevenResponse body = partijMapper.mapContactgegeven(contactgegeven);

        URI uri = UriBuilder.fromResource(ProfielController.class)
                .path("contactgegeven").path("{id}")
                .build(contactgegeven.id);
        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven toegevoegd");

        return Response.created(uri).entity(body).build();
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
            logboekContext.setStatus(StatusCode.ERROR);
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
    @Logboek(name = "verwijderContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631")
    public Response verwijderContactgegeven(
            @PathParam("contactgegevenId") UUID contactgegevenId,
            @Valid PartijIdentificatieRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        Contactgegeven contactgegeven = partijService.verwijderContactgegeven(request.getIdentificatieType(), request.getIdentificatieNummer(), contactgegevenId);

        if (contactgegeven == null) {
            logboekContext.setStatus(StatusCode.ERROR);
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

        Voorkeur voorkeur = partijService.addVoorkeur(request.getIdentificatieType(), request.getIdentificatieNummer(), request);
        VoorkeurResponse body = partijMapper.mapVoorkeur(voorkeur);

        logboekContext.setStatus(StatusCode.OK);
        URI uri = UriBuilder.fromResource(ProfielController.class)
                .path("voorkeur").path("{id}")
                .build(voorkeur.id);
        LOG.info("Voorkeur toegevoegd");

        return Response.created(uri).entity(body).build();
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
            logboekContext.setStatus(StatusCode.ERROR);
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
    @Logboek(name = "verwijderVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630")
    public Response verwijderVoorkeur(
            @PathParam("voorkeurId") UUID voorkeurId,
            @Valid PartijIdentificatieRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(request.getIdentificatieType()));

        Voorkeur voorkeur = partijService.verwijderVoorkeur(request.getIdentificatieType(), request.getIdentificatieNummer(), voorkeurId);

        if (voorkeur == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor verwijdering");
            throw Problems.notFound("Voorkeur niet gevonden", "Voorkeur of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur verwijderd");

        return Response.noContent().build();
    }
}
