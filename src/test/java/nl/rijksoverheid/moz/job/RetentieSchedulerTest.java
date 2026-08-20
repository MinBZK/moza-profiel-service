package nl.rijksoverheid.moz.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import nl.rijksoverheid.moz.helper.HashHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@QuarkusTest
public class RetentieSchedulerTest {

    private static final String VOORKEUR_PROCESSING_ACTIVITY_ID = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-630";
    private static final String CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID = "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-631";

    @Inject
    RetentieScheduler retentieScheduler;

    // Alleen gebruikt om één interne fasemethode te stubben (zie de fase-fout-test): een spy
    // wrapt de echte bean, dus verwijderInactieveRecords()'s eigen catch/anomalie/gauge-logica
    // draait echt mee i.p.v. herimplementeerd te worden in de test.
    @InjectSpy
    RetentieScheduler retentieSchedulerSpy;

    @Inject
    MeterRegistry meterRegistry;

    @InjectMock
    ProcessingHandler processingHandler;

    @Inject
    HashHelper hashHelper;

    @BeforeEach
    void stubProcessingHandler() {
        // Elke soft-delete triggert een span; zonder stub geeft de gemockte startSpan() null
        // terug en NPE't de daaropvolgende span.end() in RetentieScheduler.
        Mockito.doReturn(Mockito.mock(Span.class)).when(processingHandler).startSpan(Mockito.anyString(), Mockito.any());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        ScopeContactgegeven.deleteAll();
        ScopeVoorkeur.deleteAll();
        Contactgegeven.deleteAll();
        Voorkeur.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
    }

    private UUID createPartij() {
        return createPartij("123456789");
    }

