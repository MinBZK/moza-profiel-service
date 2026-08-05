package nl.rijksoverheid.moz.common;

/**
 * Gedeelde waardes voor logboek-context wanneer er geen echt data-subject te bepalen is
 * (record niet gevonden, of partij zonder identificatie — een geschonden invariant).
 */
public final class LogboekConstants {

    /**
     * Sentinel data-subject-id, gebruikt in plaats van een verzonnen hash van een resource-id.
     * Logboek-consumenten kunnen hierop filteren.
     */
    public static final String GEEN_SUBJECT = "onbekend";

    private LogboekConstants() {}
}
