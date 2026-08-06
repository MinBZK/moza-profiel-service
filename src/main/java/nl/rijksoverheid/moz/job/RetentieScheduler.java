package nl.rijksoverheid.moz.job;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.helper.HashHelper;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
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

    private static final Period RETENTIE_GRENS = Period.ofYears(7);

    private static final String VOORKEUR_PROCESSING_ACTIVITY_ID = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630";
    private static final String CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631";

    // Bovengrens tegen een klok- of configuratiefout die in één klap veel te veel rijen zou
    // zachtverwijderen; ongeacht INFO-logregels ("3 verwijderd" en "100.000 verwijderd" zien er
    // anders niet anders uit). Dynamisch opgezocht (niet via @ConfigProperty-veldinjectie) zodat
    // een test hem via een system property kan overschrijven.
    private static final String MAX_PER_RUN_PROPERTY = "retentie.scheduler.max-per-run";
    private static final int MAX_PER_RUN_DEFAULT = 10_000;

    private final HashHelper hashHelper;
    private final ProcessingHandler processingHandler;
    private final MeterRegistry meterRegistry;

    private final AtomicLong laatsteRunEpochSeconds = new AtomicLong(0);

    public RetentieScheduler(HashHelper hashHelper, ProcessingHandler processingHandler, MeterRegistry meterRegistry) {
        this.hashHelper = hashHelper;
        this.processingHandler = processingHandler;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauge() {
        Gauge.builder("retentie.laatste_run_epoch_seconds", laatsteRunEpochSeconds, AtomicLong::get)
                .description("Tijdstip (epoch seconds) van de laatst uitgevoerde retentiescheduler-run, ongeacht succes")
                .register(meterRegistry);
    }

    // concurrentExecution = SKIP: een langlopende run (grote tabel) mag niet overlappen met de
    // volgende vuring. Geen @Transactional hier: de twee deel-runs moeten elk hun eigen transactie
    // hebben zodat een fout in de ene de andere niet stil terugdraait of verbergt.
    @Scheduled(cron = "{retentie.scheduler.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void verwijderInactieveRecords() {
        try {
            verwijderInactieveVoorkeuren();
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van voorkeuren mislukt", e);
        }

        try {
            verwijderInactieveContactgegevens();
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van contactgegevens mislukt", e);
        }

        laatsteRunEpochSeconds.set(Instant.now().getEpochSecond());
    }

    @Transactional
    void verwijderInactieveVoorkeuren() {
        Instant nu = Instant.now();
        List<Voorkeur> kandidaten = Voorkeur.find(
                "verwijderdOp IS NULL AND COALESCE(lastUsedAt, createdAt) <= ?1", berekenGrens(nu)).list();

        if (overschrijdtMaxPerRun("voorkeur", kandidaten.size())) {
            return;
        }

        int verwijderd = 0;

        for (Voorkeur voorkeur : kandidaten) {
            // Logging (en dus identiteitsresolutie) vóór de mutatie: een rij mag niet als
            // verwijderd achterblijven zonder logboek-vermelding.
            if (!logVerwijderingOfSlaOver("verwijderVoorkeurRetentie", VOORKEUR_PROCESSING_ACTIVITY_ID, voorkeur.getPartij(), voorkeur.id, "voorkeur")) {
                continue;
            }

            voorkeur.setVerwijderdOp(nu);
            verwijderd++;
        }

        meterRegistry.counter("retentie.verwijderd", "type", "voorkeur").increment(verwijderd);
        LOG.info("Retentiescheduler: " + verwijderd + " voorkeuren verwijderd");
    }

    @Transactional
    void verwijderInactieveContactgegevens() {
        Instant nu = Instant.now();
        List<Contactgegeven> kandidaten = Contactgegeven.find(
                "verwijderdOp IS NULL AND COALESCE(lastUsedAt, createdAt) <= ?1", berekenGrens(nu)).list();

        if (overschrijdtMaxPerRun("contactgegeven", kandidaten.size())) {
            return;
        }

        int verwijderd = 0;

        for (Contactgegeven contact : kandidaten) {
            if (!logVerwijderingOfSlaOver("verwijderContactgegevenRetentie", CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID, contact.getPartij(), contact.id, "contactgegeven")) {
                continue;
            }

            contact.setVerwijderdOp(nu);
            // Net als bij handmatig verwijderen (PartijService.verwijderContactgegeven): mag het
            // default-slot van de partiële index niet blijven bezetten.
            contact.setIsDefault(false);
            verwijderd++;
        }

        meterRegistry.counter("retentie.verwijderd", "type", "contactgegeven").increment(verwijderd);
        LOG.info("Retentiescheduler: " + verwijderd + " contactgegevens verwijderd");
    }

    private static Instant berekenGrens(Instant nu) {
        return nu.atZone(ZoneOffset.UTC).minus(RETENTIE_GRENS).toInstant();
    }

    private boolean overschrijdtMaxPerRun(String type, int aantalKandidaten) {
        final int grens = ConfigProvider.getConfig()
                .getOptionalValue(MAX_PER_RUN_PROPERTY, Integer.class)
                .orElse(MAX_PER_RUN_DEFAULT);

        if (aantalKandidaten <= grens) {
            return false;
        }

        LOG.error("Retentiescheduler: " + aantalKandidaten + " kandidaat-" + type + " overschrijdt de grens van "
                + grens + "; run overgeslagen. Mogelijke klok- of configuratiefout — controleer voordat "
                + MAX_PER_RUN_PROPERTY + " wordt opgehoogd.");
        meterRegistry.counter("retentie.anomalie", "type", type).increment();

        return true;
    }

    // Per rij overslaan i.p.v. de ontbrekende identificatie te laten throwen: een throw zou de hele
    // batch terugdraaien, en de corrupte rij zou elke volgende run opnieuw de anderen blokkeren.
    private boolean logVerwijderingOfSlaOver(String naam, String processingActivityId, Partij partij, UUID entityId, String type) {
        Identificatie identificatie = partij.primaireIdentificatie();

        if (identificatie == null) {
            // findOrCreatePartij voegt altijd een identificatie toe; dit wijst op datacorruptie
            // of een misgelopen migratie, niet op een routinegeval.
            LOG.error("Retentiescheduler: partij " + partij.id + " heeft geen identificatie (invariant violation, "
                    + "zie findOrCreatePartij); " + type + " " + entityId + " wordt overgeslagen, niet verwijderd");
            meterRegistry.counter("retentie.anomalie", "type", type).increment();

            return false;
        }

        LogboekContext ctx = new LogboekContext();
        ctx.setProcessingActivityId(processingActivityId);
        ctx.setDataSubjectId(hashHelper.hashIdentifier(identificatie.getIdentificatieNummer()));
        ctx.setDataSubjectType(String.valueOf(identificatie.getIdentificatieType()));
        ctx.setStatus(StatusCode.OK);
        Span span = processingHandler.startSpan(naam, Context.current());
        processingHandler.addLogboekContextToSpan(span, ctx);
        span.end();

        return true;
    }
}
