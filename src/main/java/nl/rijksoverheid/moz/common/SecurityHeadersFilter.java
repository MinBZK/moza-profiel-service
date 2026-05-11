package nl.rijksoverheid.moz.common;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    private static final String API_VERSION = "1.0.0";

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        headers.putSingle("Cache-Control", "no-store");
        headers.putSingle("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        headers.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.putSingle("X-Content-Type-Options", "nosniff");
        headers.putSingle("X-Frame-Options", "DENY");
        headers.putSingle("Referrer-Policy", "no-referrer");
        headers.putSingle("API-Version", API_VERSION);
    }
}
