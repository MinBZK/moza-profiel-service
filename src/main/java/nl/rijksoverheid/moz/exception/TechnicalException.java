package nl.rijksoverheid.moz.exception;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class TechnicalException extends RuntimeException {

    public TechnicalException(@NotNull String detail) {
        super(Objects.requireNonNull(detail, "detail"));
    }

    public TechnicalException(@NotNull String detail, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail"), cause);
    }

    public String getTitle() {
        return "Internal Server Error";
    }

    public String getDetail() {
        return getMessage();
    }
}
