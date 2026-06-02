package nl.rijksoverheid.moz.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import nl.rijksoverheid.moz.dto.response.ContactgegevenResponse;
import nl.rijksoverheid.moz.dto.response.IdentificatieResponse;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.dto.response.ScopeResponse;
import nl.rijksoverheid.moz.dto.response.VoorkeurResponse;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class PartijMapper {

    private static final Duration LAST_USED_TOUCH_THRESHOLD = Duration.ofHours(24);

    public PartijResponse toResponse(Partij partij) {
        return toResponse(partij, partij.getContactgegevens(), partij.getVoorkeuren());
    }

    public PartijResponse toResponse(
            Partij partij,
            java.util.List<Contactgegeven> contactgegevens,
            java.util.List<Voorkeur> voorkeuren) {
        PartijResponse response = new PartijResponse();
        response.partijId = partij.id;

        response.identificaties = partij.getIdentificaties().stream()
                .map(this::toIdentificatieResponse)
                .toList();

        response.contactgegevens = contactgegevens.stream()
                .map(this::toContactgegevensResponse)
                .toList();

        response.voorkeuren = voorkeuren.stream()
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

    public ContactgegevenResponse toContactgegevensResponse(Contactgegeven cg) {
        if (isStale(cg.getLastUsedAt())) {
            if (cg.isTeVerwijderenOpAutomatisch()) {
                Contactgegeven.update(
                        "lastUsedAt = ?1, teVerwijderenOp = null, teVerwijderenOpAutomatisch = false where id = ?2",
                        Instant.now(), cg.id);
            } else {
                Contactgegeven.update("lastUsedAt = ?1 where id = ?2", Instant.now(), cg.id);
            }
        }
        ContactgegevenResponse cr = new ContactgegevenResponse();
        cr.id = cg.id;
        cr.type = cg.getType();
        cr.waarde = cg.getWaarde();
        cr.isGeverifieerd = cg.isIsGeverifieerd();
        cr.isDefault = cg.isIsDefault();
        cr.createdAt = cg.getCreatedAt();
        cr.lastUpdated = cg.getLastUpdated();
        cr.teVerwijderenOp = cg.getTeVerwijderenOp();
        cr.scopes = cg.getScopes().stream().map(this::toScopeResponseFromContactgegeven).toList();
        return cr;
    }

    public VoorkeurResponse toVoorkeurResponse(Voorkeur voorkeur) {
        if (isStale(voorkeur.getLastUsedAt())) {
            if (voorkeur.isTeVerwijderenOpAutomatisch()) {
                Voorkeur.update(
                        "lastUsedAt = ?1, teVerwijderenOp = null, teVerwijderenOpAutomatisch = false where id = ?2",
                        Instant.now(), voorkeur.id);
            } else {
                Voorkeur.update("lastUsedAt = ?1 where id = ?2", Instant.now(), voorkeur.id);
            }
        }
        VoorkeurResponse vr = new VoorkeurResponse();
        vr.id = voorkeur.id;
        vr.voorkeurType = voorkeur.getVoorkeurType();
        vr.waarde = voorkeur.getWaarde();
        vr.createdAt = voorkeur.getCreatedAt();
        vr.lastUpdated = voorkeur.getLastUpdated();
        vr.teVerwijderenOp = voorkeur.getTeVerwijderenOp();
        vr.scopes = voorkeur.getScopes().stream().map(this::toScopeResponseFromVoorkeur).toList();
        return vr;
    }

    private static boolean isStale(Instant lastUsedAt) {
        return lastUsedAt == null
                || lastUsedAt.plus(LAST_USED_TOUCH_THRESHOLD).isBefore(Instant.now());
    }

    private ScopeResponse toScopeResponseFromContactgegeven(ScopeContactgegeven scope) {
        return toScopeResponse(scope.getDienstverlenerDienst());
    }

    private ScopeResponse toScopeResponseFromVoorkeur(ScopeVoorkeur scope) {
        return toScopeResponse(scope.getDienstverlenerDienst());
    }

    private ScopeResponse toScopeResponse(DienstverlenerDienst link) {
        if (link == null) {
            return null;
        }
        ScopeResponse sr = new ScopeResponse();
        sr.dienstverlenerNaam = link.getDienstverlener() != null ? link.getDienstverlener().getNaam() : null;
        sr.dienstNaam = link.getDienst() != null ? link.getDienst().getNaam() : null;
        return sr;
    }
}
