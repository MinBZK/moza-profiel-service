package nl.rijksoverheid.moz.common;

/**
 * Media types die niet door {@link jakarta.ws.rs.core.MediaType} worden gedekt.
 * Een constantenklasse in plaats van een enum, omdat de waarde als compile-time
 * constante in annotaties (@Content, @APIResponse) gebruikt moet kunnen worden.
 */
public final class MediaTypes {

    /** RFC 9457 problem details, gebruikt voor alle foutresponses. */
    public static final String PROBLEM_JSON = "application/problem+json";

    private MediaTypes() {}
}
