package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;

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
        return HttpProblem.builder()
                .withStatus(Response.Status.BAD_REQUEST)
                .withTitle(Response.Status.BAD_REQUEST.getReasonPhrase())
                .withDetail("Request body mag niet leeg zijn")
                .build();
    }
}
