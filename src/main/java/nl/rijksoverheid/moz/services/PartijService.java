package nl.rijksoverheid.moz.services;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijRequest;
import nl.rijksoverheid.moz.dto.request.ScopeRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurRequest;
import nl.rijksoverheid.moz.dto.request.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.dto.response.PartijResponse;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Scope;
import nl.rijksoverheid.moz.entity.Scoped;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class PartijService {

    private static final Logger LOG = Logger.getLogger(PartijService.class);

    @Inject
    PartijMapper partijMapper;


    @Inject
    EmailVerificatieService emailVerificatieService;

    public record AddContactgegevenResult(Contactgegeven contactgegeven, boolean wasCreated) {}

    public record AddVoorkeurResult(Voorkeur voorkeur, boolean wasCreated) {}


    @Transactional
    public AddContactgegevenResult addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        Scope candidateScope = buildScope(request.scope);

        Contactgegeven existing = Contactgegeven.find(
                "partij.id = ?1 AND type = ?2 AND waarde = ?3",
                partij.id, request.type, request.waarde
        ).firstResult();

        if (existing != null) {
            if (candidateScope == null || hasMatchingScope(existing.getScopes(), candidateScope)) {
                return new AddContactgegevenResult(existing, false);
            }
            existing.addScope(candidateScope);
            return new AddContactgegevenResult(existing, true);
        }

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);
        contactgegeven.setType(request.type);
        contactgegeven.setWaarde(request.waarde);
        contactgegeven.setGeverifieerdAt(null);

        if (request.type == ContactType.Email) {
            //todo bepaal wat we doen als het versturen van een verificatie code mislukt
            String referenceId = emailVerificatieService.requestEmailVerificationCode(request.waarde);
            contactgegeven.setVerificatieReferentieId(referenceId);
        }

        if (candidateScope != null) {
            contactgegeven.addScope(candidateScope);
        }

        contactgegeven.persist();

        return new AddContactgegevenResult(contactgegeven, true);
    }

    @Transactional
    public AddVoorkeurResult addVoorkeur(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            VoorkeurRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        Scope candidateScope = buildScope(request.scope);

        Voorkeur existing = Voorkeur.find(
                "partij.id = ?1 AND voorkeurType = ?2 AND waarde = ?3",
                partij.id, request.voorkeurType, request.waarde
        ).firstResult();

        if (existing != null) {
            if (candidateScope == null || hasMatchingScope(existing.getScopes(), candidateScope)) {
                return new AddVoorkeurResult(existing, false);
            }
            existing.addScope(candidateScope);
            return new AddVoorkeurResult(existing, true);
        }

        Voorkeur voorkeur = new Voorkeur();
        voorkeur.setPartij(partij);
        voorkeur.setVoorkeurType(request.voorkeurType);
        voorkeur.setWaarde(request.waarde);

        if (candidateScope != null) {
            voorkeur.addScope(candidateScope);
        }

        voorkeur.persist();

        return new AddVoorkeurResult(voorkeur, true);
    }

    private boolean hasMatchingScope(List<Scope> existing, Scope candidate) {
        Long candidateDienstId = candidate.getDienst() != null ? candidate.getDienst().id : null;
        Long candidatePartijId = candidate.getPartij() != null ? candidate.getPartij().id : null;
        return existing.stream().anyMatch(s -> {
            Long sDienstId = s.getDienst() != null ? s.getDienst().id : null;
            Long sPartijId = s.getPartij() != null ? s.getPartij().id : null;
            return Objects.equals(sDienstId, candidateDienstId)
                    && Objects.equals(sPartijId, candidatePartijId);
        });
    }

    private Scope buildScope(ScopeRequest request) {
        if (request == null) {
            return null;
        }

        Partij scopePartij = null;
        if (request.scopeIdentificatieType != null && request.scopeIdentificatieNummer != null) {
            scopePartij = findOrCreatePartij(request.scopeIdentificatieType, request.scopeIdentificatieNummer);
        }

        Dienst dienst = null;
        if (request.dienstId != null) {
            dienst = Dienst.findById(request.dienstId);
            if (dienst == null) {
                throw new WebApplicationException(
                        "Dienst met id " + request.dienstId + " bestaat niet",
                        Response.Status.NOT_FOUND);
            }
        }

        if (scopePartij == null && dienst == null) {
            return null;
        }

        Scope scope = new Scope();
        scope.setPartij(scopePartij);
        scope.setDienst(dienst);
        return scope;
    }

    private Partij findOrCreatePartij(IdentificatieType type, String nummer) {
        Partij partij = Partij.findByIdentificatie(type, nummer);
        if (partij == null) {
            LOG.info("Nieuwe partij aanmaken");
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
        replaceScopes(contact, buildScope(request.scope));

        if (request.type == ContactType.Email) {
            //todo bepaal wat we doen als het versturen van een verificatie code mislukt
            String referenceId = emailVerificatieService.requestEmailVerificationCode(request.waarde);
            contact.setVerificatieReferentieId(referenceId);
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
        replaceScopes(voorkeur, buildScope(request.scope));

        return true;
    }

    private void replaceScopes(Scoped owner, Scope newScope) {
        owner.clearScopes();
        if (newScope != null) {
            owner.addScope(newScope);
        }
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

    @Transactional
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

        StringBuilder query = new StringBuilder(
                "select distinct c from Contactgegeven c left join c.scopes s left join s.dienst d left join d.dienstverlener dv " +
                        "where c.partij.id = :partijId"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partijId", partij.id);

        if (request.dienstverlener != null) {
            query.append(" AND (s IS NULL OR dv.naam = :dvNaam)");
            params.put("dvNaam", request.dienstverlener);
        }

        if (request.dienstverlenerOin != null) {
            query.append(" AND (s IS NULL OR dv.oin = :oin)");
            params.put("oin", request.dienstverlenerOin);
        }

        if (request.dienstBeschrijving != null) {
            query.append(" AND (s IS NULL OR d.beschrijving = :dienstBeschr)");
            params.put("dienstBeschr", request.dienstBeschrijving);
        }

        PanacheQuery<Contactgegeven> panacheQuery = Contactgegeven.find(query.toString(), params);
        List<Contactgegeven> filtered = panacheQuery.list();

        partij.setContactgegevens(filtered);
        return partij;
    }

}
