package nl.rijksoverheid.moz.job;

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
    private static final int MAX_PER_RUN_DEFAULT = 10_000;

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
        Gauge.builder("retentie.laatste_run_epoch_seconds", laatsteRunEpochSeconds, AtomicLong::get)
                .description("Tijdstip (epoch seconds) van de laatst uitgevoerde retentiescheduler-run, ongeacht succes")
                .register(meterRegistry);
        Gauge.builder("retentie.laatste_geslaagde_run_epoch_seconds", laatsteGeslaagdeRunEpochSeconds, AtomicLong::get)
                .description("Tijdstip (epoch seconds) van de laatste retentiescheduler-run waarin alle drie de fasen slaagden")
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
        try {
            int verwijderd = verwijderInactieveVoorkeuren();
            meterRegistry.counter("retentie.verwijderd", "type", "voorkeur").increment(verwijderd);
            LOG.info("Retentiescheduler: " + verwijderd + " voorkeuren verwijderd");
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van voorkeuren mislukt", e);
            meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "fase-fout").increment();
            alleFasenGeslaagd = false;
        }

        try {
            int verwijderd = verwijderInactieveContactgegevens();
            meterRegistry.counter("retentie.verwijderd", "type", "contactgegeven").increment(verwijderd);
            LOG.info("Retentiescheduler: " + verwijderd + " contactgegevens verwijderd");
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van contactgegevens mislukt", e);
            meterRegistry.counter("retentie.anomalie", "entiteit", "contactgegeven", "reden", "fase-fout").increment();
            alleFasenGeslaagd = false;
        }

        try {
            if (!cascadeDeleteLegePartijen()) {
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

        // JOIN FETCH partij + identificaties: zonder deze fetch is Voorkeur.partij (EAGER
        // @ManyToOne, geen @BatchSize) een secundaire SELECT per kandidaat, en resolveerIdentiteitOfSlaOver's
        // partij.primaireIdentificatie() hieronder nog een LAZY collectie zonder @BatchSize erbovenop —
        // bij de bovengrens ~20.000 extra statements in één transactie die tegelijk rijlocks
        // vasthoudt op alle kandidaten. ORDER BY + page(): bij een overschrijding worden de
        // langst-inactieve rijen het eerst verwerkt (zie bepaalVerwerkingslimiet).
        List<Voorkeur> kandidaten = Voorkeur.find(
                        "SELECT v FROM Voorkeur v JOIN FETCH v.partij p LEFT JOIN FETCH p.identificaties "
                                + "WHERE v.verwijderdOp IS NULL AND COALESCE(v.lastUsedAt, v.createdAt) <= ?1 "
                                + "ORDER BY COALESCE(v.lastUsedAt, v.createdAt)", grens)
                .page(0, (int) limiet)
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

        registreerLogboekNaCommit(teLoggen, "verwijderVoorkeurRetentie", VOORKEUR_PROCESSING_ACTIVITY_ID);

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

        List<Contactgegeven> kandidaten = Contactgegeven.find(
                        "SELECT c FROM Contactgegeven c JOIN FETCH c.partij p LEFT JOIN FETCH p.identificaties "
                                + "WHERE c.verwijderdOp IS NULL AND COALESCE(c.lastUsedAt, c.createdAt) <= ?1 "
                                + "ORDER BY COALESCE(c.lastUsedAt, c.createdAt)", grens)
                .page(0, (int) limiet)
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

        registreerLogboekNaCommit(teLoggen, "verwijderContactgegevenRetentie", CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID);

        return verwijderd;
    }

    // Niet private: zie toelichting bij verwijderInactieveVoorkeuren. Eigen transactie per partij
    // (i.p.v. één transactie voor de hele cascade): een falende partij — lock wait tegen een
    // gelijktijdige findOrCreatePartij, transactietimeout — mag de andere kandidaten niet
    // terugdraaien. Elke transactie vergrendelt hooguit één partij-rij, dus dit kan structureel
    // niet deadlocken (zie ook PartijService.lockEnLeesVerwijderdOp).
    //
    // Kandidaten komen uit een reconciliatiequery i.p.v. de partij-id's die déze run zijn geraakt:
    // een partij waarvan de cascade eerder mislukte (of waarvan de JVM omviel tussen het soft-
    // deleten van haar laatste kind en deze fase) levert in een latere run geen kandidaat-voorkeur/
    // -contactgegeven meer op om als "geraakt" herkend te worden — de query hieronder vindt zo'n
    // partij toch terug, ongeacht welke run haar leeg maakte. Geen index op partij.verwijderdOp, dus
    // dit is een scan over alle actieve partijen; voor nu acceptabel (nog geen productiedata).
    boolean cascadeDeleteLegePartijen() {
        List<UUID> kandidaatIds = Partij.getEntityManager()
                .createQuery("SELECT p.id FROM Partij p WHERE p.verwijderdOp IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM Voorkeur v WHERE v.partij = p AND v.verwijderdOp IS NULL) "
                        + "AND NOT EXISTS (SELECT 1 FROM Contactgegeven c WHERE c.partij = p AND c.verwijderdOp IS NULL)",
                        UUID.class)
                .getResultList();

        Instant nu = Instant.now();
        boolean alleGeslaagd = true;

        for (UUID id : kandidaatIds) {
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    Partij partij = Partij.findById(id);

                    if (partij != null) {
                        partijService.deleteLegePartij(partij, nu);
                    }
                });
            } catch (Exception e) {
                LOG.error("Retentiescheduler: cascade-verwijdering van partij " + id + " mislukt", e);
                meterRegistry.counter("retentie.anomalie", "entiteit", "partij", "reden", "cascade-fout").increment();
                alleGeslaagd = false;
            }
        }

        return alleGeslaagd;
    }

    // Kalenderjaren (Period), verankerd op UTC: een vaste Duration zou schrikkeljaren negeren, en
    // een lokale zone zou de grens per DST-overgang een uur laten schuiven.
    private static Instant berekenGrens(Instant nu) {
        return nu.atZone(ZoneOffset.UTC).minus(RETENTIE_GRENS).toInstant();
    }

    // Bij overschrijding wordt niet de hele fase overgeslagen (dat zou de kandidaatcount de
    // volgende run gelijk of hoger laten, dus zonder handmatig ingrijpen nooit meer vooruitgang) —
    // in plaats daarvan wordt verwerking beperkt tot de grens, oudste rijen eerst (zie de
    // ORDER BY in de aanroepende methode).
    private long bepaalVerwerkingslimiet(String type, long aantalKandidaten) {
        final int grens = ConfigProvider.getConfig()
                .getOptionalValue(MAX_PER_RUN_PROPERTY, Integer.class)
                .orElse(MAX_PER_RUN_DEFAULT);

        if (aantalKandidaten <= grens) {
            return aantalKandidaten;
        }

        LOG.error("Retentiescheduler: " + aantalKandidaten + " kandidaat-" + type + " overschrijdt de grens van "
                + grens + "; verwerking wordt beperkt tot de " + grens + " langst-inactieve rijen. Mogelijke "
                + "klok- of configuratiefout — controleer voordat " + MAX_PER_RUN_PROPERTY + " wordt opgehoogd.");
        meterRegistry.counter("retentie.anomalie", "entiteit", type, "reden", "max-per-run").increment();

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
    private void registreerLogboekNaCommit(List<GeauditeerdeIdentiteit> identiteiten, String naam, String processingActivityId) {
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

                for (GeauditeerdeIdentiteit identiteit : identiteiten) {
                    LogboekContext ctx = new LogboekContext();
                    ctx.setProcessingActivityId(processingActivityId);
                    ctx.setDataSubjectId(hashHelper.hashIdentifier(identiteit.identificatieNummer()));
                    ctx.setDataSubjectType(String.valueOf(identiteit.identificatieType()));
                    ctx.setStatus(StatusCode.OK);
                    Span span = processingHandler.startSpan(naam, Context.current());
                    processingHandler.addLogboekContextToSpan(span, ctx);
                    span.end();
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
