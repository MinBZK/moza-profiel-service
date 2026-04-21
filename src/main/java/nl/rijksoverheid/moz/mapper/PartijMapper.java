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

import java.time.Duration;
import java.time.LocalDateTime;

@ApplicationScoped
public class PartijMapper {

    private static final Duration LAST_USED_TOUCH_THRESHOLD = Duration.ofHours(24);

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
        if (isStale(cg.getLastUsedAt())) {
            Contactgegeven.update("lastUsedAt = ?1 where id = ?2", LocalDateTime.now(), cg.id);
        }
        ContactgegevenResponse cr = new ContactgegevenResponse();
        cr.id = cg.id;
        cr.type = cg.getType();
        cr.waarde = cg.getWaarde();
        cr.isGeverifieerd = cg.getGeverifieerdAt() != null;
        cr.nogSteedsValide = cg.isNogSteedsValide();
        cr.createdAt = cg.getCreatedAt();
        cr.lastUpdated = cg.getLastUpdated();
        cr.scope = toScopeResponse(cg.getScope());
        return cr;
    }

    private VoorkeurResponse toVoorkeurResponse(Voorkeur voorkeur) {
        if (isStale(voorkeur.getLastUsedAt())) {
            Voorkeur.update("lastUsedAt = ?1 where id = ?2", LocalDateTime.now(), voorkeur.id);
        }
        VoorkeurResponse vr = new VoorkeurResponse();
        vr.id = voorkeur.id;
        vr.voorkeurType = voorkeur.getVoorkeurType();
        vr.waarde = voorkeur.getWaarde();
        vr.createdAt = voorkeur.getCreatedAt();
        vr.lastUpdated = voorkeur.getLastUpdated();
        vr.scope = toScopeResponse(voorkeur.getScope());
        return vr;
    }

    private static boolean isStale(LocalDateTime lastUsedAt) {
        return lastUsedAt == null
                || lastUsedAt.plus(LAST_USED_TOUCH_THRESHOLD).isBefore(LocalDateTime.now());
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
