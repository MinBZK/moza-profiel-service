package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import nl.rijksoverheid.moz.architectuur.fixtures.RequestMutaties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

/**
 * Toetst de regels zelf, niet de productiecode.
 *
 * <p>{@link RequestDtoOnveranderbaarheidTest} draait ze tegen een codebase die ze niet
 * overtreedt, dus daar zijn ze per definitie groen — ook als ze niets zouden vangen. Dat is in
 * deze PR drie keer misgegaan: eerst dekte {@code callMethodWhere} geen method references, daarna
 * liet de naam-allow-list de fluent setter {@code isDefault(Boolean)} door, en vervolgens zag de
 * ariteitsregel via {@code callCodeUnitWhere} opnieuw geen method references. Steeds bleven beide
 * regels groen en steeds stond in het commentaar dat het gat gedicht was.
 *
 * <p>Deze test dwingt af dat elke bekende ontsnappingsvorm door minstens één regel wordt gepakt.
 * Een nieuwe vorm hoort een klasse in {@link RequestMutaties} te krijgen; blijkt die er dan
 * doorheen te komen, dan faalt deze test in plaats van dat de vorm ongemerkt toegevoegd wordt.
 */
class RegelDekkingTest {

    private static final List<ArchRule> REGELS = List.of(
            RequestOnveranderbaarheidRegels.GEEN_MUTERENDE_NAAM,
            RequestOnveranderbaarheidRegels.GEEN_AANROEP_MET_PARAMETERS);

    @ParameterizedTest
    @ValueSource(classes = {
            RequestMutaties.DirecteSetter.class,
            RequestMutaties.FluentSetter.class,
            RequestMutaties.FluentSetterMetGetterNaam.class,
            RequestMutaties.MethodReferenceNaarSetter.class,
            RequestMutaties.MethodReferenceNaarFluentSetterMetGetterNaam.class,
    })
    void elkeMutatievormWordtDoorMinstensEenRegelGepakt(Class<?> mutatie) {
        JavaClasses klassen = new ClassFileImporter().importClasses(mutatie);

        boolean gepakt = REGELS.stream().anyMatch(regel -> faalt(regel, klassen));

        Assertions.assertTrue(gepakt,
                mutatie.getSimpleName() + " muteert een binnenkomend request maar wordt door geen"
                        + " enkele regel gevlagd");
    }

    @Test
    void lezenWordtDoorGeenEnkeleRegelGepakt() {
        JavaClasses klassen = new ClassFileImporter().importClasses(RequestMutaties.AlleenLezen.class);

        for (ArchRule regel : REGELS) {
            Assertions.assertFalse(faalt(regel, klassen),
                    "Een klasse die het request alleen uitleest hoort geen overtreding op te"
                            + " leveren, maar werd gevlagd door: " + regel.getDescription());
        }
    }

    private static boolean faalt(ArchRule regel, JavaClasses klassen) {
        try {
            regel.check(klassen);
            return false;
        } catch (AssertionError verwacht) {
            return true;
        }
    }
}
