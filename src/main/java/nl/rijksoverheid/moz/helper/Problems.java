package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * De twee vormen die deze helper aanbiedt. Elders in de code worden problemen ook rechtstreeks
 * met {@code HttpProblem.valueOf} of {@code builder()} opgebouwd. Wie hier een helper toevoegt:
 * controleer of hij ook echt vanuit productiecode wordt aangeroepen — drie voorgangers waren
 * alleen door hun eigen unittest in leven gehouden.
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
