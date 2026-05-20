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
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class PartijService {

    private static final Logger LOG = Logger.getLogger(PartijService.class);

    @Inject
    PartijMapper partijMapper;

    @Inject
    EmailVerificatieService emailVerificatieService;

    @Inject
    DienstverlenerService dienstverlenerService;

    public record AddContactgegevenResult(Contactgegeven contactgegeven, boolean wasCreated, boolean scopeAdded) {}

    public record AddVoorkeurResult(Voorkeur voorkeur, boolean wasCreated, boolean scopeAdded) {}

    @Transactional
    public AddContactgegevenResult addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        DienstverlenerDienst link = resolveDienstverlenerDienst(request.scope);

        String normalisedWaarde = request.type == ContactType.Email
                ? request.waarde.toLowerCase(Locale.ROOT)
                : request.waarde;

        Contactgegeven existing = Contactgegeven.find(
                "partij = ?1 AND type = ?2 AND waarde = ?3",
                partij, request.type, normalisedWaarde
        ).firstResult();

        if (existing != null) {
            if (link == null || hasContactgegevenScopeFor(existing.getScopes(), link)) {
                return new AddContactgegevenResult(existing, false, false);
            }
            existing.addScope(new ScopeContactgegeven(existing, link));
            return new AddContactgegevenResult(existing, false, true);
        }

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);
        contactgegeven.setType(request.type);
        contactgegeven.setWaarde(normalisedWaarde);
        contactgegeven.setGeverifieerdAt(null);

        if (request.type == ContactType.Email) {
            String referenceId = emailVerificatieService.requestEmailVerificationCode(normalisedWaarde);
            contactgegeven.setVerificatieReferentieId(referenceId);
        }

        if (link != null) {
            contactgegeven.addScope(new ScopeContactgegeven(contactgegeven, link));
        }

        contactgegeven.persist();
        return new AddContactgegevenResult(contactgegeven, true, false);
    }

    @Transactional
    public AddVoorkeurResult addVoorkeur(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            VoorkeurRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        DienstverlenerDienst link = resolveDienstverlenerDienst(request.scope);

        // Voorkeur-invariant per 08-data.md: één rij per (partij, voorkeurType, scope).
        // POST is daarmee upsert: zelfde sleutel + nieuwe waarde overschrijft, geen tweede rij.
        Voorkeur existing = findExistingVoorkeur(partij, request.voorkeurType, link);

        if (existing != null) {
            if (!Objects.equals(existing.getWaarde(), request.waarde)) {
                existing.setWaarde(request.waarde);
            }
            return new AddVoorkeurResult(existing, false, false);
        }

        Voorkeur voorkeur = new Voorkeur();
        voorkeur.setPartij(partij);
        voorkeur.setVoorkeurType(request.voorkeurType);
        voorkeur.setWaarde(request.waarde);

        if (link != null) {
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));
        }

        voorkeur.persist();
        return new AddVoorkeurResult(voorkeur, true, false);
    }

    private Voorkeur findExistingVoorkeur(Partij partij, nl.rijksoverheid.moz.common.VoorkeurType voorkeurType, DienstverlenerDienst link) {
        if (link == null) {
            // Scope-loze voorkeur: maximaal één rij per (partij, voorkeurType) zonder ScopeVoorkeur.
            return Voorkeur.find(
                    "partij = ?1 AND voorkeurType = ?2 AND size(scopes) = 0",
                    partij, voorkeurType
            ).firstResult();
        }
        // Scoped voorkeur: maximaal één rij per (partij, voorkeurType, dienstverlenerDienst).
        return Voorkeur.find(
                "select distinct v from Voorkeur v join v.scopes s "
                        + "where v.partij = ?1 AND v.voorkeurType = ?2 AND s.dienstverlenerDienst = ?3",
                partij, voorkeurType, link
        ).firstResult();
    }

    private boolean hasContactgegevenScopeFor(List<ScopeContactgegeven> existing, DienstverlenerDienst link) {
        return existing.stream().anyMatch(s -> Objects.equals(s.getDienstverlenerDienst().id, link.id));
    }

