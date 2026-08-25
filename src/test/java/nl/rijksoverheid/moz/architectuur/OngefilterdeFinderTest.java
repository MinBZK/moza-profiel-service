package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.VerwijderbareEntiteit;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * findById/listAll/streamAll/deleteAll zijn geërfd van PanacheEntityBase en filteren
 * verwijderdOp niet — anders dan Contactgegeven.find(partij, id) e.d., die dat wél doen. Verwart
 * een latere refactor de twee, dan komt een soft deleted rij weer tevoorschijn: resurrection,
 * expliciet verboden (zie PartijService.findOrCreatePartij). Enige uitzondering:
 * RetentieScheduler.cascadeDeleteLegePartijen roept Partij.findById(id) aan op een id dat net uit
 * een eigen, al-gefilterde reconciliatiequery komt.
 * <p>
 * Partij.getContactgegevens()/getVoorkeuren()/getIdentificaties() vallen om dezelfde reden onder
 * deze regel: ze geven de rauwe, ongefilterde @OneToMany-collectie terug. getContactgegevens/
 * getVoorkeuren hebben vandaag geen aanroeper buiten het entity-pakket (productiecode gebruikt
 * Contactgegeven.find(partij)/Voorkeur.find(partij), die wél filteren). getIdentificaties heeft er
 * wél één: PartijService.deleteLegePartij cascadet welbewust élke identificatie van een net-
 * geverwijderde partij, ongeacht status — vandaar de tweede uitzondering hieronder.
 */
class OngefilterdeFinderTest {

    private static final String ENTITY_PAKKET = "nl.rijksoverheid.moz.entity..";
    private static final String RETENTIE_SCHEDULER = "nl.rijksoverheid.moz.job.RetentieScheduler";
    private static final String PARTIJ_SERVICE = "nl.rijksoverheid.moz.services.PartijService";

    // Afgeleid van VerwijderbareEntiteit i.p.v. een losse, hand-onderhouden lijst: een vijfde
    // soft-deletable entiteit valt hiermee automatisch onder de regel zodra ze de basisklasse
    // extendt, zonder dat iemand deze test hoeft bij te werken.
    private static final DescribedPredicate<JavaClass> IS_ONGEFILTERDE_ENTITEIT =
            assignableTo(VerwijderbareEntiteit.class).and(not(type(VerwijderbareEntiteit.class)));

    private static final DescribedPredicate<JavaAccess<?>> DOEL_IS_ONGEFILTERDE_FINDER =
            target(owner(IS_ONGEFILTERDE_ENTITEIT))
                    .and(target(nameMatching("findById|listAll|streamAll|deleteAll|getContactgegevens|getVoorkeuren|getIdentificaties")));

    private static final DescribedPredicate<JavaAccess<?>> IS_CASCADE_UITZONDERING =
            DescribedPredicate.describe("de uitzondering in RetentieScheduler.cascadeDeleteLegePartijen",
                    toegang -> toegang.getOrigin().getOwner().getFullName().equals(RETENTIE_SCHEDULER)
                            && toegang.getOrigin().getName().equals("cascadeDeleteLegePartijen"));

    private static final DescribedPredicate<JavaAccess<?>> IS_DELETE_LEGE_PARTIJ_UITZONDERING =
            DescribedPredicate.describe("de uitzondering in PartijService.deleteLegePartij",
                    toegang -> toegang.getOrigin().getOwner().getFullName().equals(PARTIJ_SERVICE)
                            && toegang.getOrigin().getName().equals("deleteLegePartij"));

    private static final ArchRule REGEL = noClasses()
            .that().resideOutsideOfPackage(ENTITY_PAKKET)
            .should().accessTargetWhere(DOEL_IS_ONGEFILTERDE_FINDER
                    .and(not(IS_CASCADE_UITZONDERING))
                    .and(not(IS_DELETE_LEGE_PARTIJ_UITZONDERING)))
            .because("dit omzeilt de soft-delete-filter en kan een verwijderde rij laten herleven");

    @Test
    void productiecodeGebruiktGeenOngefilterdeFinderBuitenHetEntityPakket() {
        JavaClasses klassen = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("nl.rijksoverheid.moz");

        REGEL.check(klassen);
    }

    /**
     * Bewijst dat de regel ook echt afgaat, niet alleen dat productiecode hem toevallig niet
     * raakt. Zonder deze test zou een predicate-fout (verkeerde methodenaam, verkeerd pakket)
     * onopgemerkt blijven — de vorige test zou dan voor de verkeerde reden groen zijn.
     */
    @Test
    void regelDetecteertEenEchteOvertreding() {
        JavaClasses klassen = new ClassFileImporter().importClasses(Overtreder.class);

        Assertions.assertThrows(AssertionError.class, () -> REGEL.check(klassen),
                "de regel hoort Overtreder.lees() te vlaggen");
    }

    /**
     * Losse test van de getContactgegevens/getVoorkeuren-namen in DOEL_IS_ONGEFILTERDE_FINDER:
     * regelDetecteertEenEchteOvertreding alleen bewijst niet dat déze twee namen ook echt in de
     * regex zitten — een tikfout daarin zou door die test heen glippen zolang findById nog werkt.
     */
    @Test
    void regelDetecteertEenOngefilterdeGetterOvertreding() {
        JavaClasses klassen = new ClassFileImporter().importClasses(OvertrederViaGetter.class);

        Assertions.assertThrows(AssertionError.class, () -> REGEL.check(klassen),
                "de regel hoort OvertrederViaGetter.lees() te vlaggen");
    }

    static class Overtreder {
        Voorkeur lees(UUID id) {
            return Voorkeur.findById(id);
        }
    }

    static class OvertrederViaGetter {
        List<Contactgegeven> lees(Partij partij) {
            return partij.getContactgegevens();
        }
    }
}
