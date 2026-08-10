package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
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
 * emitteert in openapi-generator 7.10.0 onvoorwaardelijk setters (zowel {@code setX} als de
 * fluent variant {@code x}) en kent geen schakelaar om er records of onveranderbare klassen
 * van te maken; {@code generateBuilders} zet er een builder naast in plaats van de setters
 * te vervangen. Zonder eigen mustache-template — met het onderhoud dat daarbij hoort — is
 * een architectuurregel de manier om de eigenschap vast te leggen. Bij een upgrade van de
 * generator is die aanname het narekenen waard.
 *
 * <p>Drie uitzonderingen, alle drie bewust:
 *
 * <ul>
 *   <li>De {@code *Response}-typen vallen erbuiten omdat MapStruct ze via setters vult;
 *       de gegenereerde mapper-implementatie zou de regel per definitie overtreden.</li>
 *   <li>Tests vallen erbuiten omdat die hun request-bodies juist mét setters opbouwen.</li>
 *   <li>De gegenereerde modellen zelf vallen erbuiten. Sinds {@code accessTargetWhere}
 *       tellen ook veldtoegangen mee, en hun {@code equals}, {@code hashCode},
 *       {@code toString} en adders komen rechtstreeks bij de privévelden; daarnaast
 *       roept {@code toString} de private {@code toIndentedString} aan.</li>
 * </ul>
 *
 * <p>Eén gat dat de regel niet kan dichten: een lijst-getter geeft de levende collectie
 * terug, dus {@code request.getIdentificaties().clear()} muteert het request terwijl het
 * aanroepdoel {@code java.util.List} is en daarmee buiten de eigenaarsfilter valt. Groen
 * betekent dus "niet via de DTO gemuteerd", niet "onveranderlijk". Wie een lijst uit een
 * request bewerkt, kopieert hem eerst.
 */
class RequestDtoOnveranderbaarheidTest {

    private static final String GEGENEREERDE_MODELLEN = "nl.rijksoverheid.moz.api.generated.model";

    /**
     * Lezen is {@code get*}/{@code is*} plus de drie methodes van {@link Object};
     * {@code <init>} staat erbij omdat een request aanmaken uiteraard mag.
     */
    private static final String TOEGESTAAN = "get[A-Z].*|is[A-Z].*|equals|hashCode|toString|<init>";

    /**
     * Een allow-list in plaats van een verbod op {@code set*}: de generator maakt naast
     * {@code setWaarde(...)} ook een fluent {@code waarde(...)} die net zo goed muteert.
     * Die tweede vorm draagt de naam van de property en is dus niet aan zijn naam te
     * herkennen. Andersom kan wel: alles wat niet leest, muteert.
     *
     * <p>{@code accessTargetWhere} en niet {@code callMethodWhere}: dat laatste dekt alleen
     * echte aanroepen, terwijl ArchUnit een method reference als een eigen toegangssoort
     * modelleert. Een mutatie in de vorm
     * {@code Consumer<String> c = request::setIdentificatieNummer; c.accept("x");} kwam er
     * met {@code callMethodWhere} ongemerkt doorheen.
     */
    @Test
    void productiecodeMuteertGeenBinnenkomendRequest() {
        JavaClasses productieklassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("nl.rijksoverheid.moz");

        ArchRule regel = noClasses()
                .that().resideOutsideOfPackage(GEGENEREERDE_MODELLEN)
                .should().accessTargetWhere(
                        target(owner(resideInAPackage(GEGENEREERDE_MODELLEN)
                                .and(simpleNameEndingWith("Request"))))
                                .and(not(target(nameMatching(TOEGESTAAN)))))
                .because("een binnenkomend request hoort te blijven wat de aanroeper heeft gestuurd; "
                        + "lees het uit en bouw een eigen object als er iets afgeleid moet worden");

        regel.check(productieklassen);
    }
}
