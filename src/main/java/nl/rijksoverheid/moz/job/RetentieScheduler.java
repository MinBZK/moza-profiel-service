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

    // 6,5 jaar = 78 maanden
    private static final Period RETENTIE_GRENS = Period.ofMonths(78);
    private static final Period RETENTIE_VENSTER = Period.ofMonths(6);

    @Scheduled(cron = "{retentie.scheduler.cron}")
    @Transactional
    public void stelTeVerwijderenOpIn() {
        Instant nu = Instant.now();
        Instant grens = nu.atZone(ZoneOffset.UTC).minus(RETENTIE_GRENS).toInstant();
        Instant teVerwijderenOp = nu.atZone(ZoneOffset.UTC).plus(RETENTIE_VENSTER).toInstant();

        long voorkeurCount = Voorkeur.update(
                "teVerwijderenOp = :tvop, teVerwijderenOpAutomatisch = true, lastUpdated = :nu " +
                "WHERE teVerwijderenOp IS NULL " +
                "AND COALESCE(lastUsedAt, createdAt) <= :grens",
                Map.of("tvop", teVerwijderenOp, "nu", nu, "grens", grens));

        long contactCount = Contactgegeven.update(
                "teVerwijderenOp = :tvop, teVerwijderenOpAutomatisch = true, lastUpdated = :nu " +
                "WHERE teVerwijderenOp IS NULL " +
                "AND COALESCE(lastUsedAt, createdAt) <= :grens",
                Map.of("tvop", teVerwijderenOp, "nu", nu, "grens", grens));

        LOG.info("Retentiescheduler: " + voorkeurCount + " voorkeuren, " + contactCount + " contactgegevens bijgewerkt");
    }
}
