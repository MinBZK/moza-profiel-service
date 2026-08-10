package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import nl.rijksoverheid.moz.architectuur.fixtures.RequestMutaties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Toetst de regels zelf, niet de productiecode.
 *
 * <p>{@link RequestDtoOnveranderbaarheidTest} draait ze tegen een codebase die ze niet
 * overtreedt, dus daar zijn ze per definitie groen — ook als ze niets zouden vangen. Dat is bij
 * het opzetten van deze regels (#651) drie keer misgegaan: eerst dekte {@code callMethodWhere}
 * geen method references, daarna liet de naam-allow-list de fluent setter {@code isDefault(Boolean)}
 * door, en vervolgens zag de ariteitsregel via {@code callCodeUnitWhere} opnieuw geen method
 * references. Steeds bleven beide regels groen en steeds stond in het commentaar dat het gat
 * gedicht was.
 *
 * <p>Deze test legt per ontsnappingsvorm vast wélke regel hem pakt, niet alleen dát er één
 * regel op afgaat. Dat onderscheid is nodig: elke vorm in {@link RequestMutaties} roept een
 * methode met minstens één parameter aan, dus de ariteitsregel pakt ze allemaal in haar eentje.
 * Met een {@code anyMatch} over beide regels zou het weghalen van de naamregel deze test dus
 * groen laten — precies de vorm van vals groen die hij hoort te voorkomen.
 *
 * <p>Een nieuwe ontsnappingsvorm hoort een klasse in {@link RequestMutaties} én een waarde in
 * {@link Mutatievorm} te krijgen. Wordt die tweede stap vergeten, dan valt
 * {@link #elkeFixtureIsGedekt()} om in plaats van dat de vorm ongemerkt buiten de dekking valt.
 */
class RegelDekkingTest {

    /**
     * Per fixture: welke regel hoort hem te pakken. De verwachting staat hier expliciet zodat
     * een regel die stilzwijgend niets meer vangt zichtbaar wordt, ook als de andere regel hem
     * nog dekt.
     */
    enum Mutatievorm {
        /** Gewone setter: naam valt buiten de allow-list én de aanroep heeft een parameter. */
        DIRECTE_SETTER(RequestMutaties.DirecteSetter.class, true, true),
        /** Fluent setter met de property-naam: idem. */
        FLUENT_SETTER(RequestMutaties.FluentSetter.class, true, true),
        /** {@code isDefault(Boolean)} matcht {@code is[A-Z].*}; alleen de ariteitsregel pakt hem. */
        FLUENT_SETTER_MET_GETTER_NAAM(RequestMutaties.FluentSetterMetGetterNaam.class, false, true),
        /** Method reference naar een gewone setter: beide regels, mits ze toegangen zien. */
        METHOD_REFERENCE_NAAR_SETTER(RequestMutaties.MethodReferenceNaarSetter.class, true, true),
        /** Getter-achtige naam én method reference: opnieuw alleen de ariteitsregel. */
        METHOD_REFERENCE_NAAR_FLUENT_SETTER_MET_GETTER_NAAM(
                RequestMutaties.MethodReferenceNaarFluentSetterMetGetterNaam.class, false, true);

        private final Class<?> fixture;
        private final boolean gepaktDoorNaamregel;
        private final boolean gepaktDoorAriteitsregel;

        Mutatievorm(Class<?> fixture, boolean gepaktDoorNaamregel, boolean gepaktDoorAriteitsregel) {
            this.fixture = fixture;
            this.gepaktDoorNaamregel = gepaktDoorNaamregel;
            this.gepaktDoorAriteitsregel = gepaktDoorAriteitsregel;
        }
    }

    private static final List<ArchRule> REGELS = List.of(
            RequestOnveranderbaarheidRegels.GEEN_MUTERENDE_NAAM,
            RequestOnveranderbaarheidRegels.GEEN_AANROEP_MET_PARAMETERS);

    @ParameterizedTest
    @EnumSource(Mutatievorm.class)
    void elkeMutatievormWordtDoorDeVerwachteRegelsGepakt(Mutatievorm vorm) {
        JavaClasses klassen = new ClassFileImporter().importClasses(vorm.fixture);

        Assertions.assertEquals(vorm.gepaktDoorNaamregel,
                faalt(RequestOnveranderbaarheidRegels.GEEN_MUTERENDE_NAAM, klassen),
                vorm.fixture.getSimpleName() + ": de naamregel gedraagt zich anders dan verwacht");
        Assertions.assertEquals(vorm.gepaktDoorAriteitsregel,
                faalt(RequestOnveranderbaarheidRegels.GEEN_AANROEP_MET_PARAMETERS, klassen),
                vorm.fixture.getSimpleName() + ": de ariteitsregel gedraagt zich anders dan verwacht");

        Assertions.assertTrue(vorm.gepaktDoorNaamregel || vorm.gepaktDoorAriteitsregel,
                vorm.fixture.getSimpleName() + " muteert een binnenkomend request maar wordt door"
                        + " geen enkele regel gevlagd");
    }

    /**
     * Houdt {@link Mutatievorm} en {@link RequestMutaties} gelijk. Zonder deze test is de belofte
     * "een nieuwe vorm krijgt een fixture" afhankelijk van of de auteur ook aan de opsomming
     * hierboven denkt, en zou een vergeten regel er als volledige dekking uitzien.
     */
    @Test
    void elkeFixtureIsGedekt() {
        Set<Class<?>> gedekt = Arrays.stream(Mutatievorm.values())
                .map(vorm -> vorm.fixture)
                .collect(Collectors.toSet());

        List<String> ongedekt = Arrays.stream(RequestMutaties.class.getDeclaredClasses())
                .filter(fixture -> fixture != RequestMutaties.AlleenLezen.class)
                .filter(fixture -> !gedekt.contains(fixture))
                .map(Class::getSimpleName)
                .toList();

        Assertions.assertTrue(ongedekt.isEmpty(),
                "Deze fixtures staan niet in Mutatievorm en worden dus nergens getoetst: " + ongedekt);
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
