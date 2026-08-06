package nl.rijksoverheid.moz.validation;

import nl.rijksoverheid.moz.common.IdentificatieType;

/**
 * Gemeenschappelijke vorm van elk request dat een partij aanduidt met een type en een nummer.
 *
 * <p>Bestaat zodat {@link IdentificatieNummerValidator} niet aan één DTO vastzit. De
 * gegenereerde modellen krijgen deze interface via {@code x-implements} in het contract,
 * naast de {@code x-class-extra-annotation} die de constraint zelf oplevert.
 */
public interface HeeftIdentificatie {

    IdentificatieType getIdentificatieType();

    String getIdentificatieNummer();
}
