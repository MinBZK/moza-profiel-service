package LDV.java.nl.rijksoverheid.moz.exporter;

import LDV.java.nl.rijksoverheid.moz.config.ConfigurationLoader;
import LDV.java.nl.rijksoverheid.moz.repository.ClickHouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClickHouseSpanExporter implements SpanExporter {

    private static final Logger LOGGER = Logger.getLogger(ClickHouseSpanExporter.class.getName());

    private final ClickHouseRepository repository;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final boolean enabled;

    public ClickHouseSpanExporter() throws ConfigurationException {
        this.enabled = ConfigurationLoader.getValueByKey("logboekdataverwerking.enabled", Boolean.class);
        if (enabled) {
            this.repository = new ClickHouseRepository();
            this.tableName = ConfigurationLoader.getValueByKey("logboekdataverwerking.clickhouse.table", String.class);
            this.repository.ensureSchema();
        } else {
            this.repository = null;
            this.tableName = null;
        }
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {

        if (!enabled) {
            return CompletableResultCode.ofSuccess();
        }

        StringBuilder payload = new StringBuilder();

        for (SpanData span : spans) {
            Map<String, Object> spanMap = mapSpanToJson(span);

            try {
                payload.append(objectMapper.writeValueAsString(spanMap)).append("\n");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to serialize span data", e);
                return CompletableResultCode.ofFailure();
            }
        }

        if (!payload.isEmpty()) {
            try {
                repository.insertJsonEachRow(tableName, payload.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to insert spans into ClickHouse", e);
                return CompletableResultCode.ofFailure();
            }
        }

        return CompletableResultCode.ofSuccess();
    }

    /**
     * Maps SpanData to a JSON-compatible Map structure.
     */
    private Map<String, Object> mapSpanToJson(SpanData span) {
        Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put("traceId", span.getTraceId());
        jsonMap.put("spanId", span.getSpanId());
        jsonMap.put("status", span.getStatus().getStatusCode().name());
        jsonMap.put("name", span.getName());
        jsonMap.put("startTime", TimeUnit.NANOSECONDS.toMillis(span.getStartEpochNanos()));
        jsonMap.put("endTime", TimeUnit.NANOSECONDS.toMillis(span.getEndEpochNanos()));
        jsonMap.put("parentSpanId", span.getParentSpanId());

        Map<String, String> attributes = new HashMap<>();
        span.getAttributes().forEach((key, value) ->
                attributes.put(key.getKey(), String.valueOf(value)));
        jsonMap.put("attributes", attributes);

        Map<String, String> resource = new HashMap<>();
        span.getResource().getAttributes().forEach((key, value) ->
                resource.put(key.getKey(), String.valueOf(value)));
        jsonMap.put("resource", resource);

        return jsonMap;
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
