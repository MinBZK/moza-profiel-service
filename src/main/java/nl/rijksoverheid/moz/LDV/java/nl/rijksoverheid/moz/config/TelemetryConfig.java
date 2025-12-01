package LDV.java.nl.rijksoverheid.moz.config;

import LDV.java.nl.rijksoverheid.moz.exporter.ClickHouseSpanExporter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.apache.commons.configuration2.ex.ConfigurationException;

public final class TelemetryConfig {
    private static OpenTelemetry instance;
    private static boolean initialized = false;
    
    private TelemetryConfig() {
    }

    public static synchronized OpenTelemetry initOpenTelemetry(String serviceName) throws ConfigurationException {
        if (initialized) {
            return instance;
        }

        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), serviceName
        )));

        ClickHouseSpanExporter exporter = new ClickHouseSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();

        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
        Runtime.getRuntime().addShutdownHook(new Thread(openTelemetrySdk::close));
        
        instance = openTelemetrySdk;
        initialized = true;
        return openTelemetrySdk;
    }
}
