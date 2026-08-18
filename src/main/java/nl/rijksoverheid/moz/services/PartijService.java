package nl.rijksoverheid.moz.services;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.exception.AuthorizationException;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.BusinessException.Kind;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.ScopeRequest;
import nl.rijksoverheid.moz.api.generated.model.TeVerwijderenOpRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.mapper.PartijMapper;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;

@ApplicationScoped
public class PartijService {

    private static final Logger LOG = Logger.getLogger(PartijService.class);

    private final PartijMapper partijMapper;
    private final EmailVerificatieService emailVerificatieService;
    private final DienstverlenerService dienstverlenerService;

    public PartijService(
            PartijMapper partijMapper,
            EmailVerificatieService emailVerificatieService,
            DienstverlenerService dienstverlenerService) {
        this.partijMapper = partijMapper;
        this.emailVerificatieService = emailVerificatieService;
        this.dienstverlenerService = dienstverlenerService;
    }

    public record AddContactgegevenResult(Contactgegeven contactgegeven, boolean wasCreated, boolean scopeAdded) {}

    public record AddVoorkeurResult(Voorkeur voorkeur, boolean wasCreated, boolean scopeAdded) {}

    @Transactional
    public AddContactgegevenResult addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        DienstverlenerDienst link = resolveDienstverlenerDienst(request.getScope());

        String normalisedWaarde = request.getType() == ContactType.Email
                ? request.getWaarde().toLowerCase(Locale.ROOT)
                : request.getWaarde();

        Contactgegeven existing = Contactgegeven.find(
                "partij = ?1 AND type = ?2 AND waarde = ?3",
                partij, request.getType(), normalisedWaarde
        ).firstResult();
        
        if (existing != null) {
            LOG.info("Contactgegeven al geregistreerd voor deze partij en scope");

            applyTeVerwijderenOp(request.getTeVerwijderenOp(), existing.getLastUsedAt(), existing.getCreatedAt(), existing::setTeVerwijderenOp);

            if (existing.getType() == ContactType.Email && existing.getGeverifieerdAt() == null) {
                requestAndApplyVerificatieCode(existing);
                LOG.info("Contactgegeven al geregistreerd maar nog niet geverifieerd, nieuwe verificatiecode verzonden");
            }

            if (link == null || hasContactgegevenScopeFor(existing.getScopes(), link)) {
                return new AddContactgegevenResult(existing, false, false);
            }
            existing.addScope(new ScopeContactgegeven(existing, link));
            return new AddContactgegevenResult(existing, false, true);
        }

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);
        contactgegeven.setType(request.getType());
        contactgegeven.setWaarde(normalisedWaarde);
        contactgegeven.setGeverifieerdAt(null);

        if (request.getType() == ContactType.Email) {
            requestAndApplyVerificatieCode(contactgegeven);
        }

        applyTeVerwijderenOp(request.getTeVerwijderenOp(), null, Instant.now(), contactgegeven::setTeVerwijderenOp);

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
        DienstverlenerDienst link = resolveDienstverlenerDienst(request.getScope());

        // Voorkeur-invariant per 08-data.md: één rij per (partij, voorkeurType, scope).
        // POST is daarmee upsert: zelfde sleutel + nieuwe waarde overschrijft, geen tweede rij.
        Voorkeur existing = findExistingVoorkeur(partij, request.getVoorkeurType(), link);

        if (existing != null) {
            if (!Objects.equals(existing.getWaarde(), request.getWaarde())) {
                existing.setWaarde(request.getWaarde());
            }

            applyTeVerwijderenOp(request.getTeVerwijderenOp(), existing.getLastUsedAt(), existing.getCreatedAt(), existing::setTeVerwijderenOp);

            return new AddVoorkeurResult(existing, false, false);
        }

        Voorkeur voorkeur = new Voorkeur();
        voorkeur.setPartij(partij);
        voorkeur.setVoorkeurType(request.getVoorkeurType());
        voorkeur.setWaarde(request.getWaarde());

        applyTeVerwijderenOp(request.getTeVerwijderenOp(), null, Instant.now(), voorkeur::setTeVerwijderenOp);

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

    private void requestAndApplyVerificatieCode(Contactgegeven contact) {
        String referenceId = emailVerificatieService.requestEmailVerificationCode(contact.getWaarde());
        contact.setVerificatieReferentieId(referenceId);
        contact.setIsGeverifieerd(false);
    }

    private void applyTeVerwijderenOp(Instant requested, Instant lastUsedAt, Instant createdAt, Consumer<Instant> setter) {
        if (requested == null) return;

        if (!requested.isAfter(Instant.now())) {
            throw new BusinessException(Kind.BAD_REQUEST, "teVerwijderenOp moet in de toekomst liggen");
        }

        Instant referenceDate = lastUsedAt != null ? lastUsedAt : createdAt;
        Instant maxDate = referenceDate.atZone(ZoneOffset.UTC).plus(Period.ofYears(7)).toInstant();

        if (requested.isAfter(maxDate)) {
            throw new BusinessException(Kind.BAD_REQUEST, "teVerwijderenOp mag niet meer dan 7 jaar na de referentiedatum liggen");
        }

        setter.accept(requested);
    }

    private DienstverlenerDienst resolveDienstverlenerDienst(ScopeRequest scope) {
        if (scope == null) {
            return null;
        }

        if (scope.getDienstverlenerNaam() == null) {
            if (scope.getDienstNaam() != null) {
                throw new BusinessException(Kind.BAD_REQUEST,
                        "dienstNaam zonder dienstverlenerNaam is ongeldig");
            }
            return null;
        }

        Dienstverlener dienstverlener = dienstverlenerService.getDienstverlener(scope.getDienstverlenerNaam());
        if (dienstverlener == null) {
            throw new BusinessException(Kind.NOT_FOUND,
                    "Dienstverlener bestaat niet");
        }

        if (scope.getDienstNaam() == null) {
            return dienstverlenerService.findOrCreateDienstverlenerDienst(dienstverlener, null);
        }

        DienstverlenerDienst link = DienstverlenerDienst.find(
                "dienstverlener = ?1 AND lower(dienst.naam) = lower(?2)",
                dienstverlener, scope.getDienstNaam()
        ).firstResult();

        if (link == null) {
            throw new BusinessException(Kind.NOT_FOUND,
                    "Dienst bestaat niet voor deze dienstverlener");
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
                .filter(c -> c.id.equals(request.getId()))
                .findFirst()
                .orElse(null);

        if (contact == null) {
            return false;
        }

        ContactType oldType = contact.getType();
        String oldWaarde = contact.getWaarde();
        boolean wasDefault = contact.isIsDefault();
        // Resolve target isDefault: null = no change, else use the request value.
        boolean targetDefault = request.getIsDefault() != null ? request.getIsDefault() : wasDefault;

        String newWaarde = request.getType() == ContactType.Email
                ? request.getWaarde().toLowerCase(Locale.ROOT)
                : request.getWaarde();

        boolean valueChanged = !Objects.equals(oldType, request.getType())
                || !Objects.equals(oldWaarde, newWaarde);

        if (valueChanged && newWaarde != null && existingDuplicateExists(partij, request.getType(), newWaarde, contact.id)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Combinatie (type, waarde) bestaat al voor deze partij");
        }

        // Demote BEFORE mutating contact. Hibernate flushes dirty entities before bulk JPQL
        // updates touching the same table; mutating first would trip the partial unique index
        // before the demote could clear the slot.
        if (targetDefault) {
            demoteCurrentDefault(partij, request.getType(), contact.id);
        }

        contact.setType(request.getType());
        contact.setWaarde(newWaarde);
        replaceScopesContactgegeven(contact, resolveDienstverlenerDienst(request.getScope()));

        // Email verification: re-issue only when the email value actually changes (or the type
        // changes into Email from something else). Re-verifying on every PUT would force a
        // verified email to lose its status whenever the user only flips isDefault or a scope.
        boolean becomesEmail = request.getType() == ContactType.Email && oldType != ContactType.Email;
        boolean emailValueChanged = request.getType() == ContactType.Email
                && oldType == ContactType.Email
                && !Objects.equals(oldWaarde, newWaarde);

        if (becomesEmail || emailValueChanged) {
            String referenceId = emailVerificatieService.requestEmailVerificationCode(newWaarde);
            contact.setVerificatieReferentieId(referenceId);
            contact.setGeverifieerdAt(null);
            contact.setIsGeverifieerd(false);
        } else if (request.getType() != ContactType.Email && oldType == ContactType.Email) {
            // Type changed away from Email: stale verification fields no longer apply.
            contact.setVerificatieReferentieId(null);
            contact.setGeverifieerdAt(null);
            contact.setIsGeverifieerd(false);
        }

        contact.setIsDefault(targetDefault);
        applyTeVerwijderenOp(request.getTeVerwijderenOp(), contact.getLastUsedAt(), contact.getCreatedAt(), contact::setTeVerwijderenOp);

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
                .filter(v -> v.id.equals(request.getId()))
                .findFirst()
                .orElse(null);

        if (voorkeur == null) {
            return false;
        }

        DienstverlenerDienst targetLink = resolveDienstverlenerDienst(request.getScope());
        Voorkeur collision = findExistingVoorkeur(partij, request.getVoorkeurType(), targetLink);
        if (collision != null && !collision.id.equals(voorkeur.id)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Andere voorkeur bestaat al voor deze partij + type + scope");
        }

        voorkeur.setVoorkeurType(request.getVoorkeurType());
        voorkeur.setWaarde(request.getWaarde());
        replaceScopesVoorkeur(voorkeur, targetLink);
        applyTeVerwijderenOp(request.getTeVerwijderenOp(), voorkeur.getLastUsedAt(), voorkeur.getCreatedAt(), voorkeur::setTeVerwijderenOp);

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
    public boolean updateVoorkeurTeVerwijderenOpByDienstverlener(TeVerwijderenOpRequest request) {
        Partij partij = getPartij(request.getIdentificatieType(), request.getIdentificatieNummer());
        if (partij == null) return false;

        Voorkeur voorkeur = partij.getVoorkeuren().stream()
                .filter(v -> v.id.equals(request.getId()))
                .findFirst()
                .orElse(null);
        if (voorkeur == null) return false;

        requireDienstverlenerAuthorized(voorkeur.getScopes().stream()
                .map(ScopeVoorkeur::getDienstverlenerDienst)
                .toList(), request, "Dienstverlener is niet bevoegd voor deze voorkeur");

        applyTeVerwijderenOp(request.getTeVerwijderenOp(), voorkeur.getLastUsedAt(), voorkeur.getCreatedAt(), voorkeur::setTeVerwijderenOp);
        return true;
    }

    @Transactional
    public boolean updateContactgegevenTeVerwijderenOpByDienstverlener(TeVerwijderenOpRequest request) {
        Partij partij = getPartij(request.getIdentificatieType(), request.getIdentificatieNummer());
        if (partij == null) return false;

        Contactgegeven contact = partij.getContactgegevens().stream()
                .filter(c -> c.id.equals(request.getId()))
                .findFirst()
                .orElse(null);
        if (contact == null) return false;

        requireDienstverlenerAuthorized(contact.getScopes().stream()
                .map(ScopeContactgegeven::getDienstverlenerDienst)
                .toList(), request, "Dienstverlener is niet bevoegd voor dit contactgegeven");

        applyTeVerwijderenOp(request.getTeVerwijderenOp(), contact.getLastUsedAt(), contact.getCreatedAt(), contact::setTeVerwijderenOp);
        return true;
    }

    private void requireDienstverlenerAuthorized(List<DienstverlenerDienst> links, TeVerwijderenOpRequest request, String message) {
        boolean authorized = links.stream().anyMatch(dd -> {
            if (!dd.getDienstverlener().getNaam().equalsIgnoreCase(request.getDienstverlenerNaam())) return false;
            if (request.getDienstNaam() == null) return true;
            Dienst dienst = dd.getDienst();
            return dienst == null || dienst.getNaam().equalsIgnoreCase(request.getDienstNaam());
        });

        if (!authorized) {
            throw new AuthorizationException(message);
        }
    }

    @Transactional
    public List<PartijResponse> getPartijResponseBulk(List<PartijIdentificatieRequest> identificaties) {
        Map<IdentificatieType, List<String>> grouped = identificaties.stream()
                .collect(Collectors.groupingBy(
                        id -> id.getIdentificatieType(),
                        Collectors.mapping(id -> id.getIdentificatieNummer(), Collectors.toList())));

        return grouped.entrySet().stream()
                .flatMap(entry -> {
                    List<Partij> found = Partij.list(
                            "SELECT p FROM Partij p JOIN p.identificaties i " +
                            "WHERE i.identificatieType = ?1 AND i.identificatieNummer IN ?2",
                            entry.getKey(), entry.getValue());
                    return found.stream();
                })
                .map(partijMapper::toResponse)
                .toList();
    }

    @Transactional
    public PartijResponse getPartijResponse(IdentificatieType identificatieType, String identificatieNummer, PartijRequest partijRequest) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return null;

        if (partijRequest.getDienstverlener() == null && partijRequest.getDienstNaam() == null) {
            return partijMapper.toResponse(partij);
        }

        List<Contactgegeven> filteredContacts = findFilteredContactgegevens(partij, partijRequest);
        List<Voorkeur> filteredVoorkeuren = findFilteredVoorkeuren(partij, partijRequest);
        return partijMapper.toResponse(partij, filteredContacts, filteredVoorkeuren);
    }

    /**
     * Geen {@code distinct} in de query: een rij met meerdere matchende scopes levert net zoveel
     * join-rijen op, maar Hibernate ontdubbelt entity-resultaten sinds versie 6 zelf. Het
     * trefwoord deed hier dus niets. Dat de response geen dubbele rijen bevat is gedrag waar
     * aanroepers op leunen, dus het staat vastgelegd in
     * {@code PartijServiceScopeFilterTest.rijMetTweeMatchendeScopes_KomtSlechtsEenmaalTerug} —
     * die test valt om zodra een Hibernate-upgrade dit weer verandert.
     */
    public List<Contactgegeven> findFilteredContactgegevens(Partij partij, PartijRequest request) {
        StringBuilder query = new StringBuilder(
                "select c from Contactgegeven c " +
                "left join c.scopes s " +
                "left join s.dienstverlenerDienst dd " +
                "left join dd.dienst d " +
                "left join dd.dienstverlener dv " +
                "where c.partij = :partij"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partij", partij);

        if (request.getDienstverlener() != null) {
            query.append(" AND (s IS NULL OR lower(dv.naam) = lower(:dvNaam))");
            params.put("dvNaam", request.getDienstverlener());
        }

        if (request.getDienstNaam() != null) {
            // Unscoped row (default voor alle diensten) en DV-brede scopes (dd.dienst IS NULL)
            // matchen ook, naast scopes die expliciet op dezelfde dienst-naam wijzen.
            query.append(" AND (s IS NULL OR d IS NULL OR lower(d.naam) = lower(:dienstNaam))");
            params.put("dienstNaam", request.getDienstNaam());
        }

        PanacheQuery<Contactgegeven> panacheQuery = Contactgegeven.find(query.toString(), params);
        return panacheQuery.list();
    }

    /** Zonder {@code distinct}, om dezelfde reden als {@link #findFilteredContactgegevens}. */
    public List<Voorkeur> findFilteredVoorkeuren(Partij partij, PartijRequest request) {
        StringBuilder query = new StringBuilder(
                "select v from Voorkeur v "
                        + "left join v.scopes s "
                        + "left join s.dienstverlenerDienst dd "
                        + "left join dd.dienst d "
                        + "left join dd.dienstverlener dv "
                        + "where v.partij = :partij"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partij", partij);

        if (request.getDienstverlener() != null) {
            query.append(" AND (s IS NULL OR lower(dv.naam) = lower(:dvNaam))");
            params.put("dvNaam", request.getDienstverlener());
        }

        if (request.getDienstNaam() != null) {
            query.append(" AND (s IS NULL OR d IS NULL OR lower(d.naam) = lower(:dienstNaam))");
            params.put("dienstNaam", request.getDienstNaam());
        }

        return Voorkeur.<Voorkeur>find(query.toString(), params).list();
    }
}
