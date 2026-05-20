package nl.rijksoverheid.moz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String type,
    String title,
    Integer status,
    String detail,
    String instance,
    OffsetDateTime timestamp
) {
    public ErrorResponse(String title, Integer status, String detail) {
        this("about:blank", title, status, detail, null, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public ErrorResponse(String title, Integer status) {
        this(title, status, null);
    }
}
