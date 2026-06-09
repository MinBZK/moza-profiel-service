package nl.rijksoverheid.moz.exception;

import java.util.Objects;

public class TechnicalException extends RuntimeException {

    private final String detail;

    public TechnicalException(String detail) {
        super(Objects.requireNonNull(detail, "detail"));
        this.detail = detail;
    }

    public TechnicalException(String detail, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail"), cause);
        this.detail = detail;
    }

    public String getTitle() {
        return "Internal Server Error";
    }

    public String getDetail() {
        return detail;
    }
}
