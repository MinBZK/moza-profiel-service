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
    private final String detail;

    public BusinessException(@NotNull Kind kind, @NotNull String detail) {
        super(Objects.requireNonNull(detail, "detail"));
        this.kind = kind;
        this.detail = detail;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return kind.getReasonPhrase();
    }

    public String getDetail() {
        return detail;
    }
}
