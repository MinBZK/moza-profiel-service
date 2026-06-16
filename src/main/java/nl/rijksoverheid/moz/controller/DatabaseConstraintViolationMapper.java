package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import nl.rijksoverheid.moz.helper.Problems;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

/**
 * Maps Hibernate's DB-level constraint violations (UNIQUE, partial unique indexes, foreign keys)
 * to HTTP 409. Application pre-checks try to avoid these, but concurrent writes can still race
 * past the pre-check and land here at flush time. quarkus-http-problem's default mapper would
 * treat this Hibernate exception as a 500, so this mapper keeps the 409 semantics and emits the
 * standard RFC 9457 application/problem+json body via {@link Problems#problemResponse}.
 */
@Provider
public class DatabaseConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(DatabaseConstraintViolationMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String constraintName = exception.getConstraintName();
        LOG.warnf("Database constraint violation: %s", constraintName != null ? constraintName : "<unknown>");
        return Problems.problemResponse(
                Response.Status.CONFLICT,
                Response.Status.CONFLICT.getReasonPhrase(),
                "Resource bestaat al of conflicteert met een unique constraint");
    }
}
