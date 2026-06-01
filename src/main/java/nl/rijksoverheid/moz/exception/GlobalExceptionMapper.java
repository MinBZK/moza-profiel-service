package nl.rijksoverheid.moz.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import nl.rijksoverheid.moz.dto.response.ProblemDetail;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String ERROR_TYPE_BASE = "https://mijnoverheidzakelijk.nl/errors";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        LOG.debugf("Mapping exception: %s", exception.getClass().getName());

        return switch (exception) {
            case ConstraintViolationException cve -> handleConstraintViolationException(cve);
            case ValidationException ve -> handleValidationException(ve);
            case WebApplicationException wae -> handleWebApplicationException(wae);
            default -> handleGenericException(exception);
        };
    }

    private Response handleConstraintViolationException(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    // Strip the method-name prefix (e.g. "requestVerification.request.email" -> "request.email")
                    path = path.replaceFirst("^[^.]+\\.", "");
                    return path + " " + cv.getMessage();
                })
                .collect(Collectors.joining(", "));

        LOG.warnf("Constraint violation: %s", detail);
        return problem(Response.Status.BAD_REQUEST, "validation-failed", detail);
    }

    private Response handleValidationException(ValidationException e) {
        LOG.warnf("Validation failed: %s", e.getMessage());
        return problem(Response.Status.BAD_REQUEST, "validation-failed", e.getMessage());
    }

    private Response handleWebApplicationException(WebApplicationException e) {
        Response response = e.getResponse();
        int status = response.getStatus();
        boolean isServerError = status >= 500;

        String detail = isServerError ? null : e.getMessage();
        LOG.errorf(e, "WebApplicationException with status %d: %s", status, e.getMessage());

        return problem(
                Response.Status.fromStatusCode(status),
                typeSlug(Response.Status.fromStatusCode(status)),
                detail);
    }

    private Response handleGenericException(Throwable e) {
        LOG.error("An unexpected error occurred", e);
        return problem(Response.Status.INTERNAL_SERVER_ERROR, "internal-error", null);
    }

    private Response problem(Response.Status status, String typeSlug, String detail) {
        ProblemDetail body = new ProblemDetail(
                ERROR_TYPE_BASE + "/" + typeSlug,
                status.getReasonPhrase(),
                status.getStatusCode(),
                detail,
                uriInfo != null && uriInfo.getRequestUri() != null
                        ? uriInfo.getRequestUri().toString()
                        : null,
                OffsetDateTime.now(ZoneOffset.UTC));
        return Response.status(status).entity(body).type(PROBLEM_JSON).build();
    }

    private static String typeSlug(Response.Status status) {
        if (status == null) return "error";
        return switch (status) {
            case BAD_REQUEST -> "bad-request";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not-found";
            case CONFLICT -> "conflict";
            case PRECONDITION_FAILED -> "precondition-failed";
            case UNSUPPORTED_MEDIA_TYPE -> "unsupported-media-type";
            case INTERNAL_SERVER_ERROR -> "internal-error";
            case SERVICE_UNAVAILABLE -> "service-unavailable";
            default -> "error";
        };
    }
}
