package nl.rijksoverheid.moz;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Starts a real PostgreSQL as a child process of the test JVM, without Docker,
 * so tests exercise the actual Flyway migrations and PostgreSQL-only features
 * (partial unique indexes, pgcrypto) that an entity-generated schema lacks.
 */
public class EmbeddedPostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private EmbeddedPostgres postgres;

    @Override
    public Map<String, String> start() {
        try {
            postgres = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Embedded PostgreSQL failed to start", e);
        }

        return Map.of(
                "quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:" + postgres.getPort() + "/postgres",
                "quarkus.datasource.username", "postgres",
                "quarkus.datasource.password", "postgres");
    }

    @Override
    public void stop() {
        if (postgres == null) {
            return;
        }

        try {
            postgres.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
