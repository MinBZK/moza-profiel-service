
package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.Logboek;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.api.generated.api.ProfielApi;
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
 * REST controller voor partijen. Contract-first (#651, #751): implementeert de uit
 * {@code META-INF/openapi.yaml} gegenereerde {@link ProfielApi}, die de paden, HTTP-methodes,
 * mediatypes en de validatie van de body-parameter draagt. Herhaal ze hier niet: één
 * JAX-RS-annotatie op een implementatiemethode laat álle annotaties van de interface voor
 * die methode vervallen (JAX-RS 3.1 §3.6), inclusief {@code @Path}. De gedocumenteerde route
 * geeft dan een 404 die niet te onderscheiden is van "niet gevonden", en de methode herbindt
 * zich stilzwijgend aan het pad op klasseniveau. Een parameterconstraint opnieuw declareren
 * is een harde fout: dan start de applicatie niet meer (HV000151).
 */
public class ProfielController implements ProfielApi {

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

    @Override
    @Transactional
    @Logboek(name = "getPartij", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028")
    public Response getPartij(PartijRequest partijRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(partijRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(partijRequest.getIdentificatieType()));

        PartijResponse result = partijService.getPartijResponse(partijRequest.getIdentificatieType(), partijRequest.getIdentificatieNummer(), partijRequest);

        if (result == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Partij niet gevonden");
            throw Problems.notFound("Partij niet gevonden", "Geen partij gevonden voor het opgegeven identificatienummer.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Partij opgehaald");
        return Response.ok(result).type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Transactional
    public Response getPartijBulk(PartijBulkRequest partijBulkRequest) {

        List<PartijResponse> results = partijService.getPartijResponseBulk(partijBulkRequest.getIdentificaties());

        Set<String> foundKeys = results.stream()
                .flatMap(r -> r.getIdentificaties().stream())
                .map(id -> id.getIdentificatieType() + ":" + id.getIdentificatieNummer())
                .collect(Collectors.toSet());

        for (var identificatie : partijBulkRequest.getIdentificaties()) {
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

        if (results.size() < partijBulkRequest.getIdentificaties().size()) {
            LOG.info("Bulk partijen gedeeltelijk opgehaald");
            return Response.status(Response.Status.PARTIAL_CONTENT).entity(results)
                    .type(MediaType.APPLICATION_JSON).build();
        }

        LOG.info("Bulk partijen opgehaald");
        return Response.ok(results).type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Transactional
    @Logboek(name = "addContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-142")
    public Response addContactgegeven(ContactgegevenRequest contactgegevenRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(contactgegevenRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(contactgegevenRequest.getIdentificatieType()));

        Contactgegeven contactgegeven = partijService.addContactgegeven(contactgegevenRequest.getIdentificatieType(), contactgegevenRequest.getIdentificatieNummer(), contactgegevenRequest);
        ContactgegevenResponse body = partijMapper.mapContactgegeven(contactgegeven);

        URI uri = UriBuilder.fromResource(ProfielApi.class)
                .path("contactgegeven").path("{id}")
                .build(contactgegeven.id);
        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven toegevoegd");

        return Response.created(uri).entity(body).type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Transactional
    @Logboek(name = "updateContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-367")
    public Response updateContactgegeven(ContactgegevenUpdateRequest contactgegevenUpdateRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(contactgegevenUpdateRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(contactgegevenUpdateRequest.getIdentificatieType()));

        boolean updated = partijService.updateContactgegeven(contactgegevenUpdateRequest.getIdentificatieType(), contactgegevenUpdateRequest.getIdentificatieNummer(), contactgegevenUpdateRequest);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor update");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven bijgewerkt");

        return Response.ok().build();
    }

    @Override
    @Transactional
    // PS-631 komt van het verwijderde updateContactgegevenTeVerwijderenOp-PATCH-endpoint (niet van
    // de PS-591 die dit DELETE-endpoint zelf had) en wordt ook door RetentieScheduler gebruikt voor
    // de retentietaak. Welke ID('s) hier definitief horen is nog open, zie
    // https://github.com/MinBZK/MijnOverheidZakelijk/issues/754.
    @Logboek(name = "verwijderContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631")
    public Response verwijderContactgegeven(
            UUID contactgegevenId,
            PartijIdentificatieRequest partijIdentificatieRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(partijIdentificatieRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(partijIdentificatieRequest.getIdentificatieType()));

        Contactgegeven contactgegeven = partijService.verwijderContactgegeven(partijIdentificatieRequest.getIdentificatieType(), partijIdentificatieRequest.getIdentificatieNummer(), contactgegevenId);

        if (contactgegeven == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor verwijdering");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven verwijderd");

        return Response.noContent().build();
    }

    @Override
    @Transactional
    @Logboek(name = "addVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-824")
    public Response addVoorkeur(VoorkeurRequest voorkeurRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(voorkeurRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(voorkeurRequest.getIdentificatieType()));

        Voorkeur voorkeur = partijService.addVoorkeur(voorkeurRequest.getIdentificatieType(), voorkeurRequest.getIdentificatieNummer(), voorkeurRequest);
        VoorkeurResponse body = partijMapper.mapVoorkeur(voorkeur);

        logboekContext.setStatus(StatusCode.OK);
        URI uri = UriBuilder.fromResource(ProfielApi.class)
                .path("voorkeur").path("{id}")
                .build(voorkeur.id);
        LOG.info("Voorkeur toegevoegd");

        return Response.created(uri).entity(body).type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Transactional
    @Logboek(name = "updateVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-256")
    public Response updateVoorkeur(VoorkeurUpdateRequest voorkeurUpdateRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(voorkeurUpdateRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(voorkeurUpdateRequest.getIdentificatieType()));

        boolean updated = partijService.updateVoorkeur(voorkeurUpdateRequest.getIdentificatieType(), voorkeurUpdateRequest.getIdentificatieNummer(), voorkeurUpdateRequest);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor update");
            throw Problems.notFound("Voorkeur niet gevonden", "Voorkeur of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur bijgewerkt");

        return Response.ok().build();
    }

    @Override
    @Transactional
    // PS-630 komt van het verwijderde updateVoorkeurTeVerwijderenOp-PATCH-endpoint (niet van de
    // PS-478 die dit DELETE-endpoint zelf had) en wordt ook door RetentieScheduler gebruikt voor
    // de retentietaak. Welke ID('s) hier definitief horen is nog open, zie
    // https://github.com/MinBZK/MijnOverheidZakelijk/issues/754.
    @Logboek(name = "verwijderVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630")
    public Response verwijderVoorkeur(
            UUID voorkeurId,
            PartijIdentificatieRequest partijIdentificatieRequest) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(partijIdentificatieRequest.getIdentificatieNummer()));
        logboekContext.setDataSubjectType(String.valueOf(partijIdentificatieRequest.getIdentificatieType()));

        Voorkeur voorkeur = partijService.verwijderVoorkeur(partijIdentificatieRequest.getIdentificatieType(), partijIdentificatieRequest.getIdentificatieNummer(), voorkeurId);

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
