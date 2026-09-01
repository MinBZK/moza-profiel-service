package nl.rijksoverheid.moz.validation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import nl.rijksoverheid.moz.common.ContactType;

/**
 * Toetst {@code waarde} als e-mailadres zodra {@code type} op {@link ContactType#Email} staat;
 * zie {@link HeeftContactWaarde} voor waarom die regel niet op het veld zelf staat.
 *
 * <p>Anders dan {@link IdentificatieNummerValidator} is dit een CDI-bean, want hij heeft een
 * {@link Validator} nodig. Zonder de scope- en injectie-annotaties zoekt Hibernate Validator een
 * parameterloze constructor, die er niet is, en start de applicatie niet meer op
 * ({@code HV000064}).
 *
 * <p>Eén gedeelde instantie mag omdat de constraint geen attributen heeft en {@code initialize}
 * dus niets hoeft te onthouden.
 */
@ApplicationScoped
public class EmailWaardeValidator implements ConstraintValidator<ValidEmailWaarde, HeeftContactWaarde> {

    /**
     * Draagt Jakarta's {@code @Email}, zodat de invulling daarvan hier hergebruikt wordt in plaats
     * van nagebouwd. {@code validateValue} leest de constraint van het veld en heeft geen instantie
     * nodig; het veld wordt dus nooit gevuld. Het heet {@code email} en niet {@code waarde}, zodat
     * het niet te verwarren is met de property van de DTO waaraan de melding hangt.
     */
    private static final class EmailVeld {
        @Email
        String email;
    }

    private final Instance<Validator> jakarta;

    /**
     * {@link Instance} en geen {@link Validator}: Hibernate Validator bouwt deze klasse terwijl de
     * {@code ValidatorFactory} zelf nog wordt aangemaakt. Een directe injectie vraagt daar om de
     * factory die er op dat moment nog niet is, en dat eindigt in een {@code StackOverflowError}
     * bij het opstarten. Zo wordt de validator pas bij de eerste validatie opgehaald — met als
     * keerzijde dat een bedradingsfout hier geen opstartfout meer is maar een 500 per verzoek.
     */
    @Inject
    public EmailWaardeValidator(Instance<Validator> jakarta) {
        this.jakarta = jakarta;
    }

    @Override
    public boolean isValid(HeeftContactWaarde request, ConstraintValidatorContext context) {
        // Blanco valt hier bewust ook buiten: de pattern op waarde wijst dat al af, en zonder deze
        // uitzondering levert één blanco waarde twee violations op hetzelfde veld op.
        if (request == null || request.getWaarde() == null || request.getWaarde().isBlank()
                || !moetEmailadresZijn(request.getType())) {
            return true; // Een ontbrekende waarde is het werk van @NotNull, een blanco van @Pattern
        }

        if (jakarta.get().validateValue(EmailVeld.class, "email", request.getWaarde()).isEmpty()) {
            return true;
        }

        // Zonder property-node hangt de melding aan de klasse en meldt de 400 een leeg veld.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("waarde")
                .addConstraintViolation();

        return false;
    }

    /**
     * Een {@code switch} zonder {@code default}, net als {@link IdentificatieNummerValidator}: een
     * nieuw contacttype is dan een compilefout in plaats van een waarde die stilzwijgend
     * ongevalideerd doorkomt.
     */
    private static boolean moetEmailadresZijn(ContactType type) {
        if (type == null) {
            return false; // @NotNull op type meldt dit zelf
        }

        return switch (type) {
            case Email -> true;
            case Telefoonnummer, ApplicatieId -> false;
        };
    }
}
