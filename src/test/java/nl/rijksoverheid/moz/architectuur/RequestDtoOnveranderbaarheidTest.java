package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Een binnenkomend request is niets anders dan wat de aanroeper heeft gestuurd. Wie het
 * onderweg aanpast, laat de rest van de aanroepketen naar iets kijken dat nooit over de
 * lijn is gekomen — en dat is niet meer te herleiden uit het request zelf.
 *
 * <p>De gegenereerde DTO's kunnen die eigenschap niet zelf afdwingen: {@code jaxrs-spec}
 * emitteert onvoorwaardelijk setters (zowel {@code setX} als de fluent variant {@code x})
 * en kent geen schakelaar om er records of onveranderbare klassen van te maken. Zonder
 * eigen mustache-template — met het onderhoud dat daarbij hoort — is een architectuurregel
 * de manier om de eigenschap vast te leggen.
 *
 * <p>Deze regel geldt bewust alleen voor {@code *Request}-typen en alleen voor productiecode:
 *
 * <ul>
 *   <li>De {@code *Response}-typen zijn uitgezonderd omdat MapStruct ze via setters vult;
 *       de gegenereerde mapper-implementatie zou de regel per definitie overtreden.</li>
 *   <li>Tests zijn uitgezonderd omdat die hun request-bodies juist mét setters opbouwen.</li>
 * </ul>
 */
class RequestDtoOnveranderbaarheidTest {

    private static final String GEGENEREERDE_MODELLEN = "nl.rijksoverheid.moz.api.generated.model";

    /**
     * Een allow-list in plaats van een verbod op {@code set*}: de generator maakt naast
     * {@code setWaarde(...)} ook een fluent {@code waarde(...)} die net zo goed muteert.
     * Die tweede vorm draagt de naam van de property en is dus niet aan zijn naam te
     * herkennen. Andersom kan wel: lezen is {@code get*}/{@code is*} plus de drie methodes
     * van {@code Object}, en al het overige muteert.
     */
    @Test
    void productiecodeMuteertGeenBinnenkomendRequest() {
        JavaClasses productieklassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages("nl.rijksoverheid.moz");

        ArchRule regel = noClasses()
                .that().resideOutsideOfPackage(GEGENEREERDE_MODELLEN)
                .should().callMethodWhere(
                        target(owner(resideInAPackage(GEGENEREERDE_MODELLEN)))
                                .and(target(owner(simpleNameEndingWith("Request"))))
                                .and(not(target(nameMatching("get[A-Z].*|is[A-Z].*|equals|hashCode|toString")))))
                .because("een binnenkomend request hoort te blijven wat de aanroeper heeft gestuurd; "
                        + "lees het uit en bouw een eigen object als er iets afgeleid moet worden");

        regel.check(productieklassen);
    }
}