    // Expliciete BSN i.p.v. altijd dezelfde hardcoded waarde: een test die twee gelijktijdig
    // actieve partijen nodig heeft, moet ze een eigen BSN geven — anders staan er twee actieve
    // Identificatie-rijen met hetzelfde (type, nummer), wat alleen "werkt" omdat het H2-testschema
    // uk_identificatie niet afdwingt (die is er wél in productie, zie MigrationValidationTest).
    private UUID createPartij(String bsn) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, bsn));
            partij.persist();
            id.set(partij.id);
        });
        return id.get();
    }

    private UUID createVoorkeur(UUID partijId, Instant lastUsedAt, Instant verwijderdOp) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.setLastUsedAt(lastUsedAt);
            voorkeur.setVerwijderdOp(verwijderdOp);
            voorkeur.persist();
            id.set(voorkeur.id);
        });
        return id.get();
    }

    private UUID createContactgegeven(UUID partijId, Instant lastUsedAt, Instant verwijderdOp) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.setLastUsedAt(lastUsedAt);
            contact.setVerwijderdOp(verwijderdOp);
            contact.persist();
            id.set(contact.id);
        });
        return id.get();
    }

    /** Backdoor to set createdAt (normally immutable via @PrePersist) to a past date. */
    private void setCreatedAt(UUID voorkeurId, Instant createdAt) {
        QuarkusTransaction.requiringNew().run(() ->
            Voorkeur.update("createdAt = :ts WHERE id = :id", Map.of("ts", createdAt, "id", voorkeurId))
        );
    }

    private static Instant ouderDanGrens() {
        return Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(7)).minusDays(1).toInstant();
    }

    /** Net binnen de grens: 7 jaar min 1 dag oud, dus nog niet in aanmerking voor verwijdering. */
    private static Instant binnenGrens() {
        return Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(7)).plusDays(1).toInstant();
    }

    @Test
    void voorkeur_lastUsedAtOud_KrijgtVerwijderdOp() {
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), null);

        // Kleine marge i.p.v. exact "voor": zie toelichting bij PartijServiceTest.
        Instant voor = Instant.now().minusMillis(50);
        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNotNull(voorkeur.getVerwijderdOp(),
                    "verwijderdOp must be set for a record unused for more than 7 years");
            Assertions.assertFalse(voorkeur.getVerwijderdOp().isBefore(voor), "verwijderdOp moet circa nu zijn");
        });
    }

    @Test
    void voorkeur_lastUsedAtOud_LaatsteActieveKind_VerwijdertOokPartij() {
        UUID partijId = createPartij();
        createVoorkeur(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            Assertions.assertNotNull(partij.getVerwijderdOp(),
                    "partij zonder actieve children meer moet ook door de retentiescheduler soft-deleted worden");
            Assertions.assertTrue(partij.getIdentificaties().stream().allMatch(i -> i.getVerwijderdOp() != null),
                    "identificaties van een gecascadete partij moeten ook mee-cascaden (uk_identificatie is partieel)");
        });
    }

    @Test
    void voorkeur_lastUsedAtOud_AndereContactgegevenActief_PartijBlijftActief() {
        // Bewijst dat de cascade-check in deleteLegePartij symmetrisch is: het maakt niet uit of
        // de retentiescheduler een Voorkeur of een Contactgegeven soft-delete, de cascade kijkt
        // altijd naar beide typen. verwijderInactieveVoorkeuren draait vóór
        // verwijderInactieveContactgegevens, dus dit toetst ook dat de partij niet per ongeluk
        // cascadet vanuit de eerste van de twee loops terwijl er nog een actief contactgegeven is.
        UUID partijId = createPartij();
        createVoorkeur(partijId, ouderDanGrens(), null);
        Instant recentGebruikt = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1)).toInstant();
        createContactgegeven(partijId, recentGebruikt, null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Partij.<Partij>findById(partijId).getVerwijderdOp(),
                        "partij met nog een actief contactgegeven mag niet verwijderd worden"));
    }

    @Test
    void voorkeur_lastUsedAtNull_createdAtOud_KrijgtVerwijderdOp() {
        // lastUsedAt is null → COALESCE valt terug op createdAt
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, null, null);
        setCreatedAt(voorkeurId, ouderDanGrens());

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNotNull(voorkeur.getVerwijderdOp(),
                    "verwijderdOp must be set when lastUsedAt is null and createdAt is old (COALESCE fallback)");
        });
    }

    @Test
    void voorkeur_recentGebruikt_WordtNietAangepast() {
        UUID partijId = createPartij();
        // lastUsedAt is 1 jaar geleden — ruim binnen de 7-jaarsgrens
        Instant recentGebruikt = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1)).toInstant();
        UUID voorkeurId = createVoorkeur(partijId, recentGebruikt, null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNull(voorkeur.getVerwijderdOp(),
                    "verwijderdOp must remain null for a recently used record");
        });
    }

    @Test
    void voorkeur_netBinnenGrens_WordtNietAangepast() {
        // Onderscheidt de exacte 7-jaarsgrens van bv. een verschrijving als Period.ofMonths(7):
        // "1 jaar oud" (bestaande test) zou zo'n fout niet vangen, dit net-binnen-de-grens geval wel.
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, binnenGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNull(voorkeur.getVerwijderdOp(), "een record net binnen de retentiegrens mag niet verwijderd worden");
        });
    }

    @Test
    void voorkeur_verwijderdOpAlGezet_WordtNietOverschreven() {
        UUID partijId = createPartij();
        Instant bestaandeWaarde = Instant.now().minus(Period.ofDays(3)).truncatedTo(ChronoUnit.MICROS);
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), bestaandeWaarde);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertEquals(bestaandeWaarde, voorkeur.getVerwijderdOp(),
                    "An already-set verwijderdOp must not be overwritten by the scheduler");
        });
    }

    @Test
    void contactgegeven_lastUsedAtOud_KrijgtVerwijderdOp() {
        UUID partijId = createPartij();
        UUID contactId = createContactgegeven(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId);
            Assertions.assertNotNull(contact.getVerwijderdOp(),
                    "verwijderdOp must be set on Contactgegeven when unused for more than 7 years");
        });
    }

    @Test
    void contactgegeven_lastUsedAtOud_LaatsteActieveKind_VerwijdertOokPartij() {
        UUID partijId = createPartij();
        createContactgegeven(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            Assertions.assertNotNull(partij.getVerwijderdOp(),
                    "partij zonder actieve children meer moet ook door de retentiescheduler soft-deleted worden");
            Assertions.assertTrue(partij.getIdentificaties().stream().allMatch(i -> i.getVerwijderdOp() != null),
                    "identificaties van een gecascadete partij moeten ook mee-cascaden (uk_identificatie is partieel)");
        });
    }

    @Test
    void contactgegeven_lastUsedAtOud_AndereVoorkeurActief_PartijBlijftActief() {
        UUID partijId = createPartij();
        createContactgegeven(partijId, ouderDanGrens(), null);
        // Niet-verlopen voorkeur op dezelfde partij: die telt als actieve child.
        Instant recentGebruikt = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1)).toInstant();
        createVoorkeur(partijId, recentGebruikt, null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNull(Partij.<Partij>findById(partijId).getVerwijderdOp(),
                        "partij met nog een actieve voorkeur mag niet verwijderd worden"));
    }

    @Test
    void voorkeurEnContactgegevenBeideOud_VerwijdertOokPartijInZelfdeRun() {
        // Beide laatste actieve children verlopen in dezelfde run: bewijst dat cascadeDeleteLegePartijen
        // pas ná beide commits draait en de partij dan precies één keer oppikt via zijn eigen
        // reconciliatiequery, ongeacht welke van de twee fasen als laatste de partij leegmaakte.
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), null);
        UUID contactId = createContactgegeven(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertNotNull(Voorkeur.<Voorkeur>findById(voorkeurId).getVerwijderdOp());
            Assertions.assertNotNull(Contactgegeven.<Contactgegeven>findById(contactId).getVerwijderdOp());
            Partij partij = Partij.findById(partijId);
            Assertions.assertNotNull(partij.getVerwijderdOp(),
                    "partij moet ook verwijderd zijn nadat beide laatste actieve children in dezelfde run geveegd zijn");
            Assertions.assertTrue(partij.getIdentificaties().stream().allMatch(i -> i.getVerwijderdOp() != null),
                    "identificaties van een gecascadete partij moeten ook mee-cascaden (uk_identificatie is partieel)");
        });
    }

    @Test
    void contactgegeven_recentGebruikt_WordtNietAangepast() {
        UUID partijId = createPartij();
        Instant recentGebruikt = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1)).toInstant();
        UUID contactId = createContactgegeven(partijId, recentGebruikt, null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId);
            Assertions.assertNull(contact.getVerwijderdOp(), "verwijderdOp must remain null for a recently used record");
        });
    }

    @Test
    void contactgegeven_verwijderdOpAlGezet_WordtNietOverschreven() {
        UUID partijId = createPartij();
        Instant bestaandeWaarde = Instant.now().minus(Period.ofDays(3)).truncatedTo(ChronoUnit.MICROS);
        UUID contactId = createContactgegeven(partijId, ouderDanGrens(), bestaandeWaarde);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId);
            Assertions.assertEquals(bestaandeWaarde, contact.getVerwijderdOp(),
                    "An already-set verwijderdOp must not be overwritten by the scheduler");
        });
    }

    @Test
    void teVeelKandidaten_VerwerktSlechtsTotDeGrens() {
        // retentie.scheduler.max-per-run wordt live opgezocht (ConfigProvider), niet via
        // @ConfigProperty-veldinjectie — daardoor pakt een system property override hem hier op.
        System.setProperty("retentie.scheduler.max-per-run", "1");
        try {
            double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "max-per-run").count();

            UUID partijId = createPartij();
            Instant ouderste = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(8)).toInstant();
            // Minder-oude eerst aangemaakt, oudste ná: invoegvolgorde staat zo haaks op de
            // verwachte verwerkingsvolgorde, dus alleen een echte ORDER BY (niet toevallig de
            // scan-/invoegvolgorde) kan deze test laten slagen.
            UUID minderOudeVoorkeur = createVoorkeur(partijId, ouderDanGrens(), null);
            UUID oudsteVoorkeur = createVoorkeur(partijId, ouderste, null);

            retentieScheduler.verwijderInactieveRecords();

            QuarkusTransaction.requiringNew().run(() -> {
                Assertions.assertNotNull(Voorkeur.<Voorkeur>findById(oudsteVoorkeur).getVerwijderdOp(),
                        "bij een overschrijding van de grens moet de langst-inactieve rij als eerste verwerkt worden");
                Assertions.assertNull(Voorkeur.<Voorkeur>findById(minderOudeVoorkeur).getVerwijderdOp(),
                        "verwerking moet beperkt blijven tot de grens, niet de hele fase overslaan");
            });

            Assertions.assertEquals(anomalieVoor + 1,
                    meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "max-per-run").count());
        } finally {
            System.clearProperty("retentie.scheduler.max-per-run");
        }
    }

    @Test
    void voorkeur_partijZonderIdentificatie_WordtOvergeslagen_AnderenBlijvenVerwijderd() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "ontbrekende-identificatie").count();

        UUID corruptePartijId = createPartijZonderIdentificatie();
        UUID corrupteVoorkeurId = createVoorkeur(corruptePartijId, ouderDanGrens(), null);

        UUID geldigePartijId = createPartij();
        UUID geldigeVoorkeurId = createVoorkeur(geldigePartijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertNull(Voorkeur.<Voorkeur>findById(corrupteVoorkeurId).getVerwijderdOp(),
                    "een rij van een partij zonder identificatie mag niet verwijderd worden zonder logboek-vermelding");
            Assertions.assertNotNull(Voorkeur.<Voorkeur>findById(geldigeVoorkeurId).getVerwijderdOp(),
                    "een geldige kandidaat mag niet geblokkeerd raken door een corrupte partij elders in de batch");
        });

        Assertions.assertEquals(anomalieVoor + 1,
                meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "ontbrekende-identificatie").count());
    }

    @Test
    void contactgegeven_partijZonderIdentificatie_WordtOvergeslagen_AnderenBlijvenVerwijderd() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "contactgegeven", "reden", "ontbrekende-identificatie").count();

        UUID corruptePartijId = createPartijZonderIdentificatie();
        UUID corrupteContactId = createContactgegeven(corruptePartijId, ouderDanGrens(), null);

        UUID geldigePartijId = createPartij();
        UUID geldigeContactId = createContactgegeven(geldigePartijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        QuarkusTransaction.requiringNew().run(() -> {
            Assertions.assertNull(Contactgegeven.<Contactgegeven>findById(corrupteContactId).getVerwijderdOp(),
                    "een rij van een partij zonder identificatie mag niet verwijderd worden zonder logboek-vermelding");
            Assertions.assertNotNull(Contactgegeven.<Contactgegeven>findById(geldigeContactId).getVerwijderdOp(),
                    "een geldige kandidaat mag niet geblokkeerd raken door een corrupte partij elders in de batch");
        });

        Assertions.assertEquals(anomalieVoor + 1,
                meterRegistry.counter("retentie.anomalie", "entiteit", "contactgegeven", "reden", "ontbrekende-identificatie").count());
    }

    private UUID createPartijZonderIdentificatie() {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.persist();
            id.set(partij.id);
        });
        return id.get();
    }

    @Test
    void voorkeur_lastUsedAtOud_VerhoogtVerwijderdCounterEnGeslaagdeRunGauge() {
        double verwijderdVoor = meterRegistry.counter("retentie.verwijderd", "type", "voorkeur").count();

        UUID partijId = createPartij();
        createVoorkeur(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        Assertions.assertEquals(verwijderdVoor + 1,
                meterRegistry.counter("retentie.verwijderd", "type", "voorkeur").count());
        // Vergelijkt met laatste_run i.p.v. een vóór-de-run vastgelegde tijdstempel: beide gauges
        // zijn een gedeelde AtomicLong over alle tests in deze klasse (@ApplicationScoped bean),
        // en met tests in de orde van milliseconden zou "gauge >= voorEpoch" ook slagen als de
        // geslaagde-run-gauge helemaal niet was bijgewerkt door déze run. Gelijkheid met
        // laatste_run (die altijd wordt bijgewerkt) is dat niet: zie de fase-fout-test hieronder
        // voor het geval waarin ze uiteen moeten lopen.
        Assertions.assertEquals(meterRegistry.get("retentie.laatste_run_epoch_seconds").gauge().value(),
                meterRegistry.get("retentie.laatste_geslaagde_run_epoch_seconds").gauge().value(),
                "een volledig geslaagde run moet de geslaagde-run-gauge gelijk trekken met de laatste-run-gauge");
    }

    /**
     * Bewijst het kernpunt van de eerste reviewronde: een gefaalde fase mag de scheduler niet
     * "gezond" laten lijken. verwijderInactieveVoorkeuren() wordt gestubd om te falen; de andere
     * twee fasen draaien echt door, en de geslaagde-run-gauge mag niet bijgewerkt worden.
     */
    @Test
    void voorkeurFaseGooitException_VerhoogtAnomalieEnHoudtGeslaagdeRunGaugeAchter() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "fase-fout").count();
        double geslaagdVoor = meterRegistry.get("retentie.laatste_geslaagde_run_epoch_seconds").gauge().value();

        UUID partijId = createPartij();
        UUID contactId = createContactgegeven(partijId, ouderDanGrens(), null);

        Mockito.doThrow(new RuntimeException("kapot")).when(retentieSchedulerSpy).verwijderInactieveVoorkeuren();

        retentieSchedulerSpy.verwijderInactieveRecords();

        Assertions.assertEquals(anomalieVoor + 1,
                meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "fase-fout").count());
        Assertions.assertEquals(geslaagdVoor, meterRegistry.get("retentie.laatste_geslaagde_run_epoch_seconds").gauge().value(),
                "een gefaalde fase mag de geslaagde-run-gauge niet bijwerken");

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNotNull(Contactgegeven.<Contactgegeven>findById(contactId).getVerwijderdOp(),
                        "de contactgegeven-fase moet gewoon doorlopen ondanks dat de voorkeur-fase faalde"));
    }

    @Test
    void aantalKandidatenExactOpDeGrens_WordtVolledigVerwerktZonderAnomalie() {
        // <= op de grens (niet <): dit onderscheidt "exact vol" van "één te veel". Met < in
        // plaats van <= zou dit geval ten onrechte als overschrijding behandeld worden.
        System.setProperty("retentie.scheduler.max-per-run", "2");
        try {
            UUID partij1 = createPartij("111111111");
            UUID voorkeur1 = createVoorkeur(partij1, ouderDanGrens(), null);
            UUID partij2 = createPartij("222222222");
            UUID voorkeur2 = createVoorkeur(partij2, ouderDanGrens(), null);

            double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "max-per-run").count();

            retentieScheduler.verwijderInactieveRecords();

            QuarkusTransaction.requiringNew().run(() -> {
                Assertions.assertNotNull(Voorkeur.<Voorkeur>findById(voorkeur1).getVerwijderdOp(),
                        "exact op de grens moeten alle kandidaten verwerkt worden, niet slechts een deel");
                Assertions.assertNotNull(Voorkeur.<Voorkeur>findById(voorkeur2).getVerwijderdOp(),
                        "exact op de grens moeten alle kandidaten verwerkt worden, niet slechts een deel");
            });

            Assertions.assertEquals(anomalieVoor,
                    meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "max-per-run").count(),
                    "exact op de grens is geen overschrijding, dus geen anomalie");
        } finally {
            System.clearProperty("retentie.scheduler.max-per-run");
        }
    }

    /**
     * Bewijst dat de succespad-logboekvermelding daadwerkelijk wordt geëmitteerd — niet alleen
     * dat de rij verwijderd wordt. Zonder deze test zou resolveerIdentiteitOfSlaOver kunnen
     * teruggeven zonder ooit registreerLogboekNaCommit aan te roepen, en zou niets dat merken:
     * dat is precies de rechtvaardiging voor het per-rij laden i.p.v. een bulk-update.
     */
    @Test
    void voorkeur_lastUsedAtOud_EmitLogboekSpanMetJuisteActiviteitId() {
        UUID partijId = createPartij();
        createVoorkeur(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        Mockito.verify(processingHandler).startSpan(Mockito.eq("verwijderVoorkeurRetentie"), Mockito.any());

        ArgumentCaptor<LogboekContext> captor = ArgumentCaptor.forClass(LogboekContext.class);
        Mockito.verify(processingHandler).addLogboekContextToSpan(Mockito.any(), captor.capture());
        Assertions.assertEquals(VOORKEUR_PROCESSING_ACTIVITY_ID, captor.getValue().getProcessingActivityId());
        // Niet alleen de constante: dataSubjectId/Type zijn de eigenlijke persoonsgegevens in de
        // vermelding, en de enige reden dat deze scheduler per rij laadt i.p.v. een bulk-update.
        Assertions.assertEquals(hashHelper.hashIdentifier("123456789"), captor.getValue().getDataSubjectId());
        Assertions.assertEquals("BSN", captor.getValue().getDataSubjectType());
    }

    @Test
    void contactgegeven_lastUsedAtOud_EmitLogboekSpanMetJuisteActiviteitId() {
        UUID partijId = createPartij();
        createContactgegeven(partijId, ouderDanGrens(), null);

        retentieScheduler.verwijderInactieveRecords();

        Mockito.verify(processingHandler).startSpan(Mockito.eq("verwijderContactgegevenRetentie"), Mockito.any());

        ArgumentCaptor<LogboekContext> captor = ArgumentCaptor.forClass(LogboekContext.class);
        Mockito.verify(processingHandler).addLogboekContextToSpan(Mockito.any(), captor.capture());
        Assertions.assertEquals(CONTACTGEGEVEN_PROCESSING_ACTIVITY_ID, captor.getValue().getProcessingActivityId());
        Assertions.assertEquals(hashHelper.hashIdentifier("123456789"), captor.getValue().getDataSubjectId());
        Assertions.assertEquals("BSN", captor.getValue().getDataSubjectType());
    }

    /**
     * Bewijst de andere helft van de "pas ná commit"-garantie: als de transactie die de soft-
     * delete bevat rollbackt, mag er geen enkele logboek-span geëmitteerd worden. Zonder deze
     * test zou het verwijderen van registerInterposedSynchronization (en inline emitteren, vóór
     * commit) alle andere tests in deze klasse gewoon groen laten.
     */
    @Test
    void voorkeurFaseRolledBack_EmitGeenLogboekSpan() {
        UUID partijId = createPartij();
        createVoorkeur(partijId, ouderDanGrens(), null);

        Assertions.assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    retentieScheduler.verwijderInactieveVoorkeuren();
                    throw new RuntimeException("forceer rollback ná de soft-delete, vóór commit");
                }));

        Mockito.verify(processingHandler, Mockito.never()).startSpan(Mockito.anyString(), Mockito.any());
    }

    /**
     * Bewijst dat registreerLogboekNaCommit's per-identiteit try/catch werkt: één span die faalt
     * (startSpan gooit voor de eerste kandidaat) mag de rest van de batch niet stilzwijgend
     * overslaan. Zonder de try/catch in de afterCompletion-loop zou de tweede span nooit
     * geëmitteerd worden en zou dit alleen aan de anomalie-teller te zien zijn geweest.
     */
    @Test
    void voorkeur_EenLogboekSpanFaaltInBatch_OverigeWordtTochGeemitteerdEnAnomalieOpgehoogd() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "logboek-fout").count();

        UUID partijId1 = createPartij("111111111");
        createVoorkeur(partijId1, ouderDanGrens(), null);
        UUID partijId2 = createPartij("222222222");
        createVoorkeur(partijId2, ouderDanGrens(), null);

        Mockito.doThrow(new RuntimeException("kapot"))
                .doReturn(Mockito.mock(Span.class))
                .when(processingHandler).startSpan(Mockito.anyString(), Mockito.any());

        retentieScheduler.verwijderInactieveRecords();

        Mockito.verify(processingHandler, Mockito.times(2)).startSpan(Mockito.anyString(), Mockito.any());
        Assertions.assertEquals(anomalieVoor + 1,
                meterRegistry.counter("retentie.anomalie", "entiteit", "voorkeur", "reden", "logboek-fout").count());
    }

    /**
     * Mirror van voorkeurFaseGooitException_VerhoogtAnomalieEnHoudtGeslaagdeRunGaugeAchter:
     * bewijst dat dezelfde fase-fout-afhandeling ook voor de contactgegeven-fase werkt, niet
     * alleen voor voorkeuren (die twee catch-blokken zijn los van elkaar geschreven).
     */
    @Test
    void contactgegevenFaseGooitException_VerhoogtAnomalieEnHoudtGeslaagdeRunGaugeAchter() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "entiteit", "contactgegeven", "reden", "fase-fout").count();
        double geslaagdVoor = meterRegistry.get("retentie.laatste_geslaagde_run_epoch_seconds").gauge().value();

        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), null);

        Mockito.doThrow(new RuntimeException("kapot")).when(retentieSchedulerSpy).verwijderInactieveContactgegevens();

        retentieSchedulerSpy.verwijderInactieveRecords();

        Assertions.assertEquals(anomalieVoor + 1,
                meterRegistry.counter("retentie.anomalie", "entiteit", "contactgegeven", "reden", "fase-fout").count());
        Assertions.assertEquals(geslaagdVoor, meterRegistry.get("retentie.laatste_geslaagde_run_epoch_seconds").gauge().value(),
                "een gefaalde fase mag de geslaagde-run-gauge niet bijwerken");

        QuarkusTransaction.requiringNew().run(() ->
                Assertions.assertNotNull(Voorkeur.<Voorkeur>findById(voorkeurId).getVerwijderdOp(),
                        "de voorkeur-fase moet gewoon doorlopen ondanks dat de contactgegeven-fase faalde"));
    }
}
