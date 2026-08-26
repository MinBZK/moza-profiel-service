package nl.rijksoverheid.moz.helper;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * Pseudonymises an identifier (BSN/KVK/RSIN) into the subject id used in the
 * Logboek Dataverwerkingen.
 */
// Keyed HMAC, not a plain digest: a BSN has only ~10^9 possible values, so a bare
// hash is trivially brute-forced back. A per-call salt is not an option, the
// pseudonym has to stay stable per subject.
@Startup
@ApplicationScoped
public class HashHelper {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec pepperKey;

    public HashHelper(@ConfigProperty(name = "hash.pepper") Optional<String> pepper) {
        String pepperValue = pepper.filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("hash.pepper is not configured"));
        this.pepperKey = new SecretKeySpec(pepperValue.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String hashIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            byte[] hash = mac.doFinal(identifier.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HmacSHA256 could not be initialised", e);
        }
    }
}
