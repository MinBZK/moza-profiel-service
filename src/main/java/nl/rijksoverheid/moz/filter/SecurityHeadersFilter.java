package nl.rijksoverheid.moz.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import nl.rijksoverheid.moz.ApiVersion;

/**
 * Sets the response headers required by the NL GOV API Design Rules
 * (sectie 3.8, transport security) and the NCSC web-security guidance.
 *
 * `Access-Control-Allow-Origin` is set by Quarkus' built-in CORS handling
 * (configured via `quarkus.http.cors.*` in application.properties), not here.
 */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        var headers = responseContext.getHeaders();
        headers.putSingle("Cache-Control", "no-store");
        headers.putSingle("Content-Security-Policy", "frame-ancestors 'none'");
        headers.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.putSingle("X-Content-Type-Options", "nosniff");
        headers.putSingle("X-Frame-Options", "DENY");
        headers.putSingle("Referrer-Policy", "no-referrer");
        headers.putSingle("API-Version", ApiVersion.CURRENT);
    }
}
