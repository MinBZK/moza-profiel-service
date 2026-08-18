package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.rijksoverheid.moz.api.generated.model.DienstRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenUpdateRequest;
import nl.rijksoverheid.moz.api.generated.model.DienstverlenerRequest;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurRequest;
import nl.rijksoverheid.moz.api.generated.model.VoorkeurUpdateRequest;

/**
 * Standalone fuzz target for ClusterFuzzLite.
 * Tests JSON deserialization of all request DTOs with arbitrary input.
 */
public class JsonDeserializationFuzzer {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Class<?>[] REQUEST_TYPES = {
        ContactgegevenRequest.class,
        ContactgegevenUpdateRequest.class,
        VoorkeurRequest.class,
        VoorkeurUpdateRequest.class,
        DienstverlenerRequest.class,
        DienstRequest.class,
        EmailVerificatieRequest.class,
    };

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        Class<?> targetType = data.pickValue(REQUEST_TYPES);
        String json = data.consumeRemainingAsString();
        try {
            mapper.readValue(json, targetType);
        } catch (Exception e) {
            // Expected for invalid JSON or incompatible types
        }
    }
}
