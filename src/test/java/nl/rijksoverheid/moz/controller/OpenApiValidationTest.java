package nl.rijksoverheid.moz.controller;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Base class that provides an {@link OpenApiValidationFilter} to subclasses.
 * The filter is loaded once per test JVM run (shared static field) and validates
 * every request/response against the live OpenAPI spec served by the running application.
 *
 * Known suppressions:
 * - validation.request.parameter.schema.invalidJson: false positive caused by the OpenAPI 3.1
 *   path-parameter content-serialisation format. The Atlassian validator tries to JSON-parse
 *   plain string/UUID path params and fails. All path params in this API are strings or UUIDs —
 *   none have numeric or enum schemas — so no real violation can be masked. Revisit if a
 *   constrained (non-string) path parameter is ever added.
 *
 * validation.response.body.missing used to be suppressed here as well. That suppression was
 * written when SmallRye still derived the document from the class-level @Produces annotation and
 * generated an implicit content entry for responses that carry no body. Since the contract became
 * the source of truth (mp.openapi.scan.disable=true) nothing derives anything: the six 200s that
 * declared a body they never send were plain contract errors and have been corrected in
 * META-INF/openapi.yaml. The check is enabled again, so a handler that stops returning a
 * documented body now fails the tests that use this filter.
 */
abstract class OpenApiValidationTest {

    protected static OpenApiValidationFilter validationFilter;

    @BeforeEach
    void setupValidationFilter() throws Exception {
        if (validationFilter != null) return;
        String openApiPath = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.smallrye-openapi.path", String.class)
                .orElse("/q/openapi");
        URL url = new URL("http://localhost:" + RestAssured.port + openApiPath);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        conn.setRequestProperty("Accept", "application/json");
        String specJson = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        var validator = OpenApiInteractionValidator.createFor(specJson)
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request.parameter.schema.invalidJson", ValidationReport.Level.IGNORE)
                        .build())
                .build();
        validationFilter = new OpenApiValidationFilter(validator);
    }
}
