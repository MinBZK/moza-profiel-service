
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import nl.rijksoverheid.moz.services.PartijService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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

    @Inject
    PartijService partijService;

    /**
     * Haalt een profiel op van een partij.
     * <p>     *
     * @param identificatieType Type identificatie (BSN, KVK, RSIN)
     * @param identificatieNummer Het unieke identificatienummer van de partij
     * @return Response met ResponseVoorPartij of 404 als de partij niet bestaat
     */
    @GET
    @Path("/{identificatieType}/{identificatieNummer}")
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
    public Response getPartij(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @BeanParam PartijRequest partijRequest) {

        PartijResponse result = partijService.getPartijResponse(identificatieType, identificatieNummer, partijRequest);

        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

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
                    description = "Contactgegeven succesvol toegevoegd"
            )
    })
    public Response addContactgegeven(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            ContactgegevenRequest request) {

        partijService.addContactgegeven(identificatieType, identificatieNummer, request);

        URI uri = URI.create(String.format("/contactgegeven/%s/%s", identificatieType, identificatieNummer));
        return Response.created(uri).build();
    }

    /**
     * Update een bestaand contactgegeven van een partij.
     * IdentificatieType en Nummer kunnen niet gewijzigd worden.
     * Alleen type, waarde en afdeling kunnen worden geüpdatet.
     */
    @PUT
    @Path("/contactgegeven/{identificatieType}/{identificatieNummer}/")
    @Transactional
    @Operation(
            summary = "Update contactgegeven van een partij",
            description = "Werk type, waarde en afdeling van een contactgegeven bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Contactgegeven succesvol bijgewerkt"),
            @APIResponse(responseCode = "404", description = "Contactgegeven of partij niet gevonden")
    })
    public Response updateContactgegeven(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            ContactgegevenUpdateRequest request) {

        boolean updated = partijService.updateContactgegeven(identificatieType, identificatieNummer, request);

        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

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
    public Response deleteContactgegeven(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @PathParam("contactgegevenId") Long contactgegevenId) {

        boolean deleted = partijService.deleteContactgegeven(identificatieType, identificatieNummer, contactgegevenId);

        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

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
                    description = "Voorkeur succesvol toegevoegd"
            )
    })
    public Response addVoorkeur(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            VoorkeurRequest request) {

        partijService.addVoorkeur(identificatieType, identificatieNummer, request);

        URI uri = URI.create(String.format("/%s/%s", identificatieType, identificatieNummer));
        return Response.created(uri).build();
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
            description = "Werk type, waarde en afdeling van een voorkeur bij. Identificatie kan niet aangepast worden."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voorkeur succesvol bijgewerkt"),
            @APIResponse(responseCode = "404", description = "Voorkeur of partij niet gevonden")
    })
    public Response updateVoorkeur(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            VoorkeurUpdateRequest request) {

        boolean updated = partijService.updateVoorkeur(identificatieType, identificatieNummer, request);

        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

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
    public Response deleteVoorkeur(
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @PathParam("identificatieNummer") String identificatieNummer,
            @PathParam("voorkeurId") Long voorkeurId) {

        boolean deleted = partijService.deleteVoorkeur(identificatieType, identificatieNummer, voorkeurId);

        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.noContent().build();
    }
}