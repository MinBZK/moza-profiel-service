package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.StatusCode;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.Logboek;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.ProblemDetail;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.helper.HashHelper;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import nl.rijksoverheid.moz.services.PartijService;
import nl.rijksoverheid.moz.services.PartijService.AddContactgegevenResult;
import nl.rijksoverheid.moz.services.PartijService.AddVoorkeurResult;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * REST Controller voor het beheren van partijen.
 * <p>
 * Deze controller biedt endpoints voor:
 * <ul>
 *   <li>Ophalen en beheren van partijen</li>
 *   <li>Delete van partijen</li>
 * </ul>
 */
@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Profiel", description = "Endpoints voor het beheren van partijen")
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

    /**
     * Haalt een profiel op van een partij.
     * <p>     *
     * @param identificatieType Type identificatie (BSN, KVK, RSIN)
     * @param identificatieNummer Het unieke identificatienummer van de partij
     * @return Response met ResponseVoorPartij of 404 als de partij niet bestaat
     */
    @GET
    @Path("/identificaties/{identificatie-type}/{identificatie-nummer}")
    @Transactional
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
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @Logboek(name= "getPartij", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028")
    public Response getPartij(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            @BeanParam PartijRequest partijRequest) {

        PartijResponse result = partijService.getPartijResponse(identificatieType, identificatieNummer, partijRequest);

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (result == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Partij niet gevonden");
            throw new NotFoundException();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Partij opgehaald");
        return Response.ok(result).build();
    }

    /**
     * Voegt een niewe contactgegeven toe voor een partij.
     *
     * @param identificatieType Type identificatie van de partij
     * @param identificatieNummer Identificatienummer van de partij
     * @param request Request body met contactgegevens
     * @return Response 201 Created met Location header naar de aangemaakte resource
     */
    @POST
    @Path("/contactgegevens/{identificatie-type}/{identificatie-nummer}")
    @Transactional
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
                    description = "Ongeldige request body",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @Logboek(name= "addContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-142")
    public Response addContactgegeven(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            ContactgegevenRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij addContactgegeven");
            throw new BadRequestException("Request body mag niet leeg zijn");
        }

        AddContactgegevenResult result = partijService.addContactgegeven(identificatieType, identificatieNummer, request);
        ContactgegevenResponse body = partijMapper.toContactgegevensResponse(result.contactgegeven());

        URI uri = URI.create(String.format("/contactgegevens/%s/%s/%d", identificatieType, identificatieNummer, result.contactgegeven().id));
        logboekContext.setStatus(StatusCode.OK);

        if (result.wasCreated()) {
            LOG.info("Contactgegeven toegevoegd");
            return Response.created(uri).entity(body).build();
        }

        LOG.info("Contactgegeven al geregistreerd voor deze partij en scope");
        return Response.ok(body).location(uri).build();
    }

    /**
     * Update een bestaand contactgegeven van een partij.
     * IdentificatieType en Nummer kunnen niet gewijzigd worden.
     * Alleen type, waarde en scope kunnen worden geüpdatet.
     */
    @PUT
    @Path("/contactgegevens/{identificatie-type}/{identificatie-nummer}/{contactgegeven-id}")
    @Transactional
    @Operation(
            summary = "Update contactgegeven van een partij",
            description = "Werk type, waarde en scope van een contactgegeven bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Contactgegeven succesvol bijgewerkt"),
            @APIResponse(
                    responseCode = "404",
                    description = "Contactgegeven of partij niet gevonden",
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
    @Logboek(name= "updateContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-367")
    public Response updateContactgegeven(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            @PathParam("contactgegeven-id") Long contactgegevenId,
            ContactgegevenUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij updateContactgegeven");
            throw new BadRequestException("Request body mag niet leeg zijn");
        }

        request.id = contactgegevenId;
        boolean updated = partijService.updateContactgegeven(identificatieType, identificatieNummer, request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor update");
            throw new NotFoundException();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven bijgewerkt");
        return Response.ok().build();
    }

    /**
     * Verwijder een contactgegeven van een partij.
     */
    @DELETE
    @Path("/contactgegevens/{identificatie-type}/{identificatie-nummer}/{contactgegeven-id}")
    @Transactional
    @Operation(
            summary = "Verwijder contactgegeven van een partij",
            description = "Verwijdert een contactgegeven volledig"
    )
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Contactgegeven succesvol verwijderd"),
            @APIResponse(
                    responseCode = "404",
                    description = "Contactgegeven of partij niet gevonden",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @Logboek(name= "deleteContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-591")
    public Response deleteContactgegeven(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            @PathParam("contactgegeven-id") Long contactgegevenId) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        boolean deleted = partijService.deleteContactgegeven(identificatieType, identificatieNummer, contactgegevenId);

        if (!deleted) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor verwijdering");
            throw new NotFoundException();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven verwijderd");
        return Response.noContent().build();
    }

    /**
     * Voegt een nieuwe voorkeur toe voor een partij.
     *
     * @param identificatieType Type identificatie van de partij
     * @param identificatieNummer Identificatienummer van de partij
     * @param request Request body met voorkeur gegevens
     * @return Response 201 Created met Location header naar de aangemaakte resource
     */
    @POST
    @Path("/voorkeuren/{identificatie-type}/{identificatie-nummer}")
    @Transactional
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
                    description = "Ongeldige request body",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @Logboek(name= "addVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-824")
    public Response addVoorkeur(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            VoorkeurRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij addVoorkeur");
            throw new BadRequestException("Request body mag niet leeg zijn");
        }

        AddVoorkeurResult result = partijService.addVoorkeur(identificatieType, identificatieNummer, request);
        VoorkeurResponse body = partijMapper.toVoorkeurResponse(result.voorkeur());

        logboekContext.setStatus(StatusCode.OK);
        URI uri = URI.create(String.format("/voorkeuren/%s/%s/%d", identificatieType, identificatieNummer, result.voorkeur().id));

        if (result.wasCreated()) {
            LOG.info("Voorkeur toegevoegd");
            return Response.created(uri).entity(body).build();
        }

        LOG.info("Voorkeur al geregistreerd voor deze partij en scope");
        return Response.ok(body).location(uri).build();
    }

    /**
     * Update een bestaande voorkeur van een partij.
     * IdentificatieType en Nummer kunnen niet gewijzigd worden.
     * Alleen type, en waarde kunnen worden geüpdatet.
     */
    @PUT
    @Path("/voorkeuren/{identificatie-type}/{identificatie-nummer}/{voorkeur-id}")
    @Transactional
    @Operation(
            summary = "Update voorkeur van een partij",
            description = "Werk type, waarde en scope van een voorkeur bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voorkeur succesvol bijgewerkt"),
            @APIResponse(
                    responseCode = "404",
                    description = "Voorkeur of partij niet gevonden",
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
    @Logboek(name= "updateVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-256")
    public Response updateVoorkeur(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            @PathParam("voorkeur-id") Long voorkeurId,
            VoorkeurUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij updateVoorkeur");
            throw new BadRequestException("Request body mag niet leeg zijn");
        }

        request.id = voorkeurId;
        boolean updated = partijService.updateVoorkeur(identificatieType, identificatieNummer, request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor update");
            throw new NotFoundException();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur bijgewerkt");
        return Response.ok().build();
    }

    /**
     * Verwijder een voorkeur van een partij.
     */
    @DELETE
    @Path("/voorkeuren/{identificatie-type}/{identificatie-nummer}/{voorkeur-id}")
    @Transactional
    @Operation(
            summary = "Verwijder voorkeur van een partij",
            description = "Verwijdert een voorkeur volledig"
    )
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Voorkeur succesvol verwijderd"),
            @APIResponse(
                    responseCode = "404",
                    description = "Voorkeur of partij niet gevonden",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Interne serverfout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @Logboek(name= "deleteVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-478")
    public Response deleteVoorkeur(
            @PathParam("identificatie-type") IdentificatieType identificatieType,
            @PathParam("identificatie-nummer") String identificatieNummer,
            @PathParam("voorkeur-id") Long voorkeurId) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        boolean deleted = partijService.deleteVoorkeur(identificatieType, identificatieNummer, voorkeurId);

        if (!deleted) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor verwijdering");
            throw new NotFoundException();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur verwijderd");
        return Response.noContent().build();
    }
}