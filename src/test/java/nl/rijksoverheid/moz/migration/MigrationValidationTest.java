package nl.rijksoverheid.moz.migration;

import jakarta.persistence.Entity;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draait de Flyway-migraties (V1..V4) tegen een echte Postgres en laat Hibernate ze daarna
 * valideren ({@code hbm2ddl.auto=validate}, hetzelfde als productie's
 * {@code schema-management.strategy=validate}) — anders dan de rest van de testsuite, die Flyway
 * overslaat en op H2 drop-and-create draait (zie src/test/resources/application.properties).
 * <p>
 * Bewust GEEN {@code @QuarkusTest}: die start de hele Quarkus-applicatie, en
 * quarkus.datasource.db-kind is build-time-vast — een Postgres-gerichte @QuarkusTest kan daardoor
 * niet veilig in dezelfde Surefire-fork draaien als de H2-gebaseerde suite (geprobeerd, brak de
 * hele suite: 146 van de 198 tests werden overgeslagen). Deze test gebruikt Flyway en Hibernate
 * rechtstreeks via hun eigen Java-API, zonder Quarkus' testframework of CDI erbij te betrekken,
 * en kan daardoor gewoon naast de rest van de suite draaien.
 */
class MigrationValidationTest {

    private static final String ENTITY_PACKAGE = "nl.rijksoverheid.moz.entity";

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void hibernateValideertSchemaTegenMigraties() throws Exception {
        List<Class<?>> entityClasses = discoverEntityClasses();
        // Canary: als dit ooit 0 oplevert, is de scan zelf stuk (bv. classpath-layout gewijzigd),
        // niet dat het domeinmodel leeg is — laat dat duidelijk falen in plaats van een lege,
        // altijd-groene validatie.
        Assertions.assertFalse(entityClasses.isEmpty(), "geen @Entity-klassen gevonden in " + ENTITY_PACKAGE);

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", postgres.getJdbcUrl())
                .applySetting("hibernate.connection.username", postgres.getUsername())
                .applySetting("hibernate.connection.password", postgres.getPassword())
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                // Matches quarkus.hibernate-envers.active=false: V1 doesn't have _aud tables yet
                // (see application.properties), so Envers must stay off here too.
                .applySetting("hibernate.integration.envers.enabled", "false")
                .build();

        try {
            MetadataSources sources = new MetadataSources(registry);
            entityClasses.forEach(sources::addAnnotatedClass);

            // Als de gemapte entiteiten niet overeenkomen met het door Flyway opgebouwde schema
            // (ontbrekende/foutieve kolom, index, constraint), gooit buildSessionFactory() een
            // SchemaManagementException — dat laten we de test laten falen.
            try (SessionFactory sessionFactory = sources.buildMetadata().buildSessionFactory()) {
                Assertions.assertNotNull(sessionFactory);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void v4IndexesZijnDaadwerkelijkPartieel() throws SQLException {
        // Hibernate's validate-mode controleert tabellen/kolommen/types, geen indexen of
        // constraints — dus die partiële WHERE-clausules uit V4 worden hierboven niet geraakt.
        // Rechtstreekse controle tegen pg_indexes om te bevestigen dat het niet per ongeluk
        // gewone (niet-partiële) indexen zijn geworden.
        Set<String> verwachteIndexes = Set.of(
                "uk_contactgegeven_dedup",
                "contactgegeven_default_per_type",
                "idx_voorkeur_retentie",
                "idx_contactgegeven_retentie"
        );

        Map<String, String> definities = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexname, indexdef FROM pg_indexes WHERE indexname IN ("
                             + verwachteIndexes.stream().map(n -> "'" + n + "'").reduce((a, b) -> a + "," + b).orElseThrow()
                             + ")")) {
            while (rs.next()) {
                definities.put(rs.getString("indexname"), rs.getString("indexdef"));
            }
        }

        Assertions.assertEquals(verwachteIndexes, definities.keySet(), "verwachte V4-indexen niet (allemaal) aangetroffen");
        definities.forEach((naam, def) ->
                Assertions.assertTrue(def.toUpperCase().contains("WHERE"), naam + " moet een partiële index zijn: " + def));
    }

    private static List<Class<?>> discoverEntityClasses() throws Exception {
        List<Class<?>> result = new ArrayList<>();
        String packagePath = ENTITY_PACKAGE.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            File directory = new File(resources.nextElement().getFile());
            if (!directory.isDirectory()) {
                continue;
            }
            File[] classFiles = directory.listFiles((dir, name) -> name.endsWith(".class"));
            if (classFiles == null) {
                continue;
            }
            for (File classFile : classFiles) {
                String simpleName = classFile.getName().substring(0, classFile.getName().length() - ".class".length());
                Class<?> candidate = Class.forName(ENTITY_PACKAGE + "." + simpleName);
                if (candidate.isAnnotationPresent(Entity.class)) {
                    result.add(candidate);
                }
            }
        }

        return result;
    }
}
