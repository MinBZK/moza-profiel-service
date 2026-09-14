package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * De controllers implementeren de uit het contract gegenereerde interfaces, die de
 * paden, HTTP-methodes, mediatypes en de validatie van de body-parameter dragen. Deze test
 * bewaakt dat ze die annotaties niet alsnog zelf gaan dragen.
 *
 * <p>Dat is geen stijlregel. De javadoc van de controllers beschrijft twee faalwijzen die allebei
 * gemeten zijn maar door niets anders worden vastgelegd, en die in tegengestelde richting
 * misleiden:
 *
 * <ul>
 *   <li>Een HTTP-methode-annotatie op een implementatiemethode laat álle annotaties van de
 *       interface voor die methode vervallen, ook {@code @Path}. De gedocumenteerde route geeft
 *       dan een 404 die niet te onderscheiden is van "resource niet gevonden", en de methode
 *       herbindt zich stilzwijgend aan het pad op klasseniveau.</li>
 *   <li>Een {@code @Consumes} of {@code @Produces} op een implementatiemethode wordt juist
 *       genegeerd: die van de interface wint. Wie zo'n annotatie neerzet om het mediatype bij te
 *       sturen, ziet niets gebeuren. Het Content-Type van een succesantwoord hoort daarom op de
 *       {@code Response} zelf gezet te worden, zoals de controllers doen.</li>
 * </ul>
 *
 * <p>Een parameterconstraint opnieuw declareren is de derde faalwijze, en die verdedigt zichzelf:
 * Hibernate Validator werpt dan {@code HV000151} en de applicatie start niet meer.
 */
class ControllerAnnotatiesTest {

    private static final String CONTROLLER_PAKKET = "nl.rijksoverheid.moz.controller";

    /** De gegenereerde interfaces die de controllers implementeren. */
    private static final String SERVER_API_PAKKET = "nl.rijksoverheid.moz.api.generated.api";

    private static final List<Class<? extends Annotation>> VERBODEN_OP_DE_IMPLEMENTATIE = List.of(
            GET.class, POST.class, PUT.class, PATCH.class, DELETE.class, HEAD.class, OPTIONS.class,
            Path.class, Consumes.class, Produces.class,
            Valid.class, PathParam.class, QueryParam.class);

    @Test
    void controllersHerhalenGeenAnnotatiesVanDeGegenereerdeInterface() {
        JavaClasses klassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(CONTROLLER_PAKKET);

        List<String> bevindingen = new ArrayList<>();

        for (JavaClass klasse : klassen) {
            boolean implementeertContract = klasse.getAllRawInterfaces().stream()
                    .anyMatch(api -> api.getPackageName().equals(SERVER_API_PAKKET));

            if (!implementeertContract) {
                continue;
            }

            for (Class<? extends Annotation> annotatie : VERBODEN_OP_DE_IMPLEMENTATIE) {
                if (klasse.isAnnotatedWith(annotatie)) {
                    bevindingen.add(klasse.getSimpleName() + " draagt @" + annotatie.getSimpleName());
                }
            }

            for (JavaMethod methode : klasse.getMethods()) {
                for (Class<? extends Annotation> annotatie : VERBODEN_OP_DE_IMPLEMENTATIE) {
                    if (methode.isAnnotatedWith(annotatie)) {
                        bevindingen.add(methode.getOwner().getSimpleName() + "#" + methode.getName()
                                + " draagt @" + annotatie.getSimpleName());
                    }

                    boolean opParameter = methode.getParameters().stream()
                            .anyMatch(parameter -> parameter.isAnnotatedWith(annotatie));

                    if (opParameter) {
                        bevindingen.add(methode.getOwner().getSimpleName() + "#" + methode.getName()
                                + " heeft een parameter met @" + annotatie.getSimpleName());
                    }
                }
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(),
                "Deze annotaties horen op de gegenereerde interface te staan, niet op de "
                        + "implementatie:\n" + String.join("\n", bevindingen));
    }

    /**
     * Zonder deze controle zou de test hierboven ook groen zijn wanneer de controllers de
     * interfaces helemaal niet meer implementeren — dan valt er immers niets te herhalen.
     */
    @Test
    void erZijnControllersDieHetContractImplementeren() {
        JavaClasses klassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(CONTROLLER_PAKKET);

        long aantal = klassen.stream()
                .filter(klasse -> klasse.getAllRawInterfaces().stream()
                        .anyMatch(api -> api.getPackageName().equals(SERVER_API_PAKKET)))
                .count();

        Assertions.assertEquals(3, aantal,
                "Verwacht drie controllers die een gegenereerde interface implementeren");
    }
}
