package nl.rijksoverheid.moz.exception;

import java.util.Objects;

/**
 * Thrown for authorization failures (e.g. missing write access, invalid JWT scope).
 * Not yet used; wired into DomainExceptionMapper for future use.
 */
public class AuthorizationException extends RuntimeException {

    private final String detail;

    public AuthorizationException(String detail) {
        super(Objects.requireNonNull(detail, "detail"));
        this.detail = detail;
    }

    public String getTitle() {
        return "Forbidden";
    }

    public String getDetail() {
        return detail;
    }
}
