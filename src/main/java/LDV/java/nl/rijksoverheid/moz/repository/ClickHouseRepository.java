package LDV.java.nl.rijksoverheid.moz.repository;

import LDV.java.nl.rijksoverheid.moz.config.ConfigurationLoader;
import com.clickhouse.client.api.Client;
import com.clickhouse.data.ClickHouseFormat;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ClickHouseRepository {
    private final Client client;

    public ClickHouseRepository() throws ConfigurationException {
        this.client = new Client.Builder()
                .addEndpoint(ConfigurationLoader.getValueByKey("logboekdataverwerking.clickhouse.endpoint", String.class))
                .setUsername(ConfigurationLoader.getValueByKey("logboekdataverwerking.clickhouse.username", String.class))
                .setPassword(ConfigurationLoader.getValueByKey("logboekdataverwerking.clickhouse.password", String.class))
                .setDefaultDatabase(ConfigurationLoader.getValueByKey("logboekdataverwerking.clickhouse.database", String.class))
                .build();

    }

    public void ensureSchema() throws ConfigurationException {
        String table = ConfigurationLoader.getValueByKey("logboekdataverwerking.clickhouse.table", String.class);
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
