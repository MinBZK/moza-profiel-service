package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.InstanceUtils;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

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

    /**
     * Levert dezelfde velden als een geworpen {@code HttpProblem}, dat zijn {@code instance} van
     * de extensie krijgt. Een exceptionmapper geeft zijn respons rechtstreeks terug en loopt die
     * stap niet, dus wordt {@code instance} hier gezet.
     *
     * <p>Via {@code uriInfo.getPath()} en {@code InstanceUtils}, dezelfde route als de extensie:
     * die laat het root-path weg en levert {@code null} bij een pad dat geen geldige URI oplevert.
     * Zelf een {@code URI} bouwen werpt daar, en dan valt een nette 404 om in een 500.
     */
    public static Response problemResponse(Response.Status status, String title, String detail,
            UriInfo uriInfo) {
        return HttpProblem.builder()
                .withStatus(status)
                .withTitle(title)
                .withDetail(detail)
                .withInstance(InstanceUtils.pathToInstance(uriInfo.getPath()))
                .build()
                .toResponse();
    }
}
