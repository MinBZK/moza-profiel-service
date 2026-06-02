package nl.rijksoverheid.moz.job;

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
import nl.rijksoverheid.moz.mapper.PartijMapper;
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
    PartijMapper partijMapper;

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

    private UUID createVoorkeur(UUID partijId, Instant lastUsedAt, Instant teVerwijderenOp) {
        AtomicReference<UUID> id = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = Partij.findById(partijId);
            Voorkeur voorkeur = new Voorkeur();
            voorkeur.setVoorkeurType(VoorkeurType.WebsiteTaal);
            voorkeur.setWaarde("nl");
            voorkeur.setPartij(partij);
            voorkeur.setLastUsedAt(lastUsedAt);
            voorkeur.setTeVerwijderenOp(teVerwijderenOp);
            voorkeur.persist();
            id.set(voorkeur.id);
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
        return Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofMonths(79)).toInstant();
    }

    @Test
    void voorkeur_lastUsedAtOud_KrijgtTeVerwijderenOp() {
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), null);

        retentieScheduler.stelTeVerwijderenOpIn();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNotNull(voorkeur.getTeVerwijderenOp(),
                    "teVerwijderenOp must be set for a record unused for more than 6.5 years");
            Assertions.assertTrue(voorkeur.isTeVerwijderenOpAutomatisch(),
                    "teVerwijderenOpAutomatisch must be true when set by the scheduler");
            // Should be approximately now + 6 months
            Instant verwacht = Instant.now().atZone(ZoneOffset.UTC).plus(Period.ofMonths(6)).toInstant();
            Assertions.assertTrue(voorkeur.getTeVerwijderenOp().isBefore(verwacht.plusSeconds(5)),
                    "teVerwijderenOp moet circa nu+6 maanden zijn");
        });
    }

    @Test
    void voorkeur_lastUsedAtNull_createdAtOud_KrijgtTeVerwijderenOp() {
        // lastUsedAt is null → COALESCE falls back to createdAt
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, null, null);
        setCreatedAt(voorkeurId, ouderDanGrens());

        retentieScheduler.stelTeVerwijderenOpIn();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNotNull(voorkeur.getTeVerwijderenOp(),
                    "teVerwijderenOp must be set when lastUsedAt is null and createdAt is old (COALESCE fallback)");
        });
    }

    @Test
    void voorkeur_recentGebruikt_WordtNietAangepast() {
        UUID partijId = createPartij();
        // lastUsedAt is 1 year ago — well within the 6.5-year threshold
        Instant recentGebruikt = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1)).toInstant();
        UUID voorkeurId = createVoorkeur(partijId, recentGebruikt, null);

        retentieScheduler.stelTeVerwijderenOpIn();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNull(voorkeur.getTeVerwijderenOp(),
                    "teVerwijderenOp must remain null for a recently used record");
        });
    }

    @Test
    void voorkeur_teVerwijderenOpAlGezet_WordtNietOverschreven() {
        UUID partijId = createPartij();
        Instant bestaandeWaarde = Instant.now().atZone(ZoneOffset.UTC).plus(Period.ofMonths(3)).toInstant().truncatedTo(ChronoUnit.MICROS);
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), bestaandeWaarde);

        retentieScheduler.stelTeVerwijderenOpIn();

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertEquals(bestaandeWaarde, voorkeur.getTeVerwijderenOp(),
                    "An already-set teVerwijderenOp must not be overwritten by the scheduler");
        });
    }

    @Test
    void voorkeur_gebruiktNaSchedulerFlag_ClearsTeVerwijderenOp() {
        // Record is flagged by the scheduler, then the Partij uses it again → flag cleared.
        UUID partijId = createPartij();
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), null);

        retentieScheduler.stelTeVerwijderenOpIn();

        // Simulate the record being read/used via the mapper
        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            partijMapper.toVoorkeurResponse(voorkeur);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertNull(voorkeur.getTeVerwijderenOp(),
                    "teVerwijderenOp must be cleared when a scheduler-flagged record is used again");
            Assertions.assertFalse(voorkeur.isTeVerwijderenOpAutomatisch(),
                    "teVerwijderenOpAutomatisch must be reset after the record is used again");
        });
    }

    @Test
    void voorkeur_manueleTeVerwijderenOp_BlijftBijGebruik() {
        // teVerwijderenOp was set manually (flag = false) → mapper must not clear it on use.
        UUID partijId = createPartij();
        Instant manueleWaarde = Instant.now().atZone(ZoneOffset.UTC).plus(Period.ofMonths(6)).toInstant().truncatedTo(ChronoUnit.MICROS);
        UUID voorkeurId = createVoorkeur(partijId, ouderDanGrens(), manueleWaarde);
        // teVerwijderenOpAutomatisch stays false (default)

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            partijMapper.toVoorkeurResponse(voorkeur);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Voorkeur voorkeur = Voorkeur.findById(voorkeurId);
            Assertions.assertEquals(manueleWaarde, voorkeur.getTeVerwijderenOp(),
                    "A manually-set teVerwijderenOp must survive record usage");
        });
    }

    @Test
    void contactgegeven_lastUsedAtOud_KrijgtTeVerwijderenOp() {
        // Wiring check: confirms the scheduler also processes Contactgegeven records.
        AtomicReference<UUID> contactId = new AtomicReference<>();
        QuarkusTransaction.requiringNew().run(() -> {
            Partij partij = new Partij();
            partij.addIdentificatie(new Identificatie(IdentificatieType.BSN, "999999999"));
            partij.persist();

            Contactgegeven contact = new Contactgegeven();
            contact.setType(ContactType.Telefoonnummer);
            contact.setWaarde("0612345678");
            contact.setPartij(partij);
            contact.setLastUsedAt(ouderDanGrens());
            contact.persist();
            contactId.set(contact.id);
        });

        retentieScheduler.stelTeVerwijderenOpIn();

        QuarkusTransaction.requiringNew().run(() -> {
            Contactgegeven contact = Contactgegeven.findById(contactId.get());
            Assertions.assertNotNull(contact.getTeVerwijderenOp(),
                    "teVerwijderenOp must be set on Contactgegeven when unused for more than 6.5 years");
            Assertions.assertTrue(contact.isTeVerwijderenOpAutomatisch(),
                    "teVerwijderenOpAutomatisch must be true when set by the scheduler");
        });
    }
}
