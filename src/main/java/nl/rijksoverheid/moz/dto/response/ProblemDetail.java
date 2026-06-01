package nl.rijksoverheid.moz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * RFC 9457 (Problem Details for HTTP APIs) response body.
 *
 * Standard members: type, title, status, detail, instance.
 * `timestamp` is a custom extension for operational debugging, allowed by
 * RFC 9457 section 3.2.
 *
 * Serialised as `application/problem+json`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
    String type,
    String title,
    Integer status,
    String detail,
    String instance,
    OffsetDateTime timestamp
) {
}
