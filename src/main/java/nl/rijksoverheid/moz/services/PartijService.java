package nl.rijksoverheid.moz.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;

@ApplicationScoped
public class PartijService {

    @Transactional
    public void addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);

        Afdeling afdeling = Afdeling.findByBeschrijving(request.afdeling);

        contactgegeven.setAfdeling(afdeling);
        contactgegeven.setType(request.type);
        contactgegeven.setWaarde(request.waarde);
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
        contact.setAfdeling(Afdeling.findByBeschrijving((request.afdeling)));

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

        partij.getContactgegevens().remove(contact);

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

        partij.getVoorkeuren().remove(voorkeur);

        voorkeur.delete();
        return true;
    }
}
