package nl.rijksoverheid.moz.controller;

import io.opentelemetry.api.trace.StatusCode;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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
import java.util.UUID;

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
    @Path("/{identificatieType}/{identificatieNummer}")
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
                    description = "Partij niet gevonden of is verwijderd"
            )
    })
    @Logboek(name= "getPartij", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-028")
    public Response getPartij(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @BeanParam PartijRequest partijRequest) {

        PartijResponse result = partijService.getPartijResponse(identificatieType, identificatieNummer, partijRequest);

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (result == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Partij niet gevonden");
            return Response.status(Response.Status.NOT_FOUND).build();
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
    @Path("/contactgegeven/{identificatieType}/{identificatieNummer}")
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
                    description = "Request body mag niet leeg zijn"
            )
    })
    @Logboek(name= "addContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-142")
    public Response addContactgegeven(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @Valid ContactgegevenRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij addContactgegeven");
            return Response.status(Response.Status.BAD_REQUEST).entity("Request body mag niet leeg zijn").build();
        }

        AddContactgegevenResult result = partijService.addContactgegeven(identificatieType, identificatieNummer, request);
        ContactgegevenResponse body = partijMapper.toContactgegevensResponse(result.contactgegeven());

        URI uri = URI.create(String.format("/contactgegeven/%s/%s", identificatieType, identificatieNummer));
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
    @Path("/contactgegeven/{identificatieType}/{identificatieNummer}/")
    @Transactional
    @Operation(
            summary = "Update contactgegeven van een partij",
            description = "Werk type, waarde en scope van een contactgegeven bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Contactgegeven succesvol bijgewerkt"),
            @APIResponse(responseCode = "400", description = "Request body mag niet leeg zijn"),
            @APIResponse(responseCode = "404", description = "Contactgegeven of partij niet gevonden")
    })
    @Logboek(name= "updateContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-367")
    public Response updateContactgegeven(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @Valid ContactgegevenUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij updateContactgegeven");
            return Response.status(Response.Status.BAD_REQUEST).entity("Request body mag niet leeg zijn").build();
        }

        boolean updated = partijService.updateContactgegeven(identificatieType, identificatieNummer, request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor update");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Contactgegeven bijgewerkt");
        return Response.ok().build();
    }

    /**
     * Verwijder een contactgegeven van een partij.
     */
    @DELETE
    @Path("/contactgegeven/{identificatieType}/{identificatieNummer}/{contactgegevenId}")
    @Transactional
    @Operation(
            summary = "Verwijder contactgegeven van een partij",
            description = "Verwijdert een contactgegeven volledig"
    )
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Contactgegeven succesvol verwijderd"),
            @APIResponse(responseCode = "404", description = "Contactgegeven of partij niet gevonden")
    })
    @Logboek(name= "deleteContactgegeven", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-591")
    public Response deleteContactgegeven(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @PathParam("contactgegevenId") UUID contactgegevenId) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        boolean deleted = partijService.deleteContactgegeven(identificatieType, identificatieNummer, contactgegevenId);

        if (!deleted) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Contactgegeven niet gevonden voor verwijdering");
            return Response.status(Response.Status.NOT_FOUND).build();
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
    @Path("/voorkeur/{identificatieType}/{identificatieNummer}")
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
                    description = "Request body mag niet leeg zijn"
            )
    })
    @Logboek(name= "addVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-824")
    public Response addVoorkeur(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @Valid VoorkeurRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij addVoorkeur");
            return Response.status(Response.Status.BAD_REQUEST).entity("Request body mag niet leeg zijn").build();
        }

        AddVoorkeurResult result = partijService.addVoorkeur(identificatieType, identificatieNummer, request);
        VoorkeurResponse body = partijMapper.toVoorkeurResponse(result.voorkeur());

        logboekContext.setStatus(StatusCode.OK);
        URI uri = URI.create(String.format("/voorkeur/%s/%s", identificatieType, identificatieNummer));

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
     * Alleen type, en waarde kunnen worden geüpdatet.
     */
    @PUT
    @Path("/voorkeur/{identificatieType}/{identificatieNummer}/")
    @Transactional
    @Operation(
            summary = "Update voorkeur van een partij",
            description = "Werk type, waarde en scope van een voorkeur bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voorkeur succesvol bijgewerkt"),
            @APIResponse(responseCode = "400", description = "Request body mag niet leeg zijn"),
            @APIResponse(responseCode = "404", description = "Voorkeur of partij niet gevonden")
    })
    @Logboek(name= "updateVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-256")
    public Response updateVoorkeur(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @Valid VoorkeurUpdateRequest request) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        if (request == null) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Request body mag niet leeg zijn bij updateVoorkeur");
            return Response.status(Response.Status.BAD_REQUEST).entity("Request body mag niet leeg zijn").build();
        }

        boolean updated = partijService.updateVoorkeur(identificatieType, identificatieNummer, request);

        if (!updated) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor update");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur bijgewerkt");
        return Response.ok().build();
    }

    /**
     * Verwijder een voorkeur van een partij.
     */
    @DELETE
    @Path("/voorkeur/{identificatieType}/{identificatieNummer}/{voorkeurId}")
    @Transactional
    @Operation(
            summary = "Verwijder voorkeur van een partij",
            description = "Verwijdert een voorkeur volledig"
    )
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Voorkeur succesvol verwijderd"),
            @APIResponse(responseCode = "404", description = "Voorkeur of partij niet gevonden")
    })
    @Logboek(name= "deleteVoorkeur", processingActivityId = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-478")
    public Response deleteVoorkeur(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @PathParam("voorkeurId") UUID voorkeurId) {

        logboekContext.setDataSubjectId(hashHelper.hashIdentifier(identificatieNummer));
        logboekContext.setDataSubjectType(String.valueOf(identificatieType));

        boolean deleted = partijService.deleteVoorkeur(identificatieType, identificatieNummer, voorkeurId);

        if (!deleted) {
            logboekContext.setStatus(StatusCode.ERROR);
            LOG.warn("Voorkeur niet gevonden voor verwijdering");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        logboekContext.setStatus(StatusCode.OK);
        LOG.info("Voorkeur verwijderd");
        return Response.noContent().build();
    }
}