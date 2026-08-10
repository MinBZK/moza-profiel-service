package nl.rijksoverheid.moz;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Partij;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Invariants enforced by partial unique indexes in the Flyway schema. Hibernate
 * cannot express these on entities, so they only exist because tests run against
 * the real migrated PostgreSQL schema.
 */
@QuarkusTest
class DatabaseInvariantsTest {

    @AfterEach
    @Transactional
    void tearDown() {
        Contactgegeven.deleteAll();
        DienstverlenerDienst.deleteAll();
        Dienstverlener.deleteAll();
        Partij.deleteAll();
    }

    @Test
    void tweedeDefaultContactgegevenVanZelfdeTypeWordtGeweigerd() {
        UUID partijId = QuarkusTransaction.requiringNew().call(() -> {
            Partij partij = new Partij();
            partij.persist();
            persistDefaultEmail(partij, "eerste@example.com");
            return partij.id;
        });

        ConstraintViolationException violation = expectConstraintViolation(
                () -> persistDefaultEmail(Partij.findById(partijId), "tweede@example.com"));

        assertEquals("contactgegeven_default_per_type", violation.getConstraintName());
    }

    @Test
    void tweedeDvBredeKoppelingVoorZelfdeDienstverlenerWordtGeweigerd() {
        UUID dienstverlenerId = QuarkusTransaction.requiringNew().call(() -> {
            Dienstverlener dienstverlener = new Dienstverlener();
            dienstverlener.setNaam("dv-" + UUID.randomUUID());
            dienstverlener.persist();
            new DienstverlenerDienst(dienstverlener, null).persist();
            return dienstverlener.id;
        });

        ConstraintViolationException violation = expectConstraintViolation(
                () -> new DienstverlenerDienst(Dienstverlener.findById(dienstverlenerId), null).persistAndFlush());

        assertEquals("uk_dvdienst_dv_broad", violation.getConstraintName());
    }

    private static void persistDefaultEmail(Partij partij, String waarde) {
        Contactgegeven contactgegeven = new Contactgegeven();
        contactgegeven.setPartij(partij);
        contactgegeven.setType(ContactType.Email);
        contactgegeven.setWaarde(waarde);
        contactgegeven.setIsDefault(true);
        contactgegeven.persistAndFlush();
    }

    private static ConstraintViolationException expectConstraintViolation(Runnable insert) {
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> QuarkusTransaction.requiringNew().run(insert::run));

        Throwable cause = failure;

        while (cause != null && !(cause instanceof ConstraintViolationException)) {
            cause = cause.getCause();
        }

        assertNotNull(cause, "expected a ConstraintViolationException, got: " + failure);

        return (ConstraintViolationException) cause;
    }
}
