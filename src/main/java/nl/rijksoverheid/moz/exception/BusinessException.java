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
    private final String title;

    public BusinessException(@NotNull Kind kind, @NotNull String message) {
        this(kind, null, message);
    }

    private BusinessException(Kind kind, String title, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = title;
    }

    /**
     * Voor een conditie die elders al onder een specifiekere naam wordt gemeld; zonder eigen
     * titel voert de problem-body de kale reason phrase.
     */
    public static BusinessException withTitle(@NotNull Kind kind, @NotNull String title,
            @NotNull String message) {
        return new BusinessException(kind, Objects.requireNonNull(title, "title"), message);
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title != null ? title : kind.getReasonPhrase();
    }
}
