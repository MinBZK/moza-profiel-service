package nl.rijksoverheid.moz.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@QuarkusTest
public class RetentieSchedulerTest {

    @Inject
    RetentieScheduler retentieScheduler;

    @Inject
    MeterRegistry meterRegistry;

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
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "123456789"));
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
        // lastUsedAt is null → COALESCE falls back to createdAt
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
        // lastUsedAt is 1 year ago — well within the 7-year threshold
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
        // Beide laatste actieve children verlopen in dezelfde run: verwijderInactieveVoorkeuren en
        // verwijderInactieveContactgegevens verzamelen elk alleen de partij-id (eigen transactie,
        // cascaden zelf niet meer); verwijderInactieveRecords combineert beide sets tot één, en de
        // cascade-check draait daarna, ná beide commits, precies één keer in cascadeDeleteLegePartijen.
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
    void teVeelKandidaten_SlaatRunOverInPlaatsVanTeVerwijderen() {
        // retentie.scheduler.max-per-run wordt live opgezocht (ConfigProvider), niet via
        // @ConfigProperty-veldinjectie — daardoor pakt een system property override hem hier op.
        System.setProperty("retentie.scheduler.max-per-run", "1");
        try {
            UUID partijId = createPartij();
            UUID voorkeur1 = createVoorkeur(partijId, ouderDanGrens(), null);
            UUID voorkeur2 = createVoorkeur(partijId, ouderDanGrens(), null);

            retentieScheduler.verwijderInactieveRecords();

            QuarkusTransaction.requiringNew().run(() -> {
                Assertions.assertNull(Voorkeur.<Voorkeur>findById(voorkeur1).getVerwijderdOp(),
                        "run met meer kandidaten dan de grens moet volledig worden overgeslagen");
                Assertions.assertNull(Voorkeur.<Voorkeur>findById(voorkeur2).getVerwijderdOp(),
                        "run met meer kandidaten dan de grens moet volledig worden overgeslagen");
            });
        } finally {
            System.clearProperty("retentie.scheduler.max-per-run");
        }
    }

    @Test
    void voorkeur_partijZonderIdentificatie_WordtOvergeslagen_AnderenBlijvenVerwijderd() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "type", "voorkeur").count();

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

        Assertions.assertEquals(anomalieVoor + 1, meterRegistry.counter("retentie.anomalie", "type", "voorkeur").count());
    }

    @Test
    void contactgegeven_partijZonderIdentificatie_WordtOvergeslagen_AnderenBlijvenVerwijderd() {
        double anomalieVoor = meterRegistry.counter("retentie.anomalie", "type", "contactgegeven").count();

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

        Assertions.assertEquals(anomalieVoor + 1, meterRegistry.counter("retentie.anomalie", "type", "contactgegeven").count());
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
}
