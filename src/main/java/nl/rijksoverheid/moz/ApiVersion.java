package nl.rijksoverheid.moz;

/**
 * Semantic version of the API contract.
 *
 * Distinct from the artifact version in pom.xml (which tracks the build).
 * Bump the major component on breaking API changes; minor on backwards-compatible
 * additions; patch on backwards-compatible fixes.
 */
public final class ApiVersion {
    public static final String CURRENT = "1.0.0";

    private ApiVersion() {}
}
