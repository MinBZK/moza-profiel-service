package nl.rijksoverheid.moz.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import nl.rijksoverheid.moz.dto.response.ErrorResponse;
import org.jboss.logging.Logger;

import java.util.stream.Collectors;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);
    private static final String PROBLEM_JSON = "application/problem+json";

    @Override
    public Response toResponse(Throwable exception) {
        LOG.debugf("Mapping exception: %s", exception.getClass().getName());

        return switch (exception) {
            case ConstraintViolationException constraintViolationException ->
                    handleConstraintViolationException(constraintViolationException);
            case ValidationException validationException -> handleValidationException(validationException);
            case WebApplicationException webAppException -> handleWebApplicationException(webAppException);
            default -> handleGenericException(exception);
        };

    }

    private Response handleConstraintViolationException(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    // Remove the method name prefix (e.g., "requestVerification.request.email" -> "request.email")
                    path = path.replaceFirst("^[^.]+\\.", "");
                    return path + " " + cv.getMessage();
                })
                .collect(Collectors.joining(", "));

        LOG.warnf("Constraint violation: %s", detail);

        ErrorResponse errorResponse = new ErrorResponse("Bad Request", 400, detail);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .type(PROBLEM_JSON)
                .build();
    }

    private Response handleValidationException(ValidationException e) {
        LOG.warnf("Validation failed: %s", e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse("Bad Request", 400, e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .type(PROBLEM_JSON)
                .build();
    }

    private Response handleWebApplicationException(WebApplicationException e) {
        Response response = e.getResponse();
        int status = response.getStatus();
        boolean isServerError = status >= 500;

        String title = response.getStatusInfo().getReasonPhrase();
        String detail = isServerError ? null : e.getMessage();

        LOG.errorf(e, "WebApplicationException occurred with status %d: %s", status, e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(title, status, detail);
        return Response.status(status)
                .entity(errorResponse)
                .type(PROBLEM_JSON)
                .build();
    }

    private Response handleGenericException(Throwable e) {
        LOG.error("An unexpected error occurred", e);

        ErrorResponse errorResponse = new ErrorResponse("Internal Server Error", 500);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorResponse)
                .type(PROBLEM_JSON)
                .build();
    }
}
