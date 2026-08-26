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
 * Pseudonimiseert een identificatienummer (BSN/KVK/RSIN) tot het subject-id dat het
 * Logboek Dataverwerkingen gebruikt.
 */
// Keyed HMAC en geen kale digest: een BSN heeft maar ~10^9 mogelijke waarden, dus een
// kale hash triviaal te brute-forcen is. Een salt per aanroep kan niet, want het
// pseudoniem moet per subject stabiel blijven.
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
