package nl.rijksoverheid.moz.exception;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class TechnicalException extends RuntimeException {

    public TechnicalException(@NotNull String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
    }

    public String getTitle() {
        return "Internal Server Error";
    }
}
