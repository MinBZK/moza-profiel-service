package nl.rijksoverheid.moz.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import nl.rijksoverheid.moz.dto.response.DienstResponse;
import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.IdentificatieResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.ScopeResponse;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Scope;
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
        cr.taal = cg.getTaal();
        cr.terAttentieVan = cg.getTerAttentieVan();
        cr.isGeverifieerd = cg.getGeverifieerdAt() != null;
        cr.scope = toScopeResponse(cg.getScope());
        return cr;
    }

    private VoorkeurResponse toVoorkeurResponse(Voorkeur voorkeur) {
        VoorkeurResponse vr = new VoorkeurResponse();
        vr.id = voorkeur.id;
        vr.voorkeurType = voorkeur.getVoorkeurType();
        vr.waarde = voorkeur.getWaarde();
        vr.scope = toScopeResponse(voorkeur.getScope());
        return vr;
    }

    private ScopeResponse toScopeResponse(Scope scope) {
        if (scope == null) {
            return null;
        }

        ScopeResponse sr = new ScopeResponse();

        if (scope.getPartij() != null && !scope.getPartij().getIdentificaties().isEmpty()) {
            sr.partij = toIdentificatieResponse(scope.getPartij().getIdentificaties().getFirst());
        }

        if (scope.getDienst() != null) {
            sr.dienst = new DienstResponse(scope.getDienst());
        }

        return sr;
    }
}
