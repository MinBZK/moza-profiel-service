package nl.rijksoverheid.moz.services;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.response.AfdelingResponse;
import nl.rijksoverheid.moz.dto.response.ContactgegevensVoorPartijResponse;
import nl.rijksoverheid.moz.dto.response.IdentificatieResponse;
import nl.rijksoverheid.moz.dto.response.ResponseVoorPartij;
import nl.rijksoverheid.moz.entity.Afdeling;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;

@ApplicationScoped
public class PartijService {

    @Transactional
    public void addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        // Partij ophalen of aanmaken
        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);

        // Nieuwe Contactgegeven aanmaken
        Contactgegeven cg = new Contactgegeven();
        cg.setPartij(partij);

        Afdeling afdeling = Afdeling.findByBeschrijving(request.afdeling);

        cg.setAfdeling(afdeling);
        cg.setType(request.type);
        cg.setWaarde(request.waarde);
        cg.setGeverifieerdAt(null);
        cg.persist();

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

    public ResponseVoorPartij getContactvoorkeurenVoorPartij(
            IdentificatieType type,
            String nummer
    ) {
        Partij partij = Partij.findByIdentificatie(type, nummer);
        if (partij == null) {
            return null;
        }

        return buildResponseVoorPartij(partij);
    }


    private ResponseVoorPartij buildResponseVoorPartij(Partij partij) {
        ResponseVoorPartij response = new ResponseVoorPartij();
        response.partijId = partij.id;

        // Identificaties
        response.identificaties = partij.getIdentificaties().stream()
                .map(id -> {
                    IdentificatieResponse ir = new IdentificatieResponse();
                    ir.identificatieType = id.getIdentificatieType();
                    ir.identificatieNummer = id.getIdentificatieNummer();
                    return ir;
                })
                .toList();

        // Contactgegevens
        response.contactgegevens = partij.getContactgegevens().stream()
                .map(cg -> {
                    ContactgegevensVoorPartijResponse cgr = new ContactgegevensVoorPartijResponse();
                    cgr.id = cg.id;
                    cgr.type = cg.getType();
                    cgr.waarde = cg.getWaarde();
                    cgr.isGeverifieerd = cg.getGeverifieerdAt() != null;

                    if (cg.getAfdeling() != null) {
                        AfdelingResponse ar = new AfdelingResponse();
                        ar.beschrijving = cg.getAfdeling().getBeschrijving();
                        cgr.afdeling = ar;
                    }
                    return cgr;
                })
                .toList();

        return response;
    }

    @Transactional
    public boolean updateContactgegeven(IdentificatieType identificatieType, String identificatieNummer, ContactgegevenUpdateRequest request) {
        Identificatie identificatie = Identificatie.find(
                "identificatieType = :identificatieType and identificatieNummer = :identificatieNummer",
                Parameters.with("identificatieType", identificatieType)
                        .and("identificatieNummer", identificatieNummer)
        ).firstResult();

        if (identificatie == null) {
            return false;
        }

        Partij partij = identificatie.getPartij();
        if (partij == null) {
            return false;
        }

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
    public boolean deleteContactgegeven(IdentificatieType identificatieType, String identificatieNummer, Long contactgegevenId) {
        Identificatie identificatie = Identificatie.find(
                "identificatieType = :identificatieType and identificatieNummer = :identificatieNummer",
                Parameters.with("identificatieType", identificatieType)
                        .and("identificatieNummer", identificatieNummer)
        ).firstResult();

        if (identificatie == null) return false;

        Partij partij = identificatie.getPartij();
        if (partij == null) {
            return false;
        }

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
}
