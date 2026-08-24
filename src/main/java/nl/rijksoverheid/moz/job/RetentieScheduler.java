package nl.rijksoverheid.moz.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.helper.HashHelper;
import nl.rijksoverheid.moz.services.PartijService;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verwijdert (soft-delete) Voorkeur/Contactgegeven-records die lang niet zijn gebruikt.
 * <p>
 * Laadt bewust de kandidaat-entiteiten (i.p.v. een blinde bulk-update): retentieverwijdering van
 * persoonsgegevens is zelf een AVG-verwerkingsactiviteit en heeft dus een logboek-vermelding per
 * subject nodig, net als elk controllerpad. Dat kan alleen als de betrokken partij/identificatie
 * per rij bekend is. Zie {@link nl.rijksoverheid.moz.controller.ProfielController#getPartijBulk}
 * voor hetzelfde patroon (handmatige LogboekContext + span per subject i.p.v. @Logboek, dat een
 * enkel subject per aanroep veronderstelt en dus niet past op een batchjob).
 */
@ApplicationScoped
public class RetentieScheduler {

    private static final Logger LOG = Logger.getLogger(RetentieScheduler.class);

    // Bewaartermijn met een juridische grondslag (welke precies is hier niet geverifieerd),
    // geen tuning-parameter. Wijzigen is een besluit voor wie die grondslag beheert.
    private static final Period RETENTIE_GRENS = Period.ofYears(7);

    private static final String VOORKEUR_PROCESSING_ACTIVITY_ID = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630";
    private static final String CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631";

    // Bovengrens tegen een klok- of configuratiefout die in één klap veel te veel rijen zou
    // soft-deleten; ongeacht INFO-logregels ("3 verwijderd" en "100.000 verwijderd" zien er
    // anders niet anders uit). Dynamisch opgezocht (niet via @ConfigProperty-veldinjectie) zodat
    // een test hem via een system property kan overschrijven.
    private static final String MAX_PER_RUN_PROPERTY = "retentie.scheduler.max-per-run";
    // Rond getal, niet afgeleid van verwacht productievolume (dat bestaat nog niet). Herijk zodra
    // er echte gebruiksdata is.
    private static final int MAX_PER_RUN_DEFAULT = 10_000;
    // Bewuste, expliciete opt-in om ondanks een overschrijding van de grens toch te verwerken —
    // standaard uit, zodat een operator eerst moet vaststellen dat het een legitieme achterstand
    // is en geen klok-/configuratiefout. Zie bepaalVerwerkingslimiet.
    private static final String MAX_PER_RUN_OVERRIDE_PROPERTY = "retentie.scheduler.max-per-run.override";

    private final HashHelper hashHelper;
    private final ProcessingHandler processingHandler;
    private final MeterRegistry meterRegistry;
    private final PartijService partijService;
    private final TransactionSynchronizationRegistry txSyncRegistry;

    private final AtomicLong laatsteRunEpochSeconds = new AtomicLong(0);
    private final AtomicLong laatsteGeslaagdeRunEpochSeconds = new AtomicLong(0);

    public RetentieScheduler(HashHelper hashHelper, ProcessingHandler processingHandler, MeterRegistry meterRegistry,
            PartijService partijService, TransactionSynchronizationRegistry txSyncRegistry) {
        this.hashHelper = hashHelper;
        this.processingHandler = processingHandler;
        this.meterRegistry = meterRegistry;
        this.partijService = partijService;
        this.txSyncRegistry = txSyncRegistry;
    }

    @PostConstruct
    void registerGauge() {
        // Per instance (quarkus.quartz.clustered=true, maar deze AtomicLongs leven alleen in het
        // geheugen van de pod die vuurde): een replica die de job nooit uitvoert blijft op 0 staan.
        // Een alert hierop moet dus over de cluster aggregeren met max(), niet per instance kijken.
        Gauge.builder("retentie.laatste_run_epoch_seconds", laatsteRunEpochSeconds, AtomicLong::get)
                .description("Tijdstip (epoch seconds) van de laatst uitgevoerde retentiescheduler-run op déze "
                        + "instance, ongeacht succes. Quartz is clustered: aggregeer met max() over de cluster.")
                .register(meterRegistry);
        Gauge.builder("retentie.laatste_geslaagde_run_epoch_seconds", laatsteGeslaagdeRunEpochSeconds, AtomicLong::get)
                .description("Tijdstip (epoch seconds) van de laatste retentiescheduler-run op déze instance "
                        + "waarin alle drie de fasen slaagden. Quartz is clustered: aggregeer met max() over de cluster.")
                .register(meterRegistry);
    }

    // concurrentExecution = SKIP: een langlopende run (grote tabel) mag niet overlappen met de
    // volgende vuring. Geen @Transactional hier: elke deel-fase heeft zijn eigen transactie zodat
    // een fout in de ene de andere niet stil terugdraait of verbergt.
    @Scheduled(cron = "{retentie.scheduler.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void verwijderInactieveRecords() {
        boolean alleFasenGeslaagd = true;

        // Counter/log ná de transactionele aanroep, niet erin: verwijderInactieveVoorkeuren()
        // commit al vóórdat deze regel bereikt wordt (de @Transactional-interceptor commit bij
        // een normale return), dus "teruggekeerd zonder exception" betekent hier ook "gecommit".
        // Zou de commit zelf falen, dan gooit de interceptor en komt de catch hieronder uit,
        // nooit de succeslog met een aantal dat nooit werkelijkheid werd.
        //
        // anomalieCount vóór/ná i.p.v. alleen de catch: een fase kan ook zónder exception
        // anomaal zijn (max-per-run, ontbrekende-identificatie, logboek-fout zetten alleen een
        // teller op, ze gooien niets) — zonder deze vergelijking zou zo'n run alsnog als
        // "geslaagd" gelden.
        try {
            double anomalieVoor = anomalieCount("voorkeur");
            int verwijderd = verwijderInactieveVoorkeuren();
            meterRegistry.counter("retentie.verwijderd", "type", "voorkeur").increment(verwijderd);
            LOG.info("Retentiescheduler: " + verwijderd + " voorkeuren verwijderd");

            if (anomalieCount("voorkeur") > anomalieVoor) {
                alleFasenGeslaagd = false;
            }
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van voorkeuren mislukt", e);
            meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "fase-fout").increment();
            alleFasenGeslaagd = false;
        }

        try {
            double anomalieVoor = anomalieCount("contactgegeven");
            int verwijderd = verwijderInactieveContactgegevens();
            meterRegistry.counter("retentie.verwijderd", "type", "contactgegeven").increment(verwijderd);
            LOG.info("Retentiescheduler: " + verwijderd + " contactgegevens verwijderd");

            if (anomalieCount("contactgegeven") > anomalieVoor) {
                alleFasenGeslaagd = false;
            }
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van contactgegevens mislukt", e);
            meterRegistry.counter("retentie.anomalie", "entiteit", "contactgegeven", "reden", "fase-fout").increment();
            alleFasenGeslaagd = false;
        }

        try {
            double anomalieVoor = anomalieCount("partij");
            boolean cascadeGeslaagd = cascadeDeleteLegePartijen();

            if (!cascadeGeslaagd || anomalieCount("partij") > anomalieVoor) {
                alleFasenGeslaagd = false;
            }
        } catch (Exception e) {
            LOG.error("Retentiescheduler: cascade-verwijdering van lege partijen mislukt", e);
            meterRegistry.counter("retentie.anomalie", "entiteit", "partij", "reden", "fase-fout").increment();
            alleFasenGeslaagd = false;
        }

        Instant nu = Instant.now();
        laatsteRunEpochSeconds.set(nu.getEpochSecond());

        if (alleFasenGeslaagd) {
            laatsteGeslaagdeRunEpochSeconds.set(nu.getEpochSecond());
        }
    }

    private double anomalieCount(String entiteit) {
        return meterRegistry.find("retentie.anomalie").tag("entiteit", entiteit).counters()
                .stream().mapToDouble(Counter::count).sum();
    }

    // Niet private: @Transactional is een CDI interceptor binding, en ArC intercepteert door een
    // subclass te genereren die de methode overridet. Dat kan niet bij een private methode,
    // @Transactional zou dan niets doen, zonder dat dat uit de code zelf blijkt.
    @Transactional
    int verwijderInactieveVoorkeuren() {
        Instant nu = Instant.now();
        Instant grens = berekenGrens(nu);

        long limiet = bepaalVerwerkingslimiet("voorkeur", Voorkeur.count(
                "verwijderdOp IS NULL AND COALESCE(lastUsedAt, createdAt) <= ?1", grens));

        if (limiet == 0) {
            return 0;
        }

        // Twee stappen i.p.v. één page()'d fetch-join query: LEFT JOIN FETCH op een collectie
        // (p.identificaties) samen met page()/firstResult+maxResults laat Hibernate de SQL LIMIT
        // vallen en pagineert in-memory (HHH90003004) — bij precies de overschrijding waar
        // bepaalVerwerkingslimiet tegen beschermt zou dat alsnog de volledige, ongelimiteerde
        // resultaatset in het geheugen laden vóór de afkap. Eerst de ID's ophalen zonder
        // collectiefetch (daar werkt LIMIT wél op SQL-niveau, en ORDER BY bepaalt welke rijen de
        // langst-inactieve zijn), dan pas die begrensde set laden met JOIN FETCH op partij en
        // identificaties — zonder die fetch joins is Voorkeur.partij (EAGER @ManyToOne, geen
        // @BatchSize) een secundaire SELECT per kandidaat, en partij.primaireIdentificatie()
        // hieronder nog een LAZY collectie zonder @BatchSize erbovenop.
        List<UUID> kandidaatIds = Voorkeur.getEntityManager()
                .createQuery("SELECT v.id FROM Voorkeur v WHERE v.verwijderdOp IS NULL "
                        + "AND COALESCE(v.lastUsedAt, v.createdAt) <= :grens "
                        + "ORDER BY COALESCE(v.lastUsedAt, v.createdAt)", UUID.class)
                .setParameter("grens", grens)
                .setMaxResults((int) limiet)
                .getResultList();

        if (kandidaatIds.isEmpty()) {
            return 0;
        }

        List<Voorkeur> kandidaten = Voorkeur.find(
                "SELECT v FROM Voorkeur v JOIN FETCH v.partij p LEFT JOIN FETCH p.identificaties "
                        + "WHERE v.id IN ?1", kandidaatIds)
                .list();

        int verwijderd = 0;
        List<GeauditeerdeIdentiteit> teLoggen = new ArrayList<>();

        for (Voorkeur voorkeur : kandidaten) {
            Identificatie identificatie = resolveerIdentiteitOfSlaOver(voorkeur.getPartij(), voorkeur.id, "voorkeur");

            if (identificatie == null) {
                continue;
            }

            voorkeur.verwijder(nu);
            teLoggen.add(new GeauditeerdeIdentiteit(identificatie.getIdentificatieNummer(), identificatie.getIdentificatieType()));
            verwijderd++;
        }

        registreerLogboekNaCommit(teLoggen, "voorkeur", "verwijderVoorkeurRetentie", VOORKEUR_PROCESSING_ACTIVITY_ID);

        return verwijderd;
    }

    // Niet private: zie toelichting bij verwijderInactieveVoorkeuren.
    @Transactional
    int verwijderInactieveContactgegevens() {
        Instant nu = Instant.now();
        Instant grens = berekenGrens(nu);

        long limiet = bepaalVerwerkingslimiet("contactgegeven", Contactgegeven.count(
                "verwijderdOp IS NULL AND COALESCE(lastUsedAt, createdAt) <= ?1", grens));

        if (limiet == 0) {
            return 0;
        }

        // Zie toelichting bij verwijderInactieveVoorkeuren: zelfde twee-staps aanpak tegen
        // in-memory paginering bij een collectiefetch.
        List<UUID> kandidaatIds = Contactgegeven.getEntityManager()
                .createQuery("SELECT c.id FROM Contactgegeven c WHERE c.verwijderdOp IS NULL "
                        + "AND COALESCE(c.lastUsedAt, c.createdAt) <= :grens "
                        + "ORDER BY COALESCE(c.lastUsedAt, c.createdAt)", UUID.class)
                .setParameter("grens", grens)
                .setMaxResults((int) limiet)
                .getResultList();

        if (kandidaatIds.isEmpty()) {
            return 0;
        }

        List<Contactgegeven> kandidaten = Contactgegeven.find(
                "SELECT c FROM Contactgegeven c JOIN FETCH c.partij p LEFT JOIN FETCH p.identificaties "
                        + "WHERE c.id IN ?1", kandidaatIds)
                .list();

        int verwijderd = 0;
        List<GeauditeerdeIdentiteit> teLoggen = new ArrayList<>();

        for (Contactgegeven contact : kandidaten) {
            Identificatie identificatie = resolveerIdentiteitOfSlaOver(contact.getPartij(), contact.id, "contactgegeven");

            if (identificatie == null) {
                continue;
            }

            contact.verwijder(nu);
            teLoggen.add(new GeauditeerdeIdentiteit(identificatie.getIdentificatieNummer(), identificatie.getIdentificatieType()));
            verwijderd++;
        }

        registreerLogboekNaCommit(teLoggen, "contactgegeven", "verwijderContactgegevenRetentie", CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID);

        return verwijderd;
    }

    // Eigen transactie per partij (i.p.v. één transactie voor de hele cascade): een falende
    // partij — lock wait tegen een gelijktijdige findOrCreatePartij, transactietimeout — mag de
    // andere kandidaten niet terugdraaien. Elke transactie vergrendelt hooguit één partij-rij,
    // dus dit kan structureel niet deadlocken (zie ook PartijService.lockEnLeesVerwijderdOp).
    //
    // Kandidaten komen uit een reconciliatiequery i.p.v. de partij-id's die déze run zijn geraakt:
    // een partij waarvan de cascade eerder mislukte (of waarvan de JVM omviel tussen het soft-
    // deleten van haar laatste kind en deze fase) levert in een latere run geen kandidaat-voorkeur/
    // -contactgegeven meer op om als "geraakt" herkend te worden — de query hieronder vindt zo'n
    // partij toch terug, ongeacht welke run haar leeg maakte. Geen index op partij.verwijderdOp, dus
    // dit is een scan over alle actieve partijen; voor nu acceptabel (nog geen productiedata).
    //
    // Query is bewust breed: elke actieve partij zonder actieve children, ongeacht ouderdom. Dat
    // is vandaag veilig omdat findOrCreatePartij's enige twee aanroepers (PartijService.addContactgegeven/
    // addVoorkeur) de partij en haar eerste child in dezelfde transactie persisteren — een actieve
    // partij zonder child kan dus niet bestaan. Die aanname geldt niet meer zodra addContactgegeven's
    // schrijftransactie wordt gesplitst rond de externe verificatie-aanroep (voorgestelde refactor,
    // zie MinBZK/MijnOverheidZakelijk#927): in het gat tussen de twee transacties zou deze query een
    // nog-lege partij kunnen cascaden vóórdat haar eerste child ooit persisteert.
    private boolean cascadeDeleteLegePartijen() {
        List<UUID> kandidaatIds = Partij.getEntityManager()
                .createQuery("SELECT p.id FROM Partij p WHERE p.verwijderdOp IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM Voorkeur v WHERE v.partij = p AND v.verwijderdOp IS NULL) "
                        + "AND NOT EXISTS (SELECT 1 FROM Contactgegeven c WHERE c.partij = p AND c.verwijderdOp IS NULL)",
                        UUID.class)
                .getResultList();

        // Dezelfde bovengrens als de andere twee fasen: zonder cap zou de klok-/configuratiefout
        // die de andere fasen al afvangen hier alsnog ongebreideld doorwerken (één transactie per
        // kandidaat-partij, onbegrensd). Geen collectiefetch op deze query, dus geen risico op de
        // in-memory-paginering die verwijderInactieveVoorkeuren/-Contactgegevens vermijden.
        long limiet = bepaalVerwerkingslimiet("partij", kandidaatIds.size());

        if (limiet < kandidaatIds.size()) {
            kandidaatIds = kandidaatIds.subList(0, (int) limiet);
        }

        Instant nu = Instant.now();
        boolean alleGeslaagd = true;
        int gecascadeerd = 0;

        for (UUID id : kandidaatIds) {
            try {
                AtomicBoolean cascadeteDezeKeer = new AtomicBoolean(false);

                QuarkusTransaction.requiringNew().run(() -> {
                    Partij partij = Partij.findById(id);

                    if (partij != null && partijService.deleteLegePartij(partij, nu)) {
                        cascadeteDezeKeer.set(true);
                    }
                });

                if (cascadeteDezeKeer.get()) {
                    gecascadeerd++;
                }
            } catch (Exception e) {
                LOG.error("Retentiescheduler: cascade-verwijdering van partij " + id + " mislukt", e);
                meterRegistry.counter("retentie.anomalie", "entiteit", "partij", "reden", "cascade-fout").increment();
                alleGeslaagd = false;
            }
        }

        // Elke getelde partij is op dit punt al gecommit (eigen transactie per partij hierboven),
        // dus dit is net zo veilig als de "alleen tellen ná een normale return"-regel bij de andere
        // twee fasen — hier is dat alleen geen aparte aanroeplaag omdat de commits al binnen deze
        // methode plaatsvinden.
        meterRegistry.counter("retentie.verwijderd", "type", "partij").increment(gecascadeerd);
        LOG.info("Retentiescheduler: " + gecascadeerd + " partijen gecascadeerd");

        return alleGeslaagd;
    }

    // Kalenderjaren (Period), verankerd op UTC: een vaste Duration zou schrikkeljaren negeren, en
    // een lokale zone zou de grens per DST-overgang een uur laten schuiven.
    private static Instant berekenGrens(Instant nu) {
        return nu.atZone(ZoneOffset.UTC).minus(RETENTIE_GRENS).toInstant();
    }

    // Bij overschrijding wordt de fase standaard overgeslagen, niet gedeeltelijk verwerkt: de
    // grens bestaat om een klok- of configuratiefout te vangen, en zonder menselijke bevestiging
    // dat het om een legitieme achterstand gaat (niet zo'n fout) zou automatisch dosseren de fout
    // alsnog laten voltrekken, alleen verspreid over meerdere nachten in plaats van in één klap.
    // MAX_PER_RUN_OVERRIDE_PROPERTY is de bewuste ontsnapping daaruit: staat die aan, dan wordt
    // wél verwerkt (oudste eerst, voor de twee aanroepers met een ORDER BY) — maar de aanroepende
    // fase telt in verwijderInactieveRecords hoe dan ook nooit mee als geslaagd bij een
    // overschrijding, ongeacht de override (zie anomalieCount daar).
    private long bepaalVerwerkingslimiet(String type, long aantalKandidaten) {
        final int grens = ConfigProvider.getConfig()
                .getOptionalValue(MAX_PER_RUN_PROPERTY, Integer.class)
                .orElse(MAX_PER_RUN_DEFAULT);

        if (aantalKandidaten <= grens) {
            return aantalKandidaten;
        }

        meterRegistry.counter("retentie.anomalie", "entiteit", type, "reden", "max-per-run").increment();

        boolean override = ConfigProvider.getConfig()
                .getOptionalValue(MAX_PER_RUN_OVERRIDE_PROPERTY, Boolean.class)
                .orElse(false);

        if (!override) {
            LOG.error("Retentiescheduler: " + aantalKandidaten + " kandidaat-" + type + " overschrijdt de grens van "
                    + grens + "; fase overgeslagen. Kan een legitieme achterstand zijn of een klok-/"
                    + "configuratiefout — controleer eerst, zet dan pas " + MAX_PER_RUN_OVERRIDE_PROPERTY
                    + "=true om de eerste " + grens + " kandidaten alsnog te verwerken.");

            return 0;
        }

        LOG.error("Retentiescheduler: " + aantalKandidaten + " kandidaat-" + type + " overschrijdt de grens van "
                + grens + "; " + MAX_PER_RUN_OVERRIDE_PROPERTY + " staat aan, dus verwerking wordt beperkt tot "
                + "de eerste " + grens + " kandidaten.");

        return grens;
    }

    // Per rij overslaan i.p.v. te throwen: een throw draait de hele batch terug, waarna de
    // corrupte rij elke volgende run opnieuw de andere kandidaten blokkeert.
    private Identificatie resolveerIdentiteitOfSlaOver(Partij partij, UUID entityId, String type) {
        Identificatie identificatie = partij.primaireIdentificatie();

        if (identificatie == null) {
            // findOrCreatePartij voegt altijd een identificatie toe; dit wijst op datacorruptie
            // of een misgelopen migratie, niet op een routinegeval.
            LOG.error("Retentiescheduler: partij " + partij.id + " heeft geen identificatie (invariant violation, "
                    + "zie findOrCreatePartij); " + type + " " + entityId + " wordt overgeslagen, niet verwijderd");
            meterRegistry.counter("retentie.anomalie", "entiteit", type, "reden", "ontbrekende-identificatie").increment();

            return null;
        }

        return identificatie;
    }

    // Emitteert pas ná commit (afterCompletion(STATUS_COMMITTED)), nooit ervoor: een logboek-span
    // mag nooit een verwijdering claimen die niet heeft plaatsgevonden — dat is de kostbaardere
    // richting om fout te hebben voor iets dat als bewijsmiddel dient. Dit garandeert wél alleen de
    // vólgorde, niet de duurzaamheid: span.end() geeft de span door aan de OTel-processor, en een
    // volle exportqueue of exportfout wordt door de SDK per ontwerp geslikt, ongeacht of de
    // transactie committede. Die blootstelling kan deze code niet dichten.
    private void registreerLogboekNaCommit(List<GeauditeerdeIdentiteit> identiteiten, String type, String naam, String processingActivityId) {
        if (identiteiten.isEmpty()) {
            return;
        }

        txSyncRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status != Status.STATUS_COMMITTED) {
                    return;
                }

                // Eigen try/catch per identiteit: dit draait in de completion-callback van de
                // transactiemanager, waar een exception niet naar een aanroeper propageert die er
                // iets aan kan doen. Zonder vangst stopt één falende span (bv. startSpan() geeft
                // null terug, zie RetentieSchedulerTest) de hele resterende batch — de rijen zijn
                // dan al verwijderd, maar zonder logboek-vermelding, en niets meldt dat.
                for (GeauditeerdeIdentiteit identiteit : identiteiten) {
                    try {
                        LogboekContext ctx = new LogboekContext();
                        ctx.setProcessingActivityId(processingActivityId);
                        ctx.setDataSubjectId(hashHelper.hashIdentifier(identiteit.identificatieNummer()));
                        ctx.setDataSubjectType(String.valueOf(identiteit.identificatieType()));
                        ctx.setStatus(StatusCode.OK);
                        Span span = processingHandler.startSpan(naam, Context.current());
                        processingHandler.addLogboekContextToSpan(span, ctx);
                        span.end();
                    } catch (Exception e) {
                        LOG.error("Retentiescheduler: logboek-emissie voor " + naam + " mislukt ná commit "
                                + "(de rij is al verwijderd; dit is dus een gemist audit-spoor)", e);
                        meterRegistry.counter("retentie.anomalie", "entiteit", type, "reden", "logboek-fout").increment();
                    }
                }
            }
        });
    }

    // Alleen de scalaire velden, niet de entity zelf: na commit is de persistence context die
    // Identificatie beheerde mogelijk al gesloten, en deze twee waarden zijn het enige dat de
    // afterCompletion-callback nodig heeft.
    private record GeauditeerdeIdentiteit(String identificatieNummer, IdentificatieType identificatieType) {
    }
}
