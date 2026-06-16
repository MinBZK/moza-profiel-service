package nl.rijksoverheid.moz.exception;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Thrown for authorization failures (e.g. missing write access, invalid JWT scope).
 * Not yet used; wired into DomainExceptionMapper for future use.
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(@NotNull String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public String getTitle() {
        return "Forbidden";
    }
}
