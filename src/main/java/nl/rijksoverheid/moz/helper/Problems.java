package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * De twee vormen waarin deze applicatie een RFC 9457-probleem opbouwt.
 *
 * <p>Er stonden er meer. {@code missingBody}, {@code badRequest} en {@code serviceUnavailable}
 * hadden geen aanroeper meer in productiecode: de plekken die ze zouden gebruiken bouwen hun
 * probleem inline, omdat ze net iets anders nodig hebben dan de helper kon —
 * {@code RequireBodyReaderInterceptor} wil de tekst als titel in plaats van als detail, en
 * {@code EmailVerificatieController} zet er een {@code Retry-After}-header bij. Alleen hun
 * unittests hielden ze in leven, dus de dekkingsdrempel merkte er niets van. Wie hier een
 * helper toevoegt: controleer of hij ook echt vanuit productiecode wordt aangeroepen.
 */
public final class Problems {

    private Problems() {}

    public static HttpProblem notFound(String title, String detail) {
        return HttpProblem.builder()
                .withStatus(Response.Status.NOT_FOUND)
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