private DienstverlenerDienst resolveDienstverlenerDienst(ScopeRequest scope) {
        if (scope == null) {
            return null;
        }

        if (scope.dienstverlenerNaam == null) {
            if (scope.dienstNaam != null) {
                throw new WebApplicationException(
                        "dienstNaam zonder dienstverlenerNaam is ongeldig",
                        Response.Status.BAD_REQUEST);
            }
            return null;
        }

        Dienstverlener dienstverlener = dienstverlenerService.getDienstverlener(scope.dienstverlenerNaam);
        if (dienstverlener == null) {
            throw new WebApplicationException(
                    "Dienstverlener met naam " + scope.dienstverlenerNaam + " bestaat niet",
                    Response.Status.NOT_FOUND);
        }

        if (scope.dienstNaam == null) {
            return dienstverlenerService.findOrCreateDienstverlenerDienst(dienstverlener, null);
        }

        DienstverlenerDienst link = DienstverlenerDienst.find(
                "dienstverlener = ?1 AND lower(dienst.naam) = lower(?2)",
                dienstverlener, scope.dienstNaam
        ).firstResult();

        if (link == null) {
            throw new WebApplicationException(
                    "Dienst '" + scope.dienstNaam + "' bestaat niet voor dienstverlener '"
                            + scope.dienstverlenerNaam + "'",
                    Response.Status.NOT_FOUND);
        }

        return link;
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

    public Partij getPartij(IdentificatieType identificatieType, String identificatieNummer) {
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

        ContactType oldType = contact.getType();
        String oldWaarde = contact.getWaarde();
        boolean wasDefault = contact.isIsDefault();
        // Resolve target isDefault: null = no change, else use the request value.
        boolean targetDefault = request.isDefault != null ? request.isDefault : wasDefault;

        String newWaarde = request.type == ContactType.Email
                ? request.waarde.toLowerCase(Locale.ROOT)
                : request.waarde;

        boolean valueChanged = !Objects.equals(oldType, request.type)
                || !Objects.equals(oldWaarde, newWaarde);

        if (valueChanged && newWaarde != null && existingDuplicateExists(partij, request.type, newWaarde, contact.id)) {
            throw new WebApplicationException(
                    "Combinatie (type, waarde) bestaat al voor deze partij",
                    Response.Status.CONFLICT);
        }

        // Demote BEFORE mutating contact. Hibernate flushes dirty entities before bulk JPQL
        // updates touching the same table; mutating first would trip the partial unique index
        // before the demote could clear the slot.
        if (targetDefault) {
            demoteCurrentDefault(partij, request.type, contact.id);
        }

        contact.setType(request.type);
        contact.setWaarde(newWaarde);
        replaceScopesContactgegeven(contact, resolveDienstverlenerDienst(request.scope));

        // Email verification: re-issue only when the email value actually changes (or the type
        // changes into Email from something else). Re-verifying on every PUT would force a
        // verified email to lose its status whenever the user only flips isDefault or a scope.
        boolean becomesEmail = request.type == ContactType.Email && oldType != ContactType.Email;
        boolean emailValueChanged = request.type == ContactType.Email
                && oldType == ContactType.Email
                && !Objects.equals(oldWaarde, newWaarde);

        if (becomesEmail || emailValueChanged) {
            String referenceId = emailVerificatieService.requestEmailVerificationCode(newWaarde);
            contact.setVerificatieReferentieId(referenceId);
            contact.setGeverifieerdAt(null);
            contact.setIsGeverifieerd(false);
        } else if (request.type != ContactType.Email && oldType == ContactType.Email) {
            // Type changed away from Email: stale verification fields no longer apply.
            contact.setVerificatieReferentieId(null);
            contact.setGeverifieerdAt(null);
            contact.setIsGeverifieerd(false);
        }

        contact.setIsDefault(targetDefault);
        return true;
    }

    private boolean existingDuplicateExists(Partij partij, ContactType type, String waarde, UUID exceptId) {
        return Contactgegeven.find(
                "partij = ?1 AND type = ?2 AND waarde = ?3 AND id <> ?4",
                partij, type, waarde, exceptId
        ).firstResultOptional().isPresent();
    }

    private void demoteCurrentDefault(Partij partij, ContactType type, UUID exceptId) {
        // Bulk JPQL update bypasst @PreUpdate, dus lastUpdated wordt expliciet meegebumped
        // zodat de gedemoveerde rij dezelfde audit-stempel krijgt als een entity-update.
        // Persistence context is op deze plek nog niet gewarmd voor andere defaults van
        // dezelfde (partij, type), dus stale-state risico is hier in de praktijk afwezig.
        // Afhankelijkheid: Hibernate FlushModeType.AUTO (default), waarmee dirty entities
        // worden geflusht voordat een JPQL bulk-update tegen dezelfde tabel uitvoert.
        // Met flushmode=COMMIT zou deze demote-vóór-mutatie volgorde niet meer beschermen
        // tegen de partial unique index op (partij_id, type) WHERE is_default = true.
        Contactgegeven.update(
                "isDefault = false, lastUpdated = ?1 where partij = ?2 and type = ?3 and isDefault = true and id <> ?4",
                java.time.Instant.now(), partij, type, exceptId);
    }

    @Transactional
    public boolean updateVoorkeur(IdentificatieType identificatieType, String identificatieNummer, VoorkeurUpdateRequest request) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Voorkeur voorkeur = partij.getVoorkeuren().stream()
                .filter(v -> v.id.equals(request.id))
                .findFirst()
                .orElse(null);

        if (voorkeur == null) {
            return false;
        }

        DienstverlenerDienst targetLink = resolveDienstverlenerDienst(request.scope);
        Voorkeur collision = findExistingVoorkeur(partij, request.voorkeurType, targetLink);
        if (collision != null && !collision.id.equals(voorkeur.id)) {
            throw new WebApplicationException(
                    "Andere voorkeur bestaat al voor deze partij + type + scope",
                    Response.Status.CONFLICT);
        }

        voorkeur.setVoorkeurType(request.voorkeurType);
        voorkeur.setWaarde(request.waarde);
        replaceScopesVoorkeur(voorkeur, targetLink);
        return true;
    }

    private void replaceScopesContactgegeven(Contactgegeven owner, DienstverlenerDienst link) {
        owner.clearScopes();
        if (link != null) {
            owner.addScope(new ScopeContactgegeven(owner, link));
        }
    }

    private void replaceScopesVoorkeur(Voorkeur owner, DienstverlenerDienst link) {
        owner.clearScopes();
        if (link != null) {
            owner.addScope(new ScopeVoorkeur(owner, link));
        }
    }

    @Transactional
    public boolean deleteContactgegeven(IdentificatieType identificatieType, String identificatieNummer, UUID contactgegevenId) {
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
    public boolean deleteVoorkeur(IdentificatieType identificatieType, String identificatieNummer, UUID voorkeurId) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Voorkeur voorkeur = partij.getVoorkeuren().stream()
                .filter(v -> v.id.equals(voorkeurId))
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
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return null;

        if (partijRequest.isEmpty()) {
            return partijMapper.toResponse(partij);
        }

        List<Contactgegeven> filteredContacts = findFilteredContactgegevens(partij, partijRequest);
        List<Voorkeur> filteredVoorkeuren = findFilteredVoorkeuren(partij, partijRequest);
        return partijMapper.toResponse(partij, filteredContacts, filteredVoorkeuren);
    }

    public List<Contactgegeven> findFilteredContactgegevens(Partij partij, PartijRequest request) {
        StringBuilder query = new StringBuilder(
                "select distinct c from Contactgegeven c " +
                "left join c.scopes s " +
                "left join s.dienstverlenerDienst dd " +
                "left join dd.dienst d " +
                "left join dd.dienstverlener dv " +
                "where c.partij = :partij"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partij", partij);

        if (request.dienstverlener != null) {
            query.append(" AND (s IS NULL OR lower(dv.naam) = lower(:dvNaam))");
            params.put("dvNaam", request.dienstverlener);
        }

        if (request.dienstNaam != null) {
            // Unscoped row (default voor alle diensten) en DV-brede scopes (dd.dienst IS NULL)
            // matchen ook, naast scopes die expliciet op dezelfde dienst-naam wijzen.
            query.append(" AND (s IS NULL OR d IS NULL OR lower(d.naam) = lower(:dienstNaam))");
            params.put("dienstNaam", request.dienstNaam);
        }

        PanacheQuery<Contactgegeven> panacheQuery = Contactgegeven.find(query.toString(), params);
        return panacheQuery.list();
    }

    public List<Voorkeur> findFilteredVoorkeuren(Partij partij, PartijRequest request) {
        StringBuilder query = new StringBuilder(
                "select distinct v from Voorkeur v "
                        + "left join v.scopes s "
                        + "left join s.dienstverlenerDienst dd "
                        + "left join dd.dienst d "
                        + "left join dd.dienstverlener dv "
                        + "where v.partij = :partij"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partij", partij);

        if (request.dienstverlener != null) {
            query.append(" AND (s IS NULL OR lower(dv.naam) = lower(:dvNaam))");
            params.put("dvNaam", request.dienstverlener);
        }

        if (request.dienstNaam != null) {
            query.append(" AND (s IS NULL OR d IS NULL OR lower(d.naam) = lower(:dienstNaam))");
            params.put("dienstNaam", request.dienstNaam);
        }

        return Voorkeur.<Voorkeur>find(query.toString(), params).list();
    }
}
