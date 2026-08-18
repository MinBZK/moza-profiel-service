package nl.rijksoverheid.moz.exception;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Wordt gegooid door PartijService.requireDienstverlenerAuthorized wanneer een dienstverlener
 * geen scope heeft op het gegeven dat hij probeert te wijzigen, en door DomainExceptionMapper
 * vertaald naar een 403 met problem-body.
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(@NotNull String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public String getTitle() {
        return "Forbidden";
    }
}
