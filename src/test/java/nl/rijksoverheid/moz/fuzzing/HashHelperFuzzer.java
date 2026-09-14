package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import nl.rijksoverheid.moz.helper.HashHelper;

import java.util.Optional;

/**
 * Standalone fuzz target for ClusterFuzzLite.
 * Tests the keyed HMAC-SHA-256 hashing helper with arbitrary string input.
 */
public class HashHelperFuzzer {

    private static final HashHelper hashHelper = new HashHelper(Optional.of("fuzz-pepper"));

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String input = data.consumeRemainingAsString();

        String result = hashHelper.hashIdentifier(input);

        // Verify determinism: same input must always produce same output
        String result2 = hashHelper.hashIdentifier(input);
        if (result != null && !result.equals(result2)) {
            throw new AssertionError("Hash is not deterministic!");
        }
    }
}
