
package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.inject.Inject;
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
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.common.ApiResponseDescriptions;
import nl.rijksoverheid.moz.common.MediaTypes;
import nl.rijksoverheid.moz.dto.request.PartijBulkRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.TeVerwijderenOpRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
import nl.rijksoverheid.moz.filter.RequireBody;
import nl.rijksoverheid.moz.helper.HashHelper;
import nl.rijksoverheid.moz.helper.Problems;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import nl.rijksoverheid.moz.services.PartijService;
import nl.rijksoverheid.moz.services.PartijService.AddContactgegevenResult;
import nl.rijksoverheid.moz.services.PartijService.AddVoorkeurResult;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller voor het beheren van partijen.
 * <p>
 * Deze controller biedt endpoints voor:
 * <ul>
 *   <li>Ophalen van partijen (enkelvoudig en bulk)</li>
 *   <li>Beheren van contactgegevens</li>
 *   <li>Beheren van voorkeuren</li>
 * </ul>
 */
@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Profiel", description = "Endpoints voor het beheren van partijen")
@APIResponse(
        responseCode = "500",
        description = ApiResponseDescriptions.INTERNAL_SERVER_ERROR,
        content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
)
public class ProfielController {

    private static final Logger LOG = Logger.getLogger(ProfielController.class);

    @Inject
    PartijService partijService;

    @Inject
    PartijMapper partijMapper;

    @Inject
    LogboekContext logboekContext;

    @Inject
    HashHelper hashHelper;

    @Inject
    ProcessingHandler processingHandler;

