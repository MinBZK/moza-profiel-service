package nl.rijksoverheid.moz.filter;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;

/**
 * Wijst een lege request body af zodra die is gedeserialiseerd, maar vóórdat de
 * resource-methode draait. Daardoor wordt voor zo'n verzoek geen LDV-span
 * aangemaakt: er zijn immers geen persoonsgegevens verwerkt, dus hoort het niet
 * in het logboek thuis. De fout rendert als application/problem+json (RFC 9457).
 *
 * <p>De check kijkt naar het gedeserialiseerde resultaat in plaats van naar
 * transport-headers (Content-Length): dat dekt zowel een ontbrekende body als een
 * letterlijke {@code null} body.
 */
@Provider
@RequireBody
public class RequireBodyReaderInterceptor implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        Object body = context.proceed();
        if (body == null) {
            throw HttpProblem.valueOf(Response.Status.BAD_REQUEST, "Request body mag niet leeg zijn");
        }
        return body;
    }
}
