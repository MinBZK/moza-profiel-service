package nl.rijksoverheid.moz.validation;

import nl.rijksoverheid.moz.common.ContactType;

/**
 * Vorm van een request dat een contactgegeven draagt als een type met een bijbehorende waarde.
 *
 * <p>Bestaat zodat {@link EmailWaardeValidator} niet aan één DTO vastzit. {@code waarde} is
 * polymorf: bij {@link ContactType#Email} een e-mailadres, anders een telefoonnummer of een
 * applicatie-ID. Een {@code format} op dat veld zou dus telefoonnummers gaan weigeren. Het
 * contract drukt de regel uit als {@code if}/{@code then}, maar de generator maakt daar geen
 * Jakarta-constraint van; vandaar een class-level constraint.
 *
 * <p>Implementers exposen de waarde onder de bean-property {@code waarde};
 * {@link EmailWaardeValidator} hangt de melding daaraan.
 *
 * <p>Net als bij {@link HeeftIdentificatie} horen de vendor-extensies {@code x-implements} en
 * {@code x-class-extra-annotation} in het contract onlosmakelijk bij elkaar; zie
 * {@code ValidatieExtensiesTest}.
 */
public interface HeeftContactWaarde {

    ContactType getType();

    String getWaarde();
}
