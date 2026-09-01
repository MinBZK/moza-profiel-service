package nl.rijksoverheid.moz.validation;

import nl.rijksoverheid.moz.common.IdentificatieType;

/**
 * Vorm van een request dat een partij aanduidt met een type en een nummer.
 *
 * <p>Bestaat zodat {@link IdentificatieNummerValidator} niet aan één DTO vastzit. Op dit
 * moment implementeert alleen {@code EmailVerificatieRequest} hem, via {@code x-implements}
 * in het contract, naast de {@code x-class-extra-annotation} die de constraint zelf oplevert.
 * De overige requests dragen hetzelfde veldenpaar maar worden bewust niet gevalideerd: het
 * identificatienummer wordt alleen op dit endpoint gecontroleerd.
 *
 * <p>Let op dat die twee vendor-extensies bij elkaar horen. Alleen de annotatie levert bij de
 * eerste validatie een {@code UnexpectedTypeException} op, en dus een 500 in plaats van een
 * 400; alleen de interface valideert stilzwijgend niets.
 */
public interface HeeftIdentificatie {

    IdentificatieType getIdentificatieType();

    String getIdentificatieNummer();
}
