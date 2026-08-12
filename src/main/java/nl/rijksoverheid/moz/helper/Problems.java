package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * De twee vormen die deze helper aanbiedt. Problemen worden ook buiten deze klasse opgebouwd,
 * rechtstreeks met {@code HttpProblem.valueOf} of {@code HttpProblem.builder()}.
 *
 * <p>Er stonden hier meer helpers. {@code missingBody}, {@code badRequest} en
 * {@code serviceUnavailable} hadden geen aanroeper meer in productiecode; alleen hun unittests
 * hielden ze in leven, dus de dekkingsdrempel merkte er niets van. Bij de 400 was dat geen
 * bewuste keuze: {@code RequireBodyReaderInterceptor} en {@code EmailVerificatieController}
 * gebruiken {@code HttpProblem.valueOf(status, tekst)}, dat exact hetzelfde oplevert als
 * {@code missingBody} deed — reason phrase als titel, de tekst als detail. Die helpers waren
 * simpelweg nooit aangesloten. Alleen bij de 503 kon de helper het niet: daar zet
 * {@code EmailVerificatieController} een {@code Retry-After}-header en geen titel.
 *
 * <p>Wie hier een helper toevoegt: controleer of hij ook echt vanuit productiecode wordt
 * aangeroepen.
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
