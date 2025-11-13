
package nl.rijksoverheid.moz.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.common.ContactvoorkeurType;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.response.BetreffendePartijResponse;
import nl.rijksoverheid.moz.dto.response.EigenPartijResponse;
import nl.rijksoverheid.moz.entity.Contactvoorkeur;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.net.URI;

/**
 * REST Controller voor het beheren van partijen en hun contactvoorkeuren.
 * <p>
 * Deze controller biedt endpoints voor:
 * <ul>
 *   <li>Ophalen van contactvoorkeuren als eigenaar of betreffende</li>
 *   <li>Aanmaken, wijzigen en verwijderen van contactvoorkeuren</li>
 *   <li>Delete van partijen</li>
 * </ul>
 */
@Path("/api/profielservice/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Profiel", description = "Endpoints voor het beheren van partijen en contactvoorkeuren")
public class ProfielController {

    @Inject
    PartijMapper partijMapper;

    /**
     * Haalt alle contactvoorkeuren op waar de opgegeven partij eigenaar van is.
     * <p>
     * Een eigenaar is de partij die de contactvoorkeur heeft aangemaakt en beheert.
     * Verwijderde partijen worden niet geretourneerd (404).
     *
     * @param identificatieType Type identificatie (BSN, KVK, RSIN)
     * @param identificatieNummer Het unieke identificatienummer van de partij
     * @return Response met EigenPartijResponse of 404 als de partij niet bestaat
     */
    @GET
    @Path("/contactvoorkeuren/eigenaar/{identificatieType}/{identificatieNummer}")
    @Operation(
            summary = "Ophalen contactvoorkeuren van eigenaar",
            description = "Haalt alle contactvoorkeuren op waarvan de opgegeven partij eigenaar is"
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Contactvoorkeuren succesvol opgehaald",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EigenPartijResponse.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Partij niet gevonden of is verwijderd"
            )
    })
    public Response getContactvoorkeurenEigenaar(
            @Parameter(description = "Type identificatie (BSN, KVK, RSIN)", required = true)
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @Parameter(description = "Het identificatienummer", required = true)
            @PathParam("identificatieNummer") String identificatieNummer) {

        Partij partij = Partij.findByIdentificatieNummer(identificatieType, identificatieNummer);
        if (partij == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EigenPartijResponse response = partijMapper.toEigenaarPartijResponse(partij);
        return Response.ok(response).build();
    }

    /**
     * Haalt alle contactvoorkeuren op waar de opgegeven partij betreffende van is.
     * <p>
     * Een betreffende is de partij waar de contactvoorkeur over gaat.
     * Bijvoorbeeld: een gemachtigde (eigenaar) beheert contactvoorkeuren voor een burger (betreffende).
     * Verwijderde partijen worden niet geretourneerd (404).
     *
     * @param identificatieType Type identificatie (BSN, KVK, RSIN)
     * @param identificatieNummer Het unieke identificatienummer van de partij
     * @return Response met BetreffendePartijResponse of 404 als de partij niet bestaat
     */
    @GET
    @Path("/contactvoorkeuren/betreffende/{identificatieType}/{identificatieNummer}")
    @Operation(
            summary = "Ophalen contactvoorkeuren van betreffende",
            description = "Haalt alle contactvoorkeuren op waarvan de opgegeven partij betreffende is"
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Contactvoorkeuren succesvol opgehaald",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BetreffendePartijResponse.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Partij niet gevonden of is verwijderd"
            )
    })
    public Response getContactvoorkeurenBetreffende(
            @Parameter(description = "Type identificatie (BSN, KVK, RSIN)", required = true)
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @Parameter(description = "Het identificatienummer", required = true)
            @PathParam("identificatieNummer") String identificatieNummer) {

        Partij partij = Partij.findByIdentificatieNummer(identificatieType, identificatieNummer);
        if (partij == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BetreffendePartijResponse response = partijMapper.toBetreffendePartijResponse(partij);
        return Response.ok(response).build();
    }

    /**
     * Voegt een nieuwe contactvoorkeur toe voor een partij.
     * <p>
     * Als de eigenaar of betreffende partij nog niet bestaat, wordt deze automatisch aangemaakt.
     * De contactvoorkeur wordt aangemaakt met status 'niet geverifieerd' (geverifieerdAt = null).
     * <p>
     * <b>Toekomstige implementatie:</b>
     * <ul>
     *   <li>Eigenaar moet worden bepaald uit authentication token/certificaat</li>
     *   <li>Validatie of eigenaar mag handelen voor de betreffende partij</li>
     *   <li>Email/sms verificatie versturen na aanmaken</li>
     * </ul>
     *
     * @param identificatieType Type identificatie van de eigenaar partij
     * @param identificatieNummer Identificatienummer van de eigenaar partij
     * @param request Request body met contactvoorkeur gegevens (betreffende partij, scope, type, waarde, etc.)
     * @return Response 201 Created met Location header naar de aangemaakte resource
     */
    @POST
    @Path("/contactvoorkeur/{identificatieType}/{identificatieNummer}")
    @Transactional
    @ResponseStatus(201)
    @Operation(
            summary = "Toevoegen nieuwe contactvoorkeur",
            description = "Voegt een nieuwe contactvoorkeur toe. Creëert automatisch ontbrekende partijen."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Contactvoorkeur succesvol toegevoegd"
            )
    })
    public Response addContactvoorkeur(
            @Parameter(description = "Type identificatie van de eigenaar partij", required = true)
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @Parameter(description = "Identificatienummer van de eigenaar partij", required = true)
            @PathParam("identificatieNummer") String identificatieNummer,
            @Parameter(description = "Request body met contactvoorkeur gegevens", required = true)
            PartijRequest request) {

        // TODO: Eigenaar moet uit authentication token/certificaat komen
        Partij eigenaar = findOrCreatePartij(identificatieType, identificatieNummer);

        // TODO: Valideren of eigenaar mag handelen voor de betreffende partij
        Partij betreffende = findOrCreatePartij(request.identificatieType, request.identificatieNummer);

        Contactvoorkeur voorkeur = new Contactvoorkeur();
        voorkeur.setEigenaarPartij(eigenaar);
        voorkeur.setBetreffendePartij(betreffende);
        voorkeur.setScope(request.scope);
        voorkeur.setDienstType(request.dienstType);
        voorkeur.setType(request.type);
        voorkeur.setWaarde(request.waarde);
        voorkeur.setGeverifieerdAt(null);
        voorkeur.persist();

        // TODO: Email/SMS verificatie versturen en validatie endpoint implementeren
        URI uri = URI.create(String.format("/contactvoorkeuren/eigenaar/%s/%s", identificatieType, identificatieNummer));
        return Response.created(uri).build();
    }

    /**
     * Wijzigt het type en/of waarde van een bestaande contactvoorkeur.
     * <p>
     * Na wijziging wordt de contactvoorkeur als 'niet geverifieerd' gemarkeerd.
     * <p>
     * <b>Toekomstige implementatie:</b>
     * <ul>
     *   <li>Verificatie dat de contactvoorkeur bij de geauthenticeerde gebruiker hoort</li>
     *   <li>Nieuwe verificatie Email/sms versturen na wijziging</li>
     * </ul>
     *
     * @param id ID van de contactvoorkeur
     * @param type Nieuw type van de contactvoorkeur (EMAIL, sms, etc.)
     * @param waarde Nieuwe waarde van de contactvoorkeur (e-mailadres, telefoonnummer, etc.)
     * @return Response 204 No Content bij succes
     */
    @PATCH
    @Path("/contactvoorkeur/{id}")
    @Transactional
    @ResponseStatus(204)
    @Operation(
            summary = "Wijzigen contactvoorkeur",
            description = "Wijzigt het type en/of waarde van een bestaande contactvoorkeur. Vereist nieuwe verificatie."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "Contactvoorkeur succesvol gewijzigd"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Contactvoorkeur niet gevonden"
            )
    })
    public Response updateContactvoorkeur(
            @Parameter(description = "ID van de contactvoorkeur", required = true)
            @PathParam("id") Long id,
            @Parameter(description = "Nieuw type van de contactvoorkeur", required = true)
            @QueryParam("type") ContactvoorkeurType type,
            @Parameter(description = "Nieuwe waarde van de contactvoorkeur", required = true)
            @QueryParam("waarde") String waarde) {

        // TODO: Verificatie dat contactvoorkeur bij geauthenticeerde gebruiker hoort
        Contactvoorkeur voorkeur = Contactvoorkeur.findById(id);
        if (voorkeur == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        voorkeur.setType(type);
        voorkeur.setWaarde(waarde);
        voorkeur.setGeverifieerdAt(null); // Reset verification status

        // TODO: Nieuwe verificatie email/SMS versturen

        return Response.noContent().build();
    }

    /**
     * Verwijdert een contactvoorkeur permanent uit het systeem.
     * <p>
     * <b>Let op:</b> Dit is een harde delete, de contactvoorkeur wordt definitief verwijderd.
     * <p>
     * <b>Toekomstige implementatie:</b>
     * <ul>
     *   <li>Verificatie dat de contactvoorkeur bij de geauthenticeerde gebruiker hoort</li>
     * </ul>
     *
     * @param id ID van de contactvoorkeur
     * @return Response 204 No Content bij succes
     */
    @DELETE
    @Path("/contactvoorkeur/{id}")
    @Transactional
    @ResponseStatus(204)
    @Operation(
            summary = "Verwijderen contactvoorkeur",
            description = "Verwijdert een contactvoorkeur permanent uit het systeem"
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "Contactvoorkeur succesvol verwijderd"
            )
    })
    public Response deleteContactvoorkeur(
            @Parameter(description = "ID van de contactvoorkeur", required = true)
            @PathParam("id") Long id) {

        // TODO: Verificatie dat contactvoorkeur bij geauthenticeerde gebruiker hoort
        Contactvoorkeur.delete("id = ?1", id);
        return Response.noContent().build();
    }

    /**
     * Verwijderd partij uit het database.
     * <b>Toekomstige implementatie:</b>
     * <ul>
     *   <li>Verificatie dat de partij bij de geauthenticeerde gebruiker hoort</li>
     * </ul>
     *
     * @param identificatieType Type identificatie (BSN, KVK, RSIN)
     * @param identificatieNummer Het unieke identificatienummer van de partij
     * @return Response 204 No Content bij succes
     */
    @DELETE
    @Path("/partij/{identificatieType}/{identificatieNummer}")
    @Transactional
    @ResponseStatus(204)
    @Operation(
            summary = "Delete partij",
            description = "Verwijderd de partij."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "Partij succesvol verwijderd"
            )
    })
    public Response deletePartij(
            @Parameter(description = "Type identificatie (BSN, KVK, RSIN)", required = true)
            @PathParam("identificatieType") IdentificatieType identificatieType,
            @Parameter(description = "Het identificatienummer", required = true)
            @PathParam("identificatieNummer") String identificatieNummer) {

        // TODO: Verificatie dat partij bij geauthenticeerde gebruiker hoort
        // TODO: Implement actual soft delete by setting deletedAt timestamp instead of hard delete
        Partij.delete("identificatieType = ?1 and identificatieNummer = ?2", identificatieType, identificatieNummer);
        return Response.noContent().build();
    }

    /**
     * Helper methode om een partij op te halen of aan te maken als deze niet bestaat.
     *
     * @param identificatieType Type identificatie van de partij
     * @param identificatieNummer Identificatienummer van de partij
     * @return Bestaande of nieuw aangemaakte Partij
     */
    private Partij findOrCreatePartij(IdentificatieType identificatieType, String identificatieNummer) {
        Partij partij = Partij.findByIdentificatieNummer(identificatieType, identificatieNummer);
        if (partij == null) {
            partij = new Partij();
            partij.setIdentificatieType(identificatieType);
            partij.setIdentificatieNummer(identificatieNummer);
            partij.persist();
        }
        return partij;
    }
}