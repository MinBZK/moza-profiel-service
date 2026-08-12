package nl.rijksoverheid.moz.services;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.BusinessException.Kind;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.ContactgegevenRequest;
import nl.rijksoverheid.moz.dto.request.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.dto.request.PartijIdentificatieRequest;
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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;

@ApplicationScoped
public class PartijService {

    private static final Logger LOG = Logger.getLogger(PartijService.class);

    private static final String ACTIEF = "verwijderdOp IS NULL";

    // Binnen deze drempel mag een leesactie (GET/POST) geen schrijfactie veroorzaken: anders zou
    // elke aanroep lastUsedAt bijwerken. Zie touchIfStale.
    private static final Duration LAST_USED_TOUCH_THRESHOLD = Duration.ofHours(24);

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

    public record AddVoorkeurResult(Voorkeur voorkeur, boolean wasCreated) {}

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

        Contactgegeven existing = Contactgegeven.find(partij, request.type, normalisedWaarde);

        if (existing != null) {
            LOG.info("Contactgegeven al geregistreerd voor deze partij en scope");

            if (existing.getType() == ContactType.Email && existing.getGeverifieerdAt() == null) {
                requestAndApplyVerificatieCode(existing);
                LOG.info("Contactgegeven al geregistreerd maar nog niet geverifieerd, nieuwe verificatiecode verzonden");
            }

            boolean scopeAdded = false;

            if (link != null && !hasContactgegevenScopeFor(existing.getScopes(), link)) {
                existing.addScope(new ScopeContactgegeven(existing, link));
                scopeAdded = true;
            }

            // Ná alle mutaties hierboven: touchIfStale is een bulk-update die buiten de
            // persistence context om schrijft. Vóór verdere mutaties aanroepen zou hem laten
            // overschrijven door de flush van deze (dan alsnog dirty) entity bij commit.
            touchIfStale(existing);

            return new AddContactgegevenResult(existing, false, scopeAdded);
        }

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);
        contactgegeven.setType(request.type);
        contactgegeven.setWaarde(normalisedWaarde);
        contactgegeven.setGeverifieerdAt(null);
        contactgegeven.setLastUsedAt(Instant.now());

        if (request.type == ContactType.Email) {
            requestAndApplyVerificatieCode(contactgegeven);
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

        // Voorkeur-invariant per 08-data.md: maximaal één ACTIEVE rij per (partij, voorkeurType, scope).
        // POST is daarmee upsert: zelfde sleutel + nieuwe waarde overschrijft de actieve rij, geen
        // tweede rij. Een eerder zachtverwijderde rij op dezelfde sleutel blokkeert dit niet en wordt
        // ook niet hersteld — er ontstaat gewoon een nieuwe actieve rij (de unique index is partieel,
        // WHERE verwijderd_op IS NULL). Let op: deze invariant wordt uitsluitend in applicatiecode
        // afgedwongen, er is geen unieke DB-index op (partij, voorkeurType, scope); twee gelijktijdige
        // POSTs op dezelfde sleutel kunnen dus beide hier voorbij komen en twee actieve rijen invoegen.
        Voorkeur existing = Voorkeur.find(partij, request.voorkeurType, link);

        if (existing != null) {
            if (!Objects.equals(existing.getWaarde(), request.waarde)) {
                existing.setWaarde(request.waarde);
            }

            // Ná de mutatie hierboven: zie de toelichting bij touchIfStale in addContactgegeven.
            touchIfStale(existing);

            return new AddVoorkeurResult(existing, false);
        }

        Voorkeur voorkeur = new Voorkeur();
        voorkeur.setPartij(partij);
        voorkeur.setVoorkeurType(request.voorkeurType);
        voorkeur.setWaarde(request.waarde);
        voorkeur.setLastUsedAt(Instant.now());

        if (link != null) {
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));
        }

        voorkeur.persist();

        return new AddVoorkeurResult(voorkeur, true);
    }

    private boolean hasContactgegevenScopeFor(List<ScopeContactgegeven> existing, DienstverlenerDienst link) {
        return existing.stream().anyMatch(s -> Objects.equals(s.getDienstverlenerDienst().id, link.id));
    }

    private void requestAndApplyVerificatieCode(Contactgegeven contact) {
        String referenceId = emailVerificatieService.requestEmailVerificationCode(contact.getWaarde());
        contact.setVerificatieReferentieId(referenceId);
        contact.setIsGeverifieerd(false);
    }

    private DienstverlenerDienst resolveDienstverlenerDienst(ScopeRequest scope) {
        if (scope == null) {
            return null;
        }

        if (scope.dienstverlenerNaam == null) {
            if (scope.dienstNaam != null) {
                throw new BusinessException(Kind.BAD_REQUEST,
                        "dienstNaam zonder dienstverlenerNaam is ongeldig");
            }
            return null;
        }

        Dienstverlener dienstverlener = dienstverlenerService.getDienstverlener(scope.dienstverlenerNaam);
        if (dienstverlener == null) {
            throw new BusinessException(Kind.NOT_FOUND,
                    "Dienstverlener bestaat niet");
        }

        if (scope.dienstNaam == null) {
            return dienstverlenerService.findOrCreateDienstverlenerDienst(dienstverlener, null);
        }

        DienstverlenerDienst link = DienstverlenerDienst.find(
                "dienstverlener = ?1 AND LOWER(dienst.naam) = LOWER(?2)",
                dienstverlener, scope.dienstNaam
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

        Contactgegeven contact = Contactgegeven.find(partij, request.id);

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

        if (valueChanged && newWaarde != null && duplicateExists(partij, request.type, newWaarde, contact.id)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Combinatie (type, waarde) bestaat al voor deze partij");
        }

        // Demote BEFORE mutating contact.setType (see demoteCurrentDefault for why).
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

    private boolean duplicateExists(Partij partij, ContactType type, String waarde, UUID exceptId) {
        // Alleen actieve rows tellen mee: uk_contactgegeven_dedup is partieel,
        // dus een PUT die botst met een soft deleted row is geen conflict
        return Contactgegeven.exists(partij, type, waarde, exceptId);
    }

    private void demoteCurrentDefault(Partij partij, ContactType type, UUID exceptId) {
        // Moet vóór contact.setType(...) draaien (updateContactgegeven, hierboven): het wijzigen
        // van type verplaatst de rij naar een ander slot van de partiële index
        // contactgegeven_default_per_type (WHERE is_default = true AND verwijderd_op IS NULL)
        // terwijl hij nog isDefault = true draagt. Hibernate flusht dirty entities (default
        // FlushModeType.AUTO) vóór een JPQL bulk-update tegen dezelfde tabel, dus deze volgorde
        // werkt; bij flushmode=COMMIT zou de partiële index alsnog kunnen breken.
        // lastUpdated wordt expliciet meegebumped omdat een bulk-update @PreUpdate bypasst.
        // Filtert wél op verwijderdOp: een zachtverwijderde rij behoudt haar isDefault-waarde
        // zoals die was op het moment van verwijderen (zie verwijderContactgegeven) en mag daarom
        // hier niet aangeraakt worden — die rij zit toch al buiten de partiële index.
        Contactgegeven.update(
                "isDefault = false, lastUpdated = ?1 WHERE partij = ?2 AND type = ?3 AND isDefault = true AND verwijderdOp IS NULL AND id <> ?4",
                Instant.now(), partij, type, exceptId);
    }

    @Transactional
    public boolean updateVoorkeur(IdentificatieType identificatieType, String identificatieNummer, VoorkeurUpdateRequest request) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Voorkeur voorkeur = Voorkeur.find(partij, request.id);

        if (voorkeur == null) {
            return false;
        }

        DienstverlenerDienst targetLink = resolveDienstverlenerDienst(request.scope);
        Voorkeur collision = Voorkeur.find(partij, request.voorkeurType, targetLink);

        if (collision != null && !collision.id.equals(voorkeur.id)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Andere voorkeur bestaat al voor deze partij + type + scope");
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
    public Voorkeur verwijderVoorkeur(UUID id) {
        Voorkeur voorkeur = Voorkeur.findNietVerwijderdById(id);

        if (voorkeur != null) {
            voorkeur.setVerwijderdOp(Instant.now());
        }

        return voorkeur;
    }

    // isDefault blijft ongemoeid: de rij behoudt haar staat op het moment van verwijderen.
    // Waarom dat geen probleem is voor de partiële index: zie demoteCurrentDefault.
    @Transactional
    public Contactgegeven verwijderContactgegeven(UUID id) {
        Contactgegeven contact = Contactgegeven.findNietVerwijderdById(id);

        if (contact != null) {
            contact.setVerwijderdOp(Instant.now());
        }

        return contact;
    }

    @Transactional
    public List<PartijResponse> getPartijResponseBulk(List<PartijIdentificatieRequest> identificaties) {
        Map<IdentificatieType, List<String>> grouped = identificaties.stream()
                .collect(Collectors.groupingBy(
                        id -> id.identificatieType,
                        Collectors.mapping(id -> id.identificatieNummer, Collectors.toList())));

        return grouped.entrySet().stream()
                .flatMap(entry -> {
                    List<Partij> found = Partij.list(
                            "SELECT p FROM Partij p JOIN p.identificaties i " +
                            "WHERE i.identificatieType = ?1 AND i.identificatieNummer IN ?2",
                            entry.getKey(), entry.getValue());

                    return found.stream();
                })
                .map(partij -> {
                    List<Contactgegeven> contactgegevens = Contactgegeven.find(partij);
                    List<Voorkeur> voorkeuren = Voorkeur.find(partij);
                    contactgegevens.forEach(this::touchIfStale);
                    voorkeuren.forEach(this::touchIfStale);

                    return partijMapper.toResponse(partij, contactgegevens, voorkeuren);
                })
                .toList();
    }

    @Transactional
    public PartijResponse getPartijResponse(IdentificatieType identificatieType, String identificatieNummer, PartijRequest partijRequest) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return null;

        List<Contactgegeven> contactgegevens;
        List<Voorkeur> voorkeuren;

        if (partijRequest.isEmpty()) {
            contactgegevens = Contactgegeven.find(partij);
            voorkeuren = Voorkeur.find(partij);
        } else {
            contactgegevens = findFilteredContactgegevens(partij, partijRequest);
            voorkeuren = findFilteredVoorkeuren(partij, partijRequest);
        }

        contactgegevens.forEach(this::touchIfStale);
        voorkeuren.forEach(this::touchIfStale);

        return partijMapper.toResponse(partij, contactgegevens, voorkeuren);
    }

    // Bulk update, geen setter: lastUpdated (via @PreUpdate) mag niet meebewegen met een touch-on-
    // read, alleen met een echte veldwijziging. Zie ProfielControllerIntegrationTest.getPartij_ReadDoesNotBumpLastUpdated.
    private void touchIfStale(Contactgegeven cg) {
        if (isStale(cg.getLastUsedAt())) {
            Contactgegeven.update("lastUsedAt = ?1 WHERE id = ?2", Instant.now(), cg.id);
        }
    }

    private void touchIfStale(Voorkeur voorkeur) {
        if (isStale(voorkeur.getLastUsedAt())) {
            Voorkeur.update("lastUsedAt = ?1 WHERE id = ?2", Instant.now(), voorkeur.id);
        }
    }

    private static boolean isStale(Instant lastUsedAt) {
        return lastUsedAt == null
                || lastUsedAt.plus(LAST_USED_TOUCH_THRESHOLD).isBefore(Instant.now());
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
                "SELECT c FROM Contactgegeven c " +
                "LEFT JOIN c.scopes s " +
                "LEFT JOIN s.dienstverlenerDienst dd " +
                "LEFT JOIN dd.dienst d " +
                "LEFT JOIN dd.dienstverlener dv " +
                "WHERE c.partij = :partij AND c." + ACTIEF
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partij", partij);

        if (request.dienstverlener != null) {
            query.append(" AND (s IS NULL OR LOWER(dv.naam) = LOWER(:dvNaam))");
            params.put("dvNaam", request.dienstverlener);
        }

        if (request.dienstNaam != null) {
            // Unscoped row (default voor alle diensten) en DV-brede scopes (dd.dienst IS NULL)
            // matchen ook, naast scopes die expliciet op dezelfde dienst-naam wijzen.
            query.append(" AND (s IS NULL OR d IS NULL OR LOWER(d.naam) = LOWER(:dienstNaam))");
            params.put("dienstNaam", request.dienstNaam);
        }

        PanacheQuery<Contactgegeven> panacheQuery = Contactgegeven.find(query.toString(), params);

        return panacheQuery.list();
    }

    /** Zonder {@code distinct}, om dezelfde reden als {@link #findFilteredContactgegevens}. */
    public List<Voorkeur> findFilteredVoorkeuren(Partij partij, PartijRequest request) {
        StringBuilder query = new StringBuilder(
                "SELECT v FROM Voorkeur v "
                        + "LEFT JOIN v.scopes s "
                        + "LEFT JOIN s.dienstverlenerDienst dd "
                        + "LEFT JOIN dd.dienst d "
                        + "LEFT JOIN dd.dienstverlener dv "
                        + "WHERE v.partij = :partij AND v." + ACTIEF
        );
        Map<String, Object> params = new HashMap<>();
        params.put("partij", partij);

        if (request.dienstverlener != null) {
            query.append(" AND (s IS NULL OR LOWER(dv.naam) = LOWER(:dvNaam))");
            params.put("dvNaam", request.dienstverlener);
        }

        if (request.dienstNaam != null) {
            query.append(" AND (s IS NULL OR d IS NULL OR LOWER(d.naam) = LOWER(:dienstNaam))");
            params.put("dienstNaam", request.dienstNaam);
        }

        return Voorkeur.<Voorkeur>find(query.toString(), params).list();
    }
}
