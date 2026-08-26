package nl.rijksoverheid.moz.migration;

import jakarta.persistence.Entity;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draait de Flyway-migraties in db/migration tegen een echte Postgres en laat Hibernate ze
 * daarna valideren ({@code hbm2ddl.auto=validate}) — anders dan de rest van de testsuite, die op
 * H2 drop-and-create draait. Bewust geen {@code @QuarkusTest}: quarkus.datasource.db-kind is
 * build-time-vast, dus een Postgres-gerichte @QuarkusTest kan niet in dezelfde Surefire-fork
 * draaien als de H2-suite; deze test gebruikt Flyway en Hibernate rechtstreeks, buiten Quarkus om.
 * <p>
 * Vereist een draaiende Docker-daemon. Lokaal wordt de test overgeslagen als die ontbreekt (zie
 * {@code assumeTrue} in {@code startPostgres()}); in CI ({@code CI=true}) niet — dit is de enige
 * test die de migraties daadwerkelijk tegen het schema valideert, dus daar moet ontbrekende
 * Docker de build hard laten falen in plaats van die dekking stil te laten wegvallen.
 */
class MigrationValidationTest {

    private static final String ENTITY_PACKAGE = "nl.rijksoverheid.moz.entity";

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        // GitHub Actions zet CI=true: daar mag ontbrekende Docker niet leiden tot een stille
        // skip, maar moet de build hard falen. Lokaal slaan we de test wel over.
        boolean ci = System.getenv("CI") != null;
        Assumptions.assumeTrue(ci || DockerClientFactory.instance().isDockerAvailable(),
                "Docker niet beschikbaar; migratievalidatie overgeslagen");

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
                // Zelfde als quarkus.hibernate-envers.active=false (zie application.properties):
                // de migraties kennen geen _aud-tabellen, dus Envers moet ook hier uit staan.
                .applySetting("hibernate.integration.envers.enabled", "false")
                .build();

        try {
            MetadataSources sources = new MetadataSources(registry);
            entityClasses.forEach(sources::addAnnotatedClass);

            // Een ontbrekende of verkeerd getypeerde kolom laat buildSessionFactory() falen met
            // een SchemaManagementException. Indexen en constraints vallen buiten validate; zie
            // partieleIndexesZijnDaadwerkelijkPartieel.
            try (SessionFactory sessionFactory = sources.buildMetadata().buildSessionFactory()) {
                Assertions.assertNotNull(sessionFactory);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void partieleIndexesZijnDaadwerkelijkPartieel() throws SQLException {
        // Hibernate's validate-mode controleert tabellen/kolommen/types, geen indexen of
        // constraints — dus die partiële WHERE-clausules uit V4 worden hierboven niet geraakt.
        // Filtert bewust op indisunique = true: de niet-unieke FK-indexen (idx_identificatie_partij,
        // idx_voorkeur_partij) zouden anders deze check onterecht laten falen.
        Map<String, String> definities = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT ic.relname AS indexname, pg_get_indexdef(i.indexrelid) AS indexdef "
                             + "FROM pg_index i "
                             + "JOIN pg_class ic ON ic.oid = i.indexrelid "
                             + "JOIN pg_class tc ON tc.oid = i.indrelid "
                             + "WHERE tc.relname IN ('contactgegeven', 'identificatie', 'voorkeur') "
                             + "AND i.indisunique = true AND i.indisprimary = false")) {
            while (rs.next()) {
                definities.put(rs.getString("indexname"), rs.getString("indexdef"));
            }
        }

        Assertions.assertFalse(definities.isEmpty(),
                "geen unieke, niet-primaire indexen gevonden op contactgegeven/identificatie/voorkeur");
        definities.forEach((naam, def) ->
                Assertions.assertTrue(def.toUpperCase().contains("WHERE"), naam + " moet een partiële index zijn: " + def));
    }

