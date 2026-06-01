package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import nl.rijksoverheid.moz.dto.response.ProblemDetail;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Maps Hibernate's DB-level constraint violations (UNIQUE, partial unique indexes, foreign keys)
 * to HTTP 409. Application pre-checks try to avoid these, but concurrent writes can still race
 * past the pre-check and land here at flush time. Mapping to 409 keeps the error semantically
 * meaningful instead of bubbling as a 500. Body follows RFC 9457 (application/problem+json).
 */
@Provider
public class DatabaseConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(DatabaseConstraintViolationMapper.class);
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String ERROR_TYPE = "https://mijnoverheidzakelijk.nl/errors/conflict";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String constraintName = exception.getConstraintName();
        LOG.warnf("Database constraint violation: %s", constraintName != null ? constraintName : "<unknown>");

        ProblemDetail body = new ProblemDetail(
                ERROR_TYPE,
                Response.Status.CONFLICT.getReasonPhrase(),
                Response.Status.CONFLICT.getStatusCode(),
                "Resource bestaat al of conflicteert met een unique constraint",
                uriInfo != null && uriInfo.getRequestUri() != null
                        ? uriInfo.getRequestUri().toString()
                        : null,
                OffsetDateTime.now(ZoneOffset.UTC));

        return Response.status(Response.Status.CONFLICT)
                .entity(body)
                .type(PROBLEM_JSON)
                .build();
    }
}
