package nl.rijksoverheid.moz.exception;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Thrown for authorization failures (e.g. missing write access, invalid JWT scope).
 * Wordt gegooid door PartijService.requireDienstverlenerAuthorized en door
 * DomainExceptionMapper vertaald naar een 403 met problem-body.
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(@NotNull String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public String getTitle() {
        return "Forbidden";
    }
}
