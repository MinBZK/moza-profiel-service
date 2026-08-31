package nl.rijksoverheid.moz.validation;

import jakarta.enterprise.inject.Instance;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import nl.rijksoverheid.moz.api.generated.model.ContactgegevenRequest;
import nl.rijksoverheid.moz.common.ContactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * De validator draait alleen op {@link ContactType#Email}; de overige types dragen in
 * {@code waarde} een telefoonnummer of applicatie-ID en moeten ongemoeid blijven.
 *
 * <p>De ongeldige adressen hieronder zijn met opzet grof. Jakarta {@code @Email} is bewust ruim —
 * {@code jan@localhost} keurt hij goed — dus een test met een randgeval zou hier het gedrag van
 * Hibernate Validator vastpinnen in plaats van dat van deze klasse.
 */
class EmailWaardeValidatorTest {

    /**
     * Bewust niet in een try-with-resources: de {@link Validator} hieruit wordt in elke test nog
     * gebruikt, en een gesloten factory maakt dat gedrag ongedefinieerd. De test-JVM ruimt hem op.
     */
    private static final Validator JAKARTA = Validation.buildDefaultValidatorFactory().getValidator();

    private EmailWaardeValidator validator;

    @BeforeEach
    void setUp() {
        // Via een gemockte Instance en niet via een aparte constructor: zo draait deze test de
        // productieconstructor en houdt de klasse één constructiepad.
        @SuppressWarnings("unchecked")
        Instance<Validator> instance = Mockito.mock(Instance.class);
        Mockito.when(instance.get()).thenReturn(JAKARTA);
        validator = new EmailWaardeValidator(instance);
    }

    @Test
    void nullRequestIsGeldig() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void ontbrekendTypeIsGeldig() {
        assertTrue(validator.isValid(verzoek(null, "geen adres"), null));
    }

    /** Een ontbrekende waarde is het werk van de gegenereerde {@code @NotNull}, niet van deze klasse. */
    @Test
    void ontbrekendeWaardeIsGeldig() {
        assertTrue(validator.isValid(verzoek(ContactType.Email, null), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jan@rijksoverheid.nl", "j.de.vries+tag@voorbeeld.co.uk"})
    void geldigAdresPasseert(String waarde) {
        assertTrue(validator.isValid(verzoek(ContactType.Email, waarde), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"geen adres", "jan(at)rijksoverheid.nl", "jan@@rijksoverheid.nl", "@rijksoverheid.nl"})
    void ongeldigAdresWordtAfgewezen(String waarde) {
        assertFalse(validator.isValid(verzoek(ContactType.Email, waarde), contextMock()));
    }

    /**
     * Dezelfde waarde onder een ander type hoort door te komen: een telefoonnummer is nooit een
     * geldig e-mailadres, dus zou een validator die het type negeert hier omvallen.
     */
    @ParameterizedTest
    @EnumSource(value = ContactType.class, names = "Email", mode = EnumSource.Mode.EXCLUDE)
    void anderContactTypePasseert(ContactType type) {
        assertTrue(validator.isValid(verzoek(type, "0612345678"), null));
    }

    /**
     * De melding hoort aan {@code waarde} te hangen en niet aan de klasse, anders meldt de 400 een
     * lege {@code field} en weet de aanroeper niet wat hij moet aanpassen.
     *
     * <p>{@code getDefaultConstraintMessageTemplate} wordt expliciet gestubd: op een deep stub
     * geeft hij {@code null} terug, en dan matcht geen enkele {@code anyString()} eronder meer.
     */
    @Test
    void meldingHangtAanHetVeldWaarde() {
        String sjabloon = "{waarde is geen geldig e-mailadres}";
        ConstraintValidatorContext context = contextMock();
        var opbouw = Mockito.mock(ConstraintValidatorContext.ConstraintViolationBuilder.class,
                Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(context.getDefaultConstraintMessageTemplate()).thenReturn(sjabloon);
        Mockito.when(context.buildConstraintViolationWithTemplate(sjabloon)).thenReturn(opbouw);

        validator.isValid(verzoek(ContactType.Email, "geen adres"), context);

        Mockito.verify(context).disableDefaultConstraintViolation();
        Mockito.verify(opbouw).addPropertyNode("waarde");
    }

    private static ConstraintValidatorContext contextMock() {
        return Mockito.mock(ConstraintValidatorContext.class, Mockito.RETURNS_DEEP_STUBS);
    }

    private static ContactgegevenRequest verzoek(ContactType type, String waarde) {
        ContactgegevenRequest request = new ContactgegevenRequest();
        request.setType(type);
        request.setWaarde(waarde);
        return request;
    }
}
