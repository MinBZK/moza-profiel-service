package nl.rijksoverheid.moz.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bij {@code type} Email moet {@code waarde} een e-mailadres zijn;
 * MinBZK/MijnOverheidZakelijk#766. Class-level, want de regel gaat over twee velden.
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailWaardeValidator.class)
public @interface ValidEmailWaarde {
    String message() default "waarde is geen geldig e-mailadres voor het opgegeven type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
