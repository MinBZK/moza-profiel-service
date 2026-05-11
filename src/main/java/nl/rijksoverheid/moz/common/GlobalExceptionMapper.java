package nl.rijksoverheid.moz.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import nl.rijksoverheid.moz.dto.response.ProblemDetail;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);
    private static final String TYPE_PREFIX = "https://mijnoverheidzakelijk.nl/errors/";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException wae) {
            return buildResponse(
                    wae.getResponse().getStatus(),
                    Response.Status.fromStatusCode(wae.getResponse().getStatus()),
                    wae.getMessage(),
                    slug(wae)
            );
        }

        if (exception instanceof ConstraintViolationException cve) {
            String detail = cve.getConstraintViolations().stream()
                    .map(this::formatViolation)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Validation failed");
            return buildResponse(400, Response.Status.BAD_REQUEST, detail, "validation-failed");
        }

        LOG.error("Onverwachte fout bij verwerken request", exception);
        return buildResponse(500, Response.Status.INTERNAL_SERVER_ERROR,
                "Er is een onverwachte fout opgetreden", "internal-server-error");
    }

    private Response buildResponse(int status, Response.Status statusEnum, String detail, String typeSlug) {
        String title = statusEnum != null ? statusEnum.getReasonPhrase() : "Error";
        String instance = uriInfo != null && uriInfo.getRequestUri() != null
                ? uriInfo.getRequestUri().getPath()
                : null;

        ProblemDetail body = new ProblemDetail(
                TYPE_PREFIX + typeSlug,
                title,
                status,
                detail,
                instance
        );

        return Response.status(status)
                .type("application/problem+json")
                .entity(body)
                .build();
    }

    private String formatViolation(ConstraintViolation<?> v) {
        return v.getPropertyPath() + ": " + v.getMessage();
    }

    private String slug(WebApplicationException wae) {
        Response.Status s = Response.Status.fromStatusCode(wae.getResponse().getStatus());
        if (s == null) {
            return "error";
        }
        return switch (s) {
            case NOT_FOUND -> "not-found";
            case BAD_REQUEST -> "bad-request";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case CONFLICT -> "conflict";
            case UNSUPPORTED_MEDIA_TYPE -> "unsupported-media-type";
            default -> "error";
        };
    }
}
