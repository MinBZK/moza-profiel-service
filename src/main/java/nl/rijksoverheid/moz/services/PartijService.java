package nl.rijksoverheid.moz.services;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.BusinessException.Kind;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijIdentificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijRequest;
import nl.rijksoverheid.moz.api.generated.model.ScopeRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.PartijResponse;
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

    // Binnen deze drempel mag een leesactie (GET) geen schrijfactie veroorzaken: anders zou
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

    @Transactional
    public Contactgegeven addContactgegeven(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            ContactgegevenRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        DienstverlenerDienst link = resolveDienstverlenerDienst(request.getScope());

        String normalisedWaarde = request.getType() == ContactType.Email
                ? request.getWaarde().toLowerCase(Locale.ROOT)
                : request.getWaarde();

        Contactgegeven existing = Contactgegeven.find(partij, request.getType(), normalisedWaarde);

        if (existing != null) {
            throw new BusinessException(Kind.CONFLICT,
                    "Contactgegeven met dit type en deze waarde bestaat al voor deze partij");
        }

        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);
        contactgegeven.setType(request.getType());
        contactgegeven.setWaarde(normalisedWaarde);
        contactgegeven.setGeverifieerdAt(null);
        contactgegeven.setLastUsedAt(Instant.now());

        if (request.getType() == ContactType.Email) {
            requestAndApplyVerificatieCode(contactgegeven);
        }

        if (link != null) {
            contactgegeven.addScope(new ScopeContactgegeven(contactgegeven, link));
        }

        contactgegeven.persist();

        return contactgegeven;
    }

    @Transactional
    public Voorkeur addVoorkeur(
            IdentificatieType eigenaarType,
            String eigenaarNummer,
            VoorkeurRequest request) {

        Partij partij = findOrCreatePartij(eigenaarType, eigenaarNummer);
        DienstverlenerDienst link = resolveDienstverlenerDienst(request.getScope());

        // Maximaal één actieve rij per (partij, voorkeurType, scope), alleen in applicatiecode
        // afgedwongen — geen unieke DB-index. Twee gelijktijdige POSTs kunnen dus beide een
        // actieve rij invoegen. Een soft deleted rij op dezelfde sleutel blokkeert niets en
        // wordt niet hersteld.
        Voorkeur existing = Voorkeur.find(partij, request.getVoorkeurType(), link);

        if (existing != null) {
            throw new BusinessException(Kind.CONFLICT,
                    "Voorkeur voor deze partij, scope en voorkeurType bestaat al");
        }

        Voorkeur voorkeur = new Voorkeur();
        voorkeur.setPartij(partij);
        voorkeur.setVoorkeurType(request.getVoorkeurType());
        voorkeur.setWaarde(request.getWaarde());
        voorkeur.setLastUsedAt(Instant.now());

        if (link != null) {
            voorkeur.addScope(new ScopeVoorkeur(voorkeur, link));
        }

        voorkeur.persist();

        return voorkeur;
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

    // Geen resurrection: een eerder soft-deleted partij wordt niet hersteld, er komt een nieuwe
    // Partij + Identificatie. Kan zonder DB-conflict: uk_identificatie (V4) is partieel, en de
    // oude identificatie is mee-gecascadet toen haar partij leegraakte (zie deleteLegePartij),
    // dus die rij zit al buiten de index.
    private Partij findOrCreatePartij(IdentificatieType type, String nummer) {
        Partij partij = Partij.findByIdentificatie(type, nummer);

        if (partij != null) {
            // Lock + lees actuele stand: zie deleteLegePartij/lockEnLeesVerwijderdOp voor de race
            // die dit afdekt. Blijkt de partij ondertussen (net) leeggeraakt en soft-deleted te
            // zijn, dan wordt hij hier behandeld als niet gevonden — er komt een nieuwe partij,
            // net als wanneer findByIdentificatie hem al niet had gevonden.
            if (lockEnLeesVerwijderdOp(partij) == null) {
                return partij;
            }
        }

        LOG.info("Nieuwe partij aanmaken");
        Partij nieuwePartij = new Partij();
        nieuwePartij.addIdentificatie(new Identificatie(type, nummer));
        nieuwePartij.persist();

        return nieuwePartij;
    }

    public Partij getPartij(IdentificatieType identificatieType, String identificatieNummer) {
        return Partij.findByIdentificatie(identificatieType, identificatieNummer);
    }

    @Transactional
    public boolean updateContactgegeven(IdentificatieType identificatieType, String identificatieNummer, ContactgegevenUpdateRequest request) {
        Partij partij = getPartij(identificatieType, identificatieNummer);
        if (partij == null) return false;

        Contactgegeven contact = Contactgegeven.find(partij, request.getId());

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

        if (valueChanged && newWaarde != null && duplicateExists(partij, request.getType(), newWaarde, contact.id)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Combinatie (type, waarde) bestaat al voor deze partij");
        }

        // Demote vóór contact.setType (zie demoteCurrentDefault).
        if (targetDefault) {
            demoteCurrentDefault(partij, request.getType(), contact.id);
        }

        contact.setType(request.getType());
        contact.setWaarde(newWaarde);
        addContactgegevenScopeIfMissing(contact, resolveDienstverlenerDienst(request.getScope()));

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

        return true;
    }

    private boolean duplicateExists(Partij partij, ContactType type, String waarde, UUID exceptId) {
        // Alleen actieve rows tellen mee: uk_contactgegeven_dedup is partieel,
        // dus een PUT die botst met een soft deleted row is geen conflict
        return Contactgegeven.exists(partij, type, waarde, exceptId);
    }

    private void demoteCurrentDefault(Partij partij, ContactType type, UUID exceptId) {
        // Moet vóór contact.setType/setIsDefault draaien: op dat moment is contact nog clean,
        // dus deze bulk-update demote de andere rij vóórdat contacts eigen wijziging ooit
        // flusht — anders zouden beide rijen tijdelijk isDefault = true kunnen dragen voor
        // hetzelfde (partij, type), wat op contactgegeven_default_per_type botst.
        // lastUpdated wordt expliciet meegebumped omdat een bulk-update @PreUpdate bypasst.
        // Filtert wél op verwijderdOp: een rij met een soft delete behoudt haar isDefault-waarde
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

        Voorkeur voorkeur = Voorkeur.find(partij, request.getId());

        if (voorkeur == null) {
            return false;
        }

        DienstverlenerDienst targetLink = resolveDienstverlenerDienst(request.getScope());
        Voorkeur collision = Voorkeur.find(partij, request.getVoorkeurType(), targetLink);

        if (collision != null && !collision.id.equals(voorkeur.id)) {
            throw new BusinessException(Kind.CONFLICT,
                    "Andere voorkeur bestaat al voor deze partij + type + scope");
        }

        voorkeur.setVoorkeurType(request.getVoorkeurType());
        voorkeur.setWaarde(request.getWaarde());
        replaceScopesVoorkeur(voorkeur, targetLink);

        return true;
    }

    // In tegenstelling tot Voorkeur (waar scope onderdeel is van de identiteit, zie
    // replaceScopesVoorkeur) kan een Contactgegeven meerdere scopes hebben. Een PUT vervangt de
    // scopes daarom niet, maar voegt de meegestuurde scope toe als die er nog niet is — zo gaan
    // eerder toegevoegde scopes niet verloren bij een volgende PUT.
    private void addContactgegevenScopeIfMissing(Contactgegeven owner, DienstverlenerDienst link) {
        if (link != null && !hasContactgegevenScopeFor(owner.getScopes(), link)) {
            owner.addScope(new ScopeContactgegeven(owner, link));
        }
    }

    private boolean hasContactgegevenScopeFor(List<ScopeContactgegeven> existing, DienstverlenerDienst link) {
        return existing.stream().anyMatch(s -> Objects.equals(s.getDienstverlenerDienst().id, link.id));
    }

    private void replaceScopesVoorkeur(Voorkeur owner, DienstverlenerDienst link) {
        owner.clearScopes();

        if (link != null) {
            owner.addScope(new ScopeVoorkeur(owner, link));
        }
    }

    // identificatieType/Nummer resolven de partij vóórdat de voorkeur wordt opgezocht: de
    // partij-scoped, soft-delete-veilige Voorkeur.find(partij, id) hieronder garandeert zo dat een
    // id alleen wordt verwijderd als hij ook echt bij de opgegeven identiteit hoort, en dat een
    // al-verwijderde rij niet als gevonden telt. Bestaat de partij niet, of hoort de id niet bij
    // deze partij, dan is dat ononderscheidbaar van "niet gevonden" — net als bij updateVoorkeur.
    @Transactional
    public Voorkeur verwijderVoorkeur(IdentificatieType identificatieType, String identificatieNummer, UUID id) {
        Partij partij = getPartij(identificatieType, identificatieNummer);

        if (partij == null) return null;

        Voorkeur voorkeur = Voorkeur.find(partij, id);

        if (voorkeur != null) {
            Instant nu = Instant.now();
            voorkeur.verwijder(nu);
            deleteLegePartij(partij, nu);
        }

        return voorkeur;
    }

    // isDefault blijft ongemoeid: de rij behoudt haar staat op het moment van verwijderen.
    // Waarom dat geen probleem is voor de partiële index: zie demoteCurrentDefault.
    // Zie verwijderVoorkeur voor waarom identificatieType/Nummer hier ook nodig zijn.
    @Transactional
    public Contactgegeven verwijderContactgegeven(IdentificatieType identificatieType, String identificatieNummer, UUID id) {
        Partij partij = getPartij(identificatieType, identificatieNummer);

        if (partij == null) return null;

        Contactgegeven contact = Contactgegeven.find(partij, id);

        if (contact != null) {
            Instant nu = Instant.now();
            contact.verwijder(nu);
            deleteLegePartij(partij, nu);
        }

        return contact;
    }

    // Publiek: ook aangeroepen door RetentieScheduler (ander package), na het soft-deleten van
    // een Voorkeur/Contactgegeven aldaar. MANDATORY: de mutatie op een detached entity zou anders
    // stilzwijgend verloren gaan zonder dat een toekomstige, niet-transactionele aanroeper dat merkt.
    //
    // Pessimistic lock (zie lockEnLeesVerwijderdOp): zonder lock kan dit racen met
    // findOrCreatePartij, dat gelijktijdig juist een nieuwe actieve child aan dezelfde partij
    // toevoegt (de partij zou dan soft-deleted worden terwijl hij alweer een actieve child heeft),
    // of met een tweede, gelijktijdige aanroep hiervan voor dezelfde partij (die dan allebei
    // denken dat de partij nog niet leeg is en geen van beide cascadet). SELECT ... FOR UPDATE
    // serialiseert beide operaties op deze ene partij-rij.
    // Retourneert of deze aanroep de partij daadwerkelijk cascadete (false: al verwijderd, of
    // nog een actief kind) — RetentieScheduler.cascadeDeleteLegePartijen gebruikt dat om te
    // rapporteren hoeveel partijen een run daadwerkelijk cascadete.
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean deleteLegePartij(Partij partij, Instant nu) {
        if (lockEnLeesVerwijderdOp(partij) != null) {
            return false;
        }

        boolean hasActiveContactgegevens = Contactgegeven.count("partij = ?1 AND verwijderdOp IS NULL", partij) > 0;
        boolean hasActiveVoorkeuren = Voorkeur.count("partij = ?1 AND verwijderdOp IS NULL", partij) > 0;

        if (hasActiveContactgegevens || hasActiveVoorkeuren) {
            return false;
        }

        partij.verwijder(nu);
        // Zonder dit blijft de oude identificatie binnen uk_identificatie's WHERE verwijderd_op
        // IS NULL staan en blokkeert ze findOrCreatePartij's nieuwe insert voor dezelfde
        // (type, nummer) — zie V4-migratie.
        partij.getIdentificaties().forEach(i -> i.verwijder(nu));

        return true;
    }

    // Losse scalar-query i.p.v. de entity: een lock op een al-managed entity ververst haar
    // veldwaarden niet, dus verwijderdOp zou anders de stand van vóór de lock houden. De lock zelf
    // blokkeert tot een concurrente transactie commit/rollbackt (geen timeout ingesteld) — zo
    // worden de twee operaties geserialiseerd. Zie RetentieScheduler.cascadeDeleteLegePartijen
    // voor waarom dat hier niet kan deadlocken.
    private Instant lockEnLeesVerwijderdOp(Partij partij) {
        Partij.getEntityManager().lock(partij, LockModeType.PESSIMISTIC_WRITE);

        return Partij.getEntityManager()
                .createQuery("SELECT p.verwijderdOp FROM Partij p WHERE p.id = :id", Instant.class)
                .setParameter("id", partij.id)
                .getSingleResult();
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
                            "WHERE i.identificatieType = ?1 AND i.identificatieNummer IN ?2 AND p.verwijderdOp IS NULL",
                            entry.getKey(), entry.getValue());

                    return found.stream();
                })
                .map(partij -> {
                    List<Contactgegeven> contactgegevens = Contactgegeven.find(partij).stream()
                            .filter(this::touchIfStale).toList();
                    List<Voorkeur> voorkeuren = Voorkeur.find(partij).stream()
                            .filter(this::touchIfStale).toList();

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

        if (partijRequest.getDienstverlener() == null && partijRequest.getDienstNaam() == null) {
            contactgegevens = Contactgegeven.find(partij);
            voorkeuren = Voorkeur.find(partij);
        } else {
            contactgegevens = findFilteredContactgegevens(partij, partijRequest);
            voorkeuren = findFilteredVoorkeuren(partij, partijRequest);
        }

        contactgegevens = contactgegevens.stream().filter(this::touchIfStale).toList();
        voorkeuren = voorkeuren.stream().filter(this::touchIfStale).toList();

        return partijMapper.toResponse(partij, contactgegevens, voorkeuren);
    }

    // Bulk update, geen setter: lastUpdated (via @PreUpdate) mag niet meebewegen met een touch-on-
    // read, alleen met een echte veldwijziging. Zie ProfielControllerIntegrationTest.getPartij_ReadDoesNotBumpLastUpdated.
    // Package-private i.p.v. private: PartijServiceTest roept dit rechtstreeks aan om de
    // soft-delete-race hieronder deterministisch te testen, zonder echte gelijktijdigheid te
    // hoeven simuleren. Retourneert of de rij nog actief is; false betekent dat de rij tussen het
    // ophalen en deze touch soft-deleted is, en de aanroeper moet hem dan uit de response filteren
    // — anders krijgt een client een rij terug die niet meer bestaat.
    boolean touchIfStale(Contactgegeven cg) {
        if (!isStale(cg.getLastUsedAt())) {
            return true;
        }

        // AND verwijderdOp IS NULL + rowcount: een GET die overlapt met een retentie-soft-delete
        // van dezelfde rij mag lastUsedAt niet meer bumpen.
        long geraakt = Contactgegeven.update(
                "lastUsedAt = ?1 WHERE id = ?2 AND verwijderdOp IS NULL", Instant.now(), cg.id);

        if (geraakt == 0) {
            LOG.warn("touchIfStale: contactgegeven " + cg.id + " was inmiddels soft-deleted, uit response gefilterd");
        }

        return geraakt > 0;
    }

    boolean touchIfStale(Voorkeur voorkeur) {
        if (!isStale(voorkeur.getLastUsedAt())) {
            return true;
        }

        long geraakt = Voorkeur.update(
                "lastUsedAt = ?1 WHERE id = ?2 AND verwijderdOp IS NULL", Instant.now(), voorkeur.id);

        if (geraakt == 0) {
            LOG.warn("touchIfStale: voorkeur " + voorkeur.id + " was inmiddels soft-deleted, uit response gefilterd");
        }

        return geraakt > 0;
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
                "SELECT v FROM Voorkeur v "
                        + "LEFT JOIN v.scopes s "
                        + "LEFT JOIN s.dienstverlenerDienst dd "
                        + "LEFT JOIN dd.dienst d "
                        + "LEFT JOIN dd.dienstverlener dv "
                        + "WHERE v.partij = :partij AND v." + ACTIEF
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