    /**
     * Haalt een profiel op van een partij.
     *
     * @param request Request body met identificatieType (BSN, KVK, RSIN) en identificatieNummer
     * @return Response met PartijResponse of 404 als de partij niet bestaat
     */
    @POST
    @Path("/partij")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Ophalen profiel van een partij",
            description = "Haalt het profiel op van een partij"
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Partij succesvol opgehaald",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PartijResponse.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Partij niet gevonden of is verwijderd",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    @Logboek(name = "getPartij", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028")
    public Response getPartij(@Valid PartijRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        PartijResponse result = partijService.getPartijResponse(request.identificatieType, request.identificatieNummer, request);

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
    @Operation(
            summary = "Ophalen profielen van meerdere partijen",
            description = "Haalt profielen op van meerdere partijen. Niet-gevonden partijen worden stilzwijgend weggelaten."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Alle profielen succesvol opgehaald",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = PartijResponse.class))
            ),
            @APIResponse(
                    responseCode = "206",
                    description = "Profielen gedeeltelijk opgehaald",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = PartijResponse.class))
            ),
            @APIResponse(responseCode = "400", description = ApiResponseDescriptions.BAD_REQUEST_BODY),
            @APIResponse(
                    responseCode = "404",
                    description = "Geen enkel profiel gevonden",
                    content = @Content(mediaType = MediaTypes.PROBLEM_JSON, schema = @Schema(implementation = HttpProblem.class))
            )
    })
    public Response getPartijBulk(@Valid PartijBulkRequest request) {

        List<PartijResponse> results = partijService.getPartijResponseBulk(request.identificaties);

        Set<String> foundKeys = results.stream()
                .flatMap(r -> r.identificaties.stream())
                .map(id -> id.identificatieType + ":" + id.identificatieNummer)
                .collect(Collectors.toSet());

        for (var identificatie : request.identificaties) {
            boolean found = foundKeys.contains(identificatie.identificatieType + ":" + identificatie.identificatieNummer);
            LogboekContext ctx = new LogboekContext();
            ctx.setProcessingActivityId("https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028");
            ctx.setDataSubjectId(hashHelper.hashIdentifier(identificatie.identificatieNummer));
            ctx.setDataSubjectType(String.valueOf(identificatie.identificatieType));
            ctx.setStatus(found ? StatusCode.OK : StatusCode.UNSET);
            Span span = processingHandler.startSpan("getPartijBulk", Context.current());
            processingHandler.addLogboekContextToSpan(span, ctx);
            span.end();
        }

        if (results.isEmpty()) {
            LOG.warn("Geen partijen gevonden in bulk request");
            throw Problems.notFound("Partijen niet gevonden", "Geen van de opgegeven partijen is gevonden.");
        }

        if (results.size() < request.identificaties.size()) {
            LOG.info("Bulk partijen gedeeltelijk opgehaald");
            return Response.status(Response.Status.PARTIAL_CONTENT).entity(results).build();
        }

        LOG.info("Bulk partijen opgehaald");
        return Response.ok(results).build();
    }

    /**
     * Voegt een nieuwe contactgegeven toe voor een partij.
     *
     * @param request Request body met contactgegevens en partij identificatie
     * @return Response 201 Created met Location header naar de aangemaakte resource
     */
    @POST
    @Path("/contactgegeven")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Toevoegen nieuwe contactgegeven voor een partij",
            description = "Voegt een nieuwe contactgegeven toe. Creëert automatisch ontbrekende partijen."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Contactgegeven succesvol toegevoegd",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ContactgegevenResponse.class))
            ),
            @APIResponse(
                    responseCode = "200",
                    description = "Contactgegeven was al geregistreerd voor deze partij en scope",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ContactgegevenResponse.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = ApiResponseDescriptions.BAD_REQUEST_BODY
            )
    })
    @Logboek(name = "addContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-142")
    public Response addContactgegeven(@Valid ContactgegevenRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        AddContactgegevenResult result = partijService.addContactgegeven(request.identificatieType, request.identificatieNummer, request);
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

    /**
     * Update een bestaand contactgegeven van een partij.
     * IdentificatieType en Nummer kunnen niet gewijzigd worden.
     * Alleen type, waarde en scope kunnen worden geüpdatet.
     */
    @PUT
    @Path("/contactgegeven")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Update contactgegeven van een partij",
            description = "Werk type, waarde en scope van een contactgegeven bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Contactgegeven succesvol bijgewerkt"),
            @APIResponse(responseCode = "400", description = ApiResponseDescriptions.BAD_REQUEST_BODY),
            @APIResponse(responseCode = "404", description = ApiResponseDescriptions.CONTACTGEGEVEN_OF_PARTIJ_NIET_GEVONDEN)
    })
    @Logboek(name = "updateContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-367")
    public Response updateContactgegeven(@Valid ContactgegevenUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        boolean updated = partijService.updateContactgegeven(request.identificatieType, request.identificatieNummer, request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor update");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven bijgewerkt");
        return Response.ok().build();
    }

    /**
     * Verwijder een contactgegeven van een partij.
     */
    @DELETE
    @Path("/contactgegeven/{contactgegevenId}")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Verwijder contactgegeven van een partij",
            description = "Verwijdert een contactgegeven volledig"
    )
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Contactgegeven succesvol verwijderd"),
            @APIResponse(responseCode = "404", description = ApiResponseDescriptions.CONTACTGEGEVEN_OF_PARTIJ_NIET_GEVONDEN)
    })
    @Logboek(name = "deleteContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-591")
    public Response deleteContactgegeven(
            @PathParam("contactgegevenId") UUID contactgegevenId,
            @Valid PartijIdentificatieRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        boolean deleted = partijService.deleteContactgegeven(request.identificatieType, request.identificatieNummer, contactgegevenId);

        if (!deleted) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor verwijdering");
            throw Problems.notFound("Contactgegeven niet gevonden", "Contactgegeven of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven verwijderd");
        return Response.noContent().build();
    }

    /**
     * Voegt een nieuwe voorkeur toe voor een partij.
     *
     * @param request Request body met voorkeur gegevens en partij identificatie
     * @return Response 201 Created met Location header naar de aangemaakte resource
     */
    @POST
    @Path("/voorkeur")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Toevoegen nieuwe voorkeur voor een partij",
            description = "Voegt een nieuwe voorkeur toe. Creëert automatisch ontbrekende partijen."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Voorkeur succesvol toegevoegd",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VoorkeurResponse.class))
            ),
            @APIResponse(
                    responseCode = "200",
                    description = "Voorkeur was al geregistreerd voor deze partij en scope",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VoorkeurResponse.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = ApiResponseDescriptions.BAD_REQUEST_BODY
            )
    })
    @Logboek(name = "addVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-824")
    public Response addVoorkeur(@Valid VoorkeurRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        AddVoorkeurResult result = partijService.addVoorkeur(request.identificatieType, request.identificatieNummer, request);
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

    /**
     * Update een bestaande voorkeur van een partij.
     * IdentificatieType en Nummer kunnen niet gewijzigd worden.
     * Alleen type en waarde kunnen worden geüpdatet.
     */
    @PUT
    @Path("/voorkeur")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Update voorkeur van een partij",
            description = "Werk type, waarde en scope van een voorkeur bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voorkeur succesvol bijgewerkt"),
            @APIResponse(responseCode = "400", description = ApiResponseDescriptions.BAD_REQUEST_BODY),
            @APIResponse(responseCode = "404", description = ApiResponseDescriptions.VOORKEUR_OF_PARTIJ_NIET_GEVONDEN)
    })
    @Logboek(name = "updateVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-256")
    public Response updateVoorkeur(@Valid VoorkeurUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        boolean updated = partijService.updateVoorkeur(request.identificatieType, request.identificatieNummer, request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor update");
            throw Problems.notFound("Voorkeur niet gevonden", "Voorkeur of partij niet gevonden.");
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur bijgewerkt");
        return Response.ok().build();
    }

    /**
     * Verwijder een voorkeur van een partij.
     */
    @DELETE
    @Path("/voorkeur/{voorkeurId}")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Verwijder voorkeur van een partij",
            description = "Verwijdert een voorkeur volledig"
    )
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Voorkeur succesvol verwijderd"),
            @APIResponse(responseCode = "404", description = ApiResponseDescriptions.VOORKEUR_OF_PARTIJ_NIET_GEVONDEN)
    })
    @Logboek(name = "deleteVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-478")
    public Response deleteVoorkeur(
            @PathParam("voorkeurId") UUID voorkeurId,
            @Valid PartijIdentificatieRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        boolean deleted = partijService.deleteVoorkeur(request.identificatieType, request.identificatieNummer, voorkeurId);

        if (!deleted) {
            logboekContext.setStatus(StatusCode.ERROR);
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
    @Operation(
            summary = "Stel te-verwijderen-op in voor een voorkeur (Dienstverlener)",
            description = "Stelt of overschrijft de te-verwijderen-op datum voor een voorkeur. Alleen toegestaan voor een Dienstverlener met een bestaande scope op de voorkeur."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Te-verwijderen-op succesvol bijgewerkt"),
            @APIResponse(responseCode = "400", description = ApiResponseDescriptions.BAD_REQUEST_BODY),
            @APIResponse(responseCode = "403", description = "Dienstverlener heeft geen scope op deze voorkeur"),
            @APIResponse(responseCode = "404", description = ApiResponseDescriptions.VOORKEUR_OF_PARTIJ_NIET_GEVONDEN)
    })
    @Logboek(name = "updateVoorkeurTeVerwijderenOp", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630")
    public Response updateVoorkeurTeVerwijderenOp(@Valid TeVerwijderenOpRequest request) {
        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        boolean updated = partijService.updateVoorkeurTeVerwijderenOpByDienstverlener(request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur of partij niet gevonden voor te-verwijderen-op update");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Te-verwijderen-op bijgewerkt voor voorkeur");
        return Response.ok().build();
    }

    @PATCH
    @Path("/contactgegeven/te-verwijderen-op")
    @Transactional
    @RequireBody
    @Operation(
            summary = "Stel te-verwijderen-op in voor een contactgegeven (Dienstverlener)",
            description = "Stelt of overschrijft de te-verwijderen-op datum voor een contactgegeven. Alleen toegestaan voor een Dienstverlener met een bestaande scope op het contactgegeven."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Te-verwijderen-op succesvol bijgewerkt"),
            @APIResponse(responseCode = "400", description = ApiResponseDescriptions.BAD_REQUEST_BODY),
            @APIResponse(responseCode = "403", description = "Dienstverlener heeft geen scope op dit contactgegeven"),
            @APIResponse(responseCode = "404", description = ApiResponseDescriptions.CONTACTGEGEVEN_OF_PARTIJ_NIET_GEVONDEN)
    })
    @Logboek(name = "updateContactgegevenTeVerwijderenOp", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631")
    public Response updateContactgegevenTeVerwijderenOp(@Valid TeVerwijderenOpRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(request.identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(request.identificatieType));

        boolean updated = partijService.updateContactgegevenTeVerwijderenOpByDienstverlener(request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven of partij niet gevonden voor te-verwijderen-op update");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Te-verwijderen-op bijgewerkt voor contactgegeven");
        return Response.ok().build();
    }

}
