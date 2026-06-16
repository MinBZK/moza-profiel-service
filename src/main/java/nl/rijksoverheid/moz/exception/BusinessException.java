package nl.rijksoverheid.moz.exception;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class BusinessException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST("Bad Request"),
        NOT_FOUND("Not Found"),
        CONFLICT("Conflict");

        private final String reasonPhrase;

        Kind(String reasonPhrase) {
            this.reasonPhrase = reasonPhrase;
        }

        public String getReasonPhrase() {
            return reasonPhrase;
        }
    }

    private final Kind kind;

    public BusinessException(@NotNull Kind kind, @NotNull String message) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return kind.getReasonPhrase();
    }
}
