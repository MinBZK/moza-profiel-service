package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public final class Problems {

    private Problems() {}

    public static HttpProblem notFound(String title, String detail) {
        return HttpProblem.builder()
                .withStatus(Response.Status.NOT_FOUND)
                .withTitle(title)
                .withDetail(detail)
                .build();
    }

    public static HttpProblem missingBody() {
        return badRequest(Response.Status.BAD_REQUEST.getReasonPhrase(), "Request body mag niet leeg zijn");
    }

    public static HttpProblem badRequest(String title, String detail) {
        return HttpProblem.builder()
                .withStatus(Response.Status.BAD_REQUEST)
                .withTitle(title)
                .withDetail(detail)
                .build();
    }

    public static HttpProblem serviceUnavailable(String title, String detail) {
        return HttpProblem.builder()
                .withStatus(Response.Status.SERVICE_UNAVAILABLE)
                .withTitle(title)
                .withDetail(detail)
                .build();
    }

    public static Response problemResponse(Response.Status status, String title, String detail) {
        return Response.status(status)
                .type("application/problem+json")
                .entity(Map.of(
                        "type", "about:blank",
                        "title", title,
                        "status", status.getStatusCode(),
                        "detail", detail
                ))
                .build();
    }
}
