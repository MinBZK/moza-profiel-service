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

    /** Titel van elke melding dat een dienstverlener niet bestaat, op welke route ook. */
    public static final String DIENSTVERLENER_NIET_GEVONDEN = "Dienstverlener niet gevonden";

    private final Kind kind;
    private final String title;

    public BusinessException(@NotNull Kind kind, @NotNull String message) {
        this(kind, Objects.requireNonNull(kind, "kind").getReasonPhrase(), message);
    }

    private BusinessException(Kind kind, String title, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = title;
    }

    /** Zet een eigen titel in de problem-body in plaats van de reason phrase van {@code kind}. */
    public static BusinessException withTitle(@NotNull Kind kind, @NotNull String title,
            @NotNull String message) {
        if (Objects.requireNonNull(title, "title").isBlank()) {
            throw new IllegalArgumentException("title mag niet blanco zijn");
        }

        return new BusinessException(kind, title, message);
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }
}
