package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

/**
 * Maps Hibernate's DB-level constraint violations (UNIQUE, partial unique indexes, foreign keys)
 * to HTTP 409. Application pre-checks try to avoid these, but concurrent writes can still race
 * past the pre-check and land here at flush time. Mapping to 409 keeps the error semantically
 * meaningful instead of bubbling as a 500.
 */
@Provider
public class DatabaseConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(DatabaseConstraintViolationMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String constraintName = exception.getConstraintName();
        LOG.warnf("Database constraint violation: %s", constraintName != null ? constraintName : "<unknown>");
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.TEXT_PLAIN)
                .entity("Resource bestaat al of conflicteert met een unique constraint")
                .build();
    }
}
