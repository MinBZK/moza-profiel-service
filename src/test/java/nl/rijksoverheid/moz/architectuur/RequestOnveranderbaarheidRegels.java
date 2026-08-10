package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * De twee regels die vastleggen dat een binnenkomend request blijft wat de aanroeper heeft
 * gestuurd. Ze staan hier apart zodat {@link RequestDtoOnveranderbaarheidTest} ze tegen de
 * productiecode kan draaien en {@link RegelDekkingTest} tegen fixtures die elke bekende
 * ontsnappingsvorm belichamen. Zonder die tweede toepassing is de dekking van een regel een
 * bewering in commentaar in plaats van een gemeten eigenschap — en dat is in deze codebase
 * al drie keer misgegaan.
 */
final class RequestOnveranderbaarheidRegels {

    static final String GEGENEREERDE_MODELLEN = "nl.rijksoverheid.moz.api.generated.model";

    /**
     * Lezen is {@code get*}/{@code is*} plus de drie methodes van {@link Object};
     * {@code <init>} staat erbij omdat een request aanmaken uiteraard mag.
     */
    private static final String TOEGESTAAN = "get[A-Z].*|is[A-Z].*|equals|hashCode|toString|<init>";

    /**
     * {@code equals} is de enige echte uitzondering. {@code <init>} staat er voor het geval de
     * generator ooit een all-args constructor emit; vandaag declareert geen enkele gegenereerde
     * {@code *Request} een constructor, dus elke {@code <init>}-aanroep heeft nul parameters en
     * valt sowieso al buiten deze regel.
     */
    private static final String TOEGESTAAN_MET_PARAMETERS = "equals|<init>";

    private static final DescribedPredicate<JavaAccess<?>> DOEL_MET_PARAMETERS =
            DescribedPredicate.describe("een doel met parameters",
                    toegang -> toegang.getTarget() instanceof AccessTarget.CodeUnitAccessTarget doel
                            && !doel.getRawParameterTypes().isEmpty());

    private static final DescribedPredicate<JavaAccess<?>> DOEL_IS_BINNENKOMEND_REQUEST =
            target(owner(resideInAPackage(GEGENEREERDE_MODELLEN).and(simpleNameEndingWith("Request"))));

    /**
     * Naam-allow-list: alles wat niet leest, muteert. Dekt de gewone {@code setX} en de fluent
     * setter die de naam van de property draagt.
     */
    static final ArchRule GEEN_MUTERENDE_NAAM = noClasses()
            .that().resideOutsideOfPackage(GEGENEREERDE_MODELLEN)
            .should().accessTargetWhere(
                    DOEL_IS_BINNENKOMEND_REQUEST.and(not(target(nameMatching(TOEGESTAAN)))))
            .because("een binnenkomend request hoort te blijven wat de aanroeper heeft gestuurd; "
                    + "lees het uit en bouw een eigen object als er iets afgeleid moet worden");

    /**
     * Ariteit vangt wat de naam niet kan vangen. Een property die zelf al {@code is}-achtig heet
     * — {@code isDefault} op {@code ContactgegevenUpdateRequest} — levert een fluent setter
     * {@code isDefault(Boolean)} op die {@code is[A-Z].*} matcht en dus door de naamregel heen
     * glipt. Het gaat om de naam van de property, niet om het type: een {@code String} met die
     * naam is even riskant, en een boolean {@code actief} levert {@code actief(Boolean)} op, wat
     * de allow-list niet matcht.
     *
     * <p>Ook deze regel gebruikt {@code accessTargetWhere} en niet {@code callCodeUnitWhere}:
     * dat laatste ziet alleen {@code JavaCall}, terwijl ArchUnit een method reference als een
     * eigen toegangssoort modelleert. {@code Consumer<Boolean> c = request::isDefault} kwam er
     * anders langs beide regels heen.
     */
    static final ArchRule GEEN_AANROEP_MET_PARAMETERS = noClasses()
            .that().resideOutsideOfPackage(GEGENEREERDE_MODELLEN)
            .should().accessTargetWhere(
                    DOEL_IS_BINNENKOMEND_REQUEST
                            .and(not(target(nameMatching(TOEGESTAAN_MET_PARAMETERS))))
                            .and(DOEL_MET_PARAMETERS))
            .because("een methode met parameters op een binnenkomend request schrijft, ook als "
                    + "zijn naam op een getter lijkt");

    private RequestOnveranderbaarheidRegels() {
    }
}