    // idx_voorkeur_retentie en idx_contactgegeven_retentie zijn niet-uniek (dus niet gedekt door
    // de catalogusquery hierboven) maar moeten wél partieel zijn, anders scant de
    // retentiescheduler's kandidaatquery ook al soft deleted rijen — vandaar apart bij naam.
    @Test
    void retentieIndexenZijnPartieel() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexname, indexdef FROM pg_indexes "
                             + "WHERE indexname IN ('idx_voorkeur_retentie', 'idx_contactgegeven_retentie')")) {
            Map<String, String> definities = new HashMap<>();

            while (rs.next()) {
                definities.put(rs.getString("indexname"), rs.getString("indexdef"));
            }

            Assertions.assertEquals(Set.of("idx_voorkeur_retentie", "idx_contactgegeven_retentie"), definities.keySet());
            definities.forEach((naam, def) ->
                    Assertions.assertTrue(def.toUpperCase().contains("WHERE"), naam + " moet een partiële index zijn: " + def));
        }
    }

    @Test
    void ukContactgegevenDedupIsPartieel() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            conn.setAutoCommit(false);
            UUID partijId = insertPartij(conn);

            // Twee actieve rijen met dezelfde (partij_id, type, waarde): botst.
            insertContactgegeven(conn, partijId, "Email", "twee-actief@test.com", false, null);
            Savepoint sp = conn.setSavepoint();
            SQLException violatie = Assertions.assertThrows(SQLException.class,
                    () -> insertContactgegeven(conn, partijId, "Email", "twee-actief@test.com", false, null),
                    "twee actieve rijen met dezelfde (partij_id, type, waarde) horen te botsen");
            Assertions.assertEquals("23505", violatie.getSQLState());
            conn.rollback(sp);

            // Eén soft deleted + één actief op dezelfde sleutel: geaccepteerd.
            insertContactgegeven(conn, partijId, "Email", "een-verwijderd@test.com", false, Instant.now());
            Assertions.assertDoesNotThrow(() ->
                    insertContactgegeven(conn, partijId, "Email", "een-verwijderd@test.com", false, null));

            // Twee soft deleted + één actief: nog steeds geaccepteerd — bewijst op DB-niveau wat
            // PartijServiceTest.addContactgegeven_MeerdereCyclusVanToevoegenEnVerwijderen_LaatMeerdereVerwijderdeRijenToe
            // alleen op servicelaag-niveau claimt.
            insertContactgegeven(conn, partijId, "Email", "twee-verwijderd@test.com", false, Instant.now());
            insertContactgegeven(conn, partijId, "Email", "twee-verwijderd@test.com", false, Instant.now());
            Assertions.assertDoesNotThrow(() ->
                    insertContactgegeven(conn, partijId, "Email", "twee-verwijderd@test.com", false, null));

            conn.rollback();
        }
    }

    @Test
    void ukIdentificatieIsPartieel() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            conn.setAutoCommit(false);

            // Twee actieve identificaties met dezelfde (type, nummer) op verschillende partijen: botst.
            UUID partijA = insertPartij(conn);
            insertIdentificatie(conn, partijA, "BSN", "900000001", null);
            UUID partijB = insertPartij(conn);
            Savepoint sp = conn.setSavepoint();
            SQLException violatie = Assertions.assertThrows(SQLException.class,
                    () -> insertIdentificatie(conn, partijB, "BSN", "900000001", null),
                    "twee actieve identificaties met dezelfde (type, nummer) horen te botsen");
            Assertions.assertEquals("23505", violatie.getSQLState());
            conn.rollback(sp);

            // Eén soft deleted + één actief op dezelfde sleutel (nieuwe partij, nieuw UUID): geaccepteerd.
            UUID partijC = insertPartij(conn);
            insertIdentificatie(conn, partijC, "BSN", "900000002", Instant.now());
            UUID partijD = insertPartij(conn);
            Assertions.assertDoesNotThrow(() -> insertIdentificatie(conn, partijD, "BSN", "900000002", null));

            // Twee soft deleted + één actief: nog steeds geaccepteerd.
            UUID partijE = insertPartij(conn);
            insertIdentificatie(conn, partijE, "BSN", "900000003", Instant.now());
            UUID partijF = insertPartij(conn);
            insertIdentificatie(conn, partijF, "BSN", "900000003", Instant.now());
            UUID partijG = insertPartij(conn);
            Assertions.assertDoesNotThrow(() -> insertIdentificatie(conn, partijG, "BSN", "900000003", null));

            conn.rollback();
        }
    }

    @Test
    void ukIdentificatiePerPartijIsPartieel() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            conn.setAutoCommit(false);

            // Twee actieve identificaties van hetzelfde type op dezelfde partij (verschillend
            // nummer, dus uk_identificatie zit niet in de weg): botst op uk_identificatie_per_partij.
            UUID partijA = insertPartij(conn);
            insertIdentificatie(conn, partijA, "BSN", "900000010", null);
            Savepoint sp = conn.setSavepoint();
            SQLException violatie = Assertions.assertThrows(SQLException.class,
                    () -> insertIdentificatie(conn, partijA, "BSN", "900000011", null),
                    "twee actieve identificaties van hetzelfde type op dezelfde partij horen te botsen");
            Assertions.assertEquals("23505", violatie.getSQLState());
            conn.rollback(sp);

            // Eén soft deleted + één actief op dezelfde partij + type: geaccepteerd.
            UUID partijB = insertPartij(conn);
            insertIdentificatie(conn, partijB, "BSN", "900000012", Instant.now());
            Assertions.assertDoesNotThrow(() -> insertIdentificatie(conn, partijB, "BSN", "900000013", null));

            conn.rollback();
        }
    }

    @Test
    void contactgegevenDefaultPerTypeIsPartieel() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            conn.setAutoCommit(false);
            UUID partijId = insertPartij(conn);

            // Twee actieve is_default-rijen van hetzelfde type: botst.
            insertContactgegeven(conn, partijId, "Email", "default-a@test.com", true, null);
            Savepoint sp = conn.setSavepoint();
            SQLException violatie = Assertions.assertThrows(SQLException.class,
                    () -> insertContactgegeven(conn, partijId, "Email", "default-b@test.com", true, null),
                    "twee actieve is_default-rijen van hetzelfde type horen te botsen");
            Assertions.assertEquals("23505", violatie.getSQLState());
            conn.rollback(sp);

            // Eén soft deleted is_default-rij + één actieve is_default-rij van hetzelfde type:
            // geaccepteerd — dit is precies het scenario waar PartijService.demoteCurrentDefault
            // over redeneert (verwijderContactgegeven laat isDefault op de verwijderde rij staan).
            insertContactgegeven(conn, partijId, "Telefoonnummer", "default-c@test.com", true, Instant.now());
            Assertions.assertDoesNotThrow(() ->
                    insertContactgegeven(conn, partijId, "Telefoonnummer", "default-d@test.com", true, null));

            conn.rollback();
        }
    }

    private static UUID insertPartij(Connection conn) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO partij (id) VALUES (?)")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertIdentificatie(Connection conn, UUID partijId, String type, String nummer, Instant verwijderdOp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO identificatie (id, partij_id, identificatie_type, identificatie_nummer, verwijderd_op) VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, partijId);
            ps.setString(3, type);
            ps.setString(4, nummer);
            setNullableTimestamp(ps, 5, verwijderdOp);
            ps.executeUpdate();
        }
    }

    private static void insertContactgegeven(Connection conn, UUID partijId, String type, String waarde, boolean isDefault, Instant verwijderdOp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO contactgegeven (id, partij_id, type, waarde, is_default, verwijderd_op) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, partijId);
            ps.setString(3, type);
            ps.setString(4, waarde);
            ps.setBoolean(5, isDefault);
            setNullableTimestamp(ps, 6, verwijderdOp);
            ps.executeUpdate();
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
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
