package nl.rijksoverheid.moz.job;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Map;

@ApplicationScoped
public class RetentieScheduler {

    private static final Logger LOG = Logger.getLogger(RetentieScheduler.class);

    private static final Period RETENTIE_GRENS = Period.ofYears(7);

    @Scheduled(cron = "{retentie.scheduler.cron}")
    @Transactional
    public void verwijderInactieveRecords() {
        Instant nu = Instant.now();
        Instant grens = nu.atZone(ZoneOffset.UTC).minus(RETENTIE_GRENS).toInstant();

        long voorkeurCount = Voorkeur.update(
                "verwijderdOp = :nu, lastUpdated = :nu " +
                "WHERE verwijderdOp IS NULL " +
                "AND COALESCE(lastUsedAt, createdAt) <= :grens",
                Map.of("nu", nu, "grens", grens));

        long contactCount = Contactgegeven.update(
                "verwijderdOp = :nu, lastUpdated = :nu " +
                "WHERE verwijderdOp IS NULL " +
                "AND COALESCE(lastUsedAt, createdAt) <= :grens",
                Map.of("nu", nu, "grens", grens));

        LOG.info("Retentiescheduler: " + voorkeurCount + " voorkeuren, " + contactCount + " contactgegevens verwijderd");
    }
}
