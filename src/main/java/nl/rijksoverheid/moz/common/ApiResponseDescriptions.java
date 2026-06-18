package nl.rijksoverheid.moz.common;

/**
 * Hergebruikte OpenAPI-beschrijvingen voor @APIResponse-annotaties, zodat
 * gelijke responses overal dezelfde tekst tonen.
 */
public final class ApiResponseDescriptions {

    /** 400 voor endpoints met een verplichte request body (ontbreekt of faalt validatie). */
    public static final String BAD_REQUEST_BODY = "Request body ontbreekt of is ongeldig";

    /** Generieke 500, per controller toegevoegd via class-level {@code @APIResponse}. */
    public static final String INTERNAL_SERVER_ERROR = "Interne serverfout";

    /** 404 wanneer het contactgegeven of de bijbehorende partij niet bestaat. */
    public static final String CONTACTGEGEVEN_OF_PARTIJ_NIET_GEVONDEN = "Contactgegeven of partij niet gevonden";

    /** 404 wanneer de voorkeur of de bijbehorende partij niet bestaat. */
    public static final String VOORKEUR_OF_PARTIJ_NIET_GEVONDEN = "Voorkeur of partij niet gevonden";

    private ApiResponseDescriptions() {}
}
