package nl.rijksoverheid.moz.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import nl.rijksoverheid.moz.dto.response.AfdelingResponse;
import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.IdentificatieResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;

@ApplicationScoped
public class PartijMapper {

    public PartijResponse toResponse(Partij partij) {
        PartijResponse response = new PartijResponse();
        response.partijId = partij.id;

        response.identificaties = partij.getIdentificaties().stream()
                .map(this::toIdentificatieResponse)
                .toList();

        response.contactgegevens = partij.getContactgegevens().stream()
                .map(this::toContactgegevensResponse)
                .toList();

        response.voorkeuren = partij.getVoorkeuren().stream()
                .map(this::toVoorkeurResponse)
                .toList();

        return response;
    }

    private IdentificatieResponse toIdentificatieResponse(Identificatie id) {
        IdentificatieResponse ir = new IdentificatieResponse();
        ir.identificatieType = id.getIdentificatieType();
        ir.identificatieNummer = id.getIdentificatieNummer();
        return ir;
    }

    private ContactgegevenResponse toContactgegevensResponse(Contactgegeven cg) {
        ContactgegevenResponse cr = new ContactgegevenResponse();
        cr.id = cg.id;
        cr.type = cg.getType();
        cr.waarde = cg.getWaarde();
        cr.isGeverifieerd = cg.getGeverifieerdAt() != null;

        if (cg.getAfdeling() != null) {
            AfdelingResponse ar = new AfdelingResponse();
            ar.beschrijving = cg.getAfdeling().getBeschrijving();
            cr.afdeling = ar;
        }
        return cr;
    }

    private VoorkeurResponse toVoorkeurResponse(Voorkeur voorkeur) {
        VoorkeurResponse vr = new VoorkeurResponse();
        vr.id = voorkeur.id;
        vr.voorkeurType = voorkeur.getVoorkeurType();
        vr.waarde = voorkeur.getWaarde();
        return vr;
    }
}
