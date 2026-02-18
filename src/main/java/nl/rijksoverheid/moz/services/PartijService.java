package nl.rijksoverheid.moz.services;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.mapper.PartijMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PartijService {


    @Inject
    PartijMapper partijMapper;


    @Inject
    EmailVerificatieService emailVerificatieService;


    @Transactional
    public void addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);

        Afdeling afdeling = Afdeling.findById(request.afdelingId);

        contactgegeven.setAfdeling(afdeling);
        contactgegeven.setType(request.type);
        contactgegeven.setWaarde(request.waarde);

        if (request.type == ContactType.Email) {
            //todo bepaal wat we doen als het versturen van een verificatie code mislukt
            emailVerificatieService.requestEmailVerificationCode(request.waarde);
        }

        contactgegeven.setGeverifieerdAt(null);
        contactgegeven.persist();

    }

    @Transactional
    public void addVoorkeur(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            VoorkeurRequest request) {

        // Partij ophalen of aanmaken
        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);

        // Nieuwe Contactgegeven aanmaken
        Voorkeur voorkeur = new Voorkeur();
        voorkeur.setPartij(partij);

        voorkeur.setVoorkeurType(request.voorkeurType);
        voorkeur.setWaarde(request.waarde);
        voorkeur.persist();

    }

    private Partij findOrCreatePartij(IdentificatieType type, String nummer) {
        Partij partij = Partij.findByIdentificatie(type, nummer);
        if (partij == null) {
            partij = new Partij();
            partij.addIdentificatie(new Identificatie(type, nummer));
            partij.persist();
        }
        return partij;
    }

    public Partij getPartij(
            IdentificatieType identificatieType,
            String identificatieNummer
    ) {
        return Partij.findByIdentificatie(identificatieType, identificatieNummer);
    }

    @Transactional
    public boolean updateContactgegeven(IdentificatieType identificatieType, String identificatieNummer, ContactgegevenUpdateRequest request) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Contactgegeven contact = partij.getContactgegevens().stream()
                .filter(c -> c.id.equals(request.id))
                .findFirst()
                .orElse(null);

        if (contact == null) {
            return false;
        }

        contact.setType(request.type);
        contact.setWaarde(request.waarde);
        contact.setAfdeling(Afdeling.findById((request.afdelingId)));

        if (request.type == ContactType.Email) {
            //todo bepaal wat we doen als het versturen van een verificatie code mislukt
            emailVerificatieService.requestEmailVerificationCode(request.waarde);
        }

        contact.setGeverifieerdAt(null);

        return true;
    }

    @Transactional
    public boolean updateVoorkeur(IdentificatieType identificatieType, String identificatieNummer, VoorkeurUpdateRequest request) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Voorkeur voorkeur = partij.getVoorkeuren().stream()
                .filter(c -> c.id.equals(request.id))
                .findFirst()
                .orElse(null);

        if (voorkeur == null) {
            return false;
        }

        voorkeur.setVoorkeurType(request.voorkeurType);
        voorkeur.setWaarde(request.waarde);

        return true;
    }

    @Transactional
    public boolean deleteContactgegeven(IdentificatieType identificatieType, String identificatieNummer, Long contactgegevenId) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Contactgegeven contact = partij.getContactgegevens().stream()
                .filter(c -> c.id.equals(contactgegevenId))
                .findFirst()
                .orElse(null);

        if (contact == null) {
            return false;
        }

        partij.removeContactgegeven(contact);

        contact.delete();
        return true;
    }

    @Transactional
    public boolean deleteVoorkeur(IdentificatieType identificatieType, String identificatieNummer, Long voorkeurId) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Voorkeur voorkeur = partij.getVoorkeuren().stream()
                .filter(c -> c.id.equals(voorkeurId))
                .findFirst()
                .orElse(null);

        if (voorkeur == null) {
            return false;
        }

        partij.removeVoorkeur(voorkeur);

        voorkeur.delete();
        return true;
    }

    public PartijResponse getPartijResponse(IdentificatieType identificatieType, String identificatieNummer, PartijRequest partijRequest) {
        Partij partij;
        if (partijRequest.isEmpty()) {
            partij = getPartij(identificatieType, identificatieNummer);
        } else {
            partij = getPartijFiltered(identificatieType, identificatieNummer, partijRequest);
        }
        if  (partij == null) return null;

        return partijMapper.toResponse(partij);
    }

    public Partij getPartijFiltered(IdentificatieType idType,
                                    String idNummer,
                                    PartijRequest request) {
        Partij partij = getPartij(idType, idNummer);
        if (partij == null) {
            return null;
        }

        //Left join hier zodat hij altijd de default contactgegevens pakt.
        StringBuilder query = new StringBuilder(
                "select c from Contactgegeven c left join c.afdeling a left join a.dienstverlener d " +
                        "where c.partij.id = :partijId"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partijId", partij.id);

        if (request.dienstverlener != null) {
            query.append(" AND (a IS NULL OR d.naam = :dvNaam)");
            params.put("dvNaam", request.dienstverlener);
        }

        if (request.dienstverlenerOin != null) {
            query.append(" AND (a IS NULL OR d.oin = :oin)");
            params.put("oin", request.dienstverlenerOin);
        }

        if (request.afdelingBeschrijving != null) {
            query.append(" AND (a IS NULL OR a.beschrijving = :afdBeschr)");
            params.put("afdBeschr", request.afdelingBeschrijving);
        }

        PanacheQuery<Contactgegeven> panacheQuery = Contactgegeven.find(query.toString(), params);
        List<Contactgegeven> filtered = panacheQuery.list();

        partij.setContactgegevens(filtered);
        return partij;
    }

}
