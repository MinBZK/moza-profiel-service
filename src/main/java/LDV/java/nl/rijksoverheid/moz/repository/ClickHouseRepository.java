package LDV.java.nl.rijksoverheid.moz.repository;

import LDV.java.nl.rijksoverheid.moz.config.ConfigurationLoader;
import com.clickhouse.client.api.Client;
import com.clickhouse.data.ClickHouseFormat;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ClickHouseRepository {
    private final Client client;

    Configuration config;
    public ClickHouseRepository() throws ConfigurationException {
        Configurations configs = new Configurations();
        this.config = configs.properties(new File("application.properties"));

        this.client = new Client.Builder()
                .addEndpoint(ConfigurationLoader.getString("logboekdataverwerking.clickhouse.endpoint"))
                .setUsername(ConfigurationLoader.getString("logboekdataverwerking.clickhouse.username"))
                .setPassword(ConfigurationLoader.getString("logboekdataverwerking.clickhouse.password"))
                .setDefaultDatabase(ConfigurationLoader.getString("logboekdataverwerking.clickhouse.database"))
                .build();

    }

    public void ensureSchema() throws ConfigurationException {
        String table = ConfigurationLoader.getString("logboekdataverwerking.clickhouse.table");
        try {
            // Schema matching SpanData structure (camelCase)
            client.query("CREATE TABLE IF NOT EXISTS " + table + " (\n" +
                            "    traceId String,\n" +
                            "    spanId String,\n" +
                            "    status String,\n" +
                            "    name String,\n" +
                            "    startTime Int64,\n" +
                            "    endTime Int64,\n" +
                            "    parentSpanId String,\n" +
                            "    attributes Map(String, String),\n" +
                            "    resource Map(String, String)\n" +
                            ")\n" +
                            "ENGINE = MergeTree()\n" +
                            "ORDER BY (traceId, spanId);")
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure ClickHouse schema", e);
        }
    }

    public void insertJsonEachRow(String table, String jsonEachRowPayload) {
        try {
            InputStream data = new ByteArrayInputStream(jsonEachRowPayload.getBytes(StandardCharsets.UTF_8));
            client.insert(table, data, ClickHouseFormat.JSONEachRow).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert into ClickHouse", e);
        }
    }

}
