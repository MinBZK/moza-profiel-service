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
import nl.rijksoverheid.moz.services.PartijService;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
    // soft-deleten; ongeacht INFO-logregels ("3 verwijderd" en "100.000 verwijderd" zien er
    // anders niet anders uit). Dynamisch opgezocht (niet via @ConfigProperty-veldinjectie) zodat
    // een test hem via een system property kan overschrijven.
    private static final String MAX_PER_RUN_PROPERTY = "retentie.scheduler.max-per-run";
    private static final int MAX_PER_RUN_DEFAULT = 10_000;

    private final HashHelper hashHelper;
    private final ProcessingHandler processingHandler;
    private final MeterRegistry meterRegistry;
    private final PartijService partijService;

    private final AtomicLong laatsteRunEpochSeconds = new AtomicLong(0);

    public RetentieScheduler(HashHelper hashHelper, ProcessingHandler processingHandler, MeterRegistry meterRegistry, PartijService partijService) {
        this.hashHelper = hashHelper;
        this.processingHandler = processingHandler;
        this.meterRegistry = meterRegistry;
        this.partijService = partijService;
    }

    @PostConstruct
    void registerGauge() {
        Gauge.builder("retentie.laatste_run_epoch_seconds", laatsteRunEpochSeconds, AtomicLong::get)
                .description("Tijdstip (epoch seconds) van de laatst uitgevoerde retentiescheduler-run, ongeacht succes")
                .register(meterRegistry);
    }

    // concurrentExecution = SKIP: een langlopende run (grote tabel) mag niet overlappen met de
    // volgende vuring. Geen @Transactional hier: elke deel-fase heeft zijn eigen transactie zodat
    // een fout in de ene de andere niet stil terugdraait of verbergt.
    @Scheduled(cron = "{retentie.scheduler.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void verwijderInactieveRecords() {
        // Eén gecombineerde set i.p.v. losse cascade-aanroepen per fase: een partij met zowel een
        // verlopen voorkeur als een verlopen contactgegeven in dezelfde run zou anders twee keer
        // gecontroleerd worden terwijl één keer volstaat.
        Set<UUID> geraaktePartijIds = new LinkedHashSet<>();

        try {
            geraaktePartijIds.addAll(verwijderInactieveVoorkeuren());
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van voorkeuren mislukt", e);
        }

        try {
            geraaktePartijIds.addAll(verwijderInactieveContactgegevens());
        } catch (Exception e) {
            LOG.error("Retentiescheduler: verwijderen van contactgegevens mislukt", e);
        }

        try {
            cascadeDeleteLegePartijen(geraaktePartijIds);
        } catch (Exception e) {
            LOG.error("Retentiescheduler: cascade-verwijdering van lege partijen mislukt", e);
        }

        laatsteRunEpochSeconds.set(Instant.now().getEpochSecond());
    }

    // Niet private: @Transactional is een CDI interceptor binding, en ArC intercepteert door een
    // subclass te genereren die de methode overridet. Dat kan niet bij een private methode,
    // @Transactional zou dan niets doen, zonder dat dat uit de code zelf blijkt.
    //
    // Geeft de geraakte partij-id's terug i.p.v. zelf te cascaden: de cascade-check gebeurt in een
    // aparte, latere transactie (cascadeDeleteLegePartijen) zodat hij ook de partijen uit de
    // andere helft (contactgegevens) kan meenemen — zie verwijderInactieveRecords.
    @Transactional
    Set<UUID> verwijderInactieveVoorkeuren() {
        Instant nu = Instant.now();
        List<Voorkeur> kandidaten = Voorkeur.find(
                "verwijderdOp IS NULL AND COALESCE(lastUsedAt, createdAt) <= ?1", berekenGrens(nu)).list();

        if (overschrijdtMaxPerRun("voorkeur", kandidaten.size())) {
            return Set.of();
        }

        int verwijderd = 0;
        Set<UUID> geraaktePartijIds = new LinkedHashSet<>();

        for (Voorkeur voorkeur : kandidaten) {
            // Logging (en dus identiteitsresolutie) vóór de mutatie: een rij mag niet als
            // verwijderd achterblijven zonder logboek-vermelding.
            if (!logVerwijderingOfSlaOver("verwijderVoorkeurRetentie", VOORKEUR_PROCESSING_ACTIVITY_ID, voorkeur.getPartij(), voorkeur.id, "voorkeur")) {
                continue;
            }

            voorkeur.setVerwijderdOp(nu);
            geraaktePartijIds.add(voorkeur.getPartij().id);
            verwijderd++;
        }

        meterRegistry.counter("retentie.verwijderd", "type", "voorkeur").increment(verwijderd);
        LOG.info("Retentiescheduler: " + verwijderd + " voorkeuren verwijderd");

        return geraaktePartijIds;
    }

    // Niet private: zie toelichting bij verwijderInactieveVoorkeuren.
    @Transactional
    Set<UUID> verwijderInactieveContactgegevens() {
        Instant nu = Instant.now();
        List<Contactgegeven> kandidaten = Contactgegeven.find(
                "verwijderdOp IS NULL AND COALESCE(lastUsedAt, createdAt) <= ?1", berekenGrens(nu)).list();

        if (overschrijdtMaxPerRun("contactgegeven", kandidaten.size())) {
            return Set.of();
        }

        int verwijderd = 0;
        Set<UUID> geraaktePartijIds = new LinkedHashSet<>();

        for (Contactgegeven contact : kandidaten) {
            if (!logVerwijderingOfSlaOver("verwijderContactgegevenRetentie", CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID, contact.getPartij(), contact.id, "contactgegeven")) {
                continue;
            }

            contact.setVerwijderdOp(nu);
            geraaktePartijIds.add(contact.getPartij().id);
            verwijderd++;
        }

        meterRegistry.counter("retentie.verwijderd", "type", "contactgegeven").increment(verwijderd);
        LOG.info("Retentiescheduler: " + verwijderd + " contactgegevens verwijderd");

        return geraaktePartijIds;
    }

    // Niet private: zie toelichting bij verwijderInactieveVoorkeuren. Eigen transactie, ná de
    // twee soft-delete-fasen: een fout hier draait dus niet de al gecommitte voorkeur/
    // contactgegeven-verwijderingen terug, en omgekeerd blokkeert een fout dáár deze fase niet
    // (verwijderInactieveRecords vangt elke fase apart af).
    //
    // Dit is de enige plek die meerdere partij-rijen ná elkaar vergrendelt binnen één transactie
    // (zie PartijService.lockEnLeesVerwijerdOp). TreeSet sorteert op UUID vóór het vergrendelen:
    // zonder een vaste volgorde zouden twee gelijktijdige runs van deze methode (bv. bij falende
    // cluster-failover van concurrentExecution = SKIP, dat process-lokaal is, niet cluster-breed)
    // in principe elkaars rijen in omgekeerde volgorde kunnen vergrendelen — een klassieke
    // deadlock. Met een vaste volgorde is dat structureel uitgesloten, ongeacht of zo'n overlap
    // zich ooit voordoet.
    @Transactional
    void cascadeDeleteLegePartijen(Set<UUID> partijIds) {
        Instant nu = Instant.now();

        for (UUID id : new TreeSet<>(partijIds)) {
            Partij partij = Partij.findById(id);

            if (partij != null) {
                partijService.deleteLegePartij(partij, nu);
            }
        }
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
    // ProfielController.setDataSubjectFromPartij gooit voor dezelfde conditie wél een exception —
    // bewust anders: één HTTP-request afbreken kost weinig, een hele nachtelijke batch afbreken
    // (en daarmee alle andere kandidaten blokkeren) weegt hier zwaarder.
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
