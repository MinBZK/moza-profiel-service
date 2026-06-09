package nl.rijksoverheid.moz.filter;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Wijst een lege request body af vóórdat de resource-methode draait. Daardoor
 * wordt voor zo'n verzoek geen LDV-span aangemaakt: er zijn immers geen
 * persoonsgegevens verwerkt, dus hoort het niet in het logboek thuis.
 * De fout rendert als application/problem+json (RFC 9457).
 *
 * <p>De detectie kijkt naar de request-headers in plaats van naar
 * {@code hasEntity()}: die is in Quarkus REST onbetrouwbaar in een request-filter
 * omdat de body op dat moment nog niet is ingelezen. Een verzoek heeft alleen een
 * body als Content-Length &gt; 0 is of als het chunked wordt verstuurd.
 */
@Provider
@RequireBody
public class RequireBodyFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        String transferEncoding = ctx.getHeaderString("Transfer-Encoding");
        boolean chunked = transferEncoding != null && transferEncoding.toLowerCase().contains("chunked");

        // Een verzoek draagt alleen een body als het chunked is of Content-Length > 0 heeft.
        if (!chunked && ctx.getLength() <= 0) {
            throw HttpProblem.valueOf(Response.Status.BAD_REQUEST, "Request body mag niet leeg zijn");
        }
    }
}
