package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * findById/listAll/streamAll/deleteAll/findByIdOptional/findByIds/findAll/deleteById/update/delete
 * zijn geërfd van PanacheEntityBase en filteren verwijderdOp niet — anders dan Contactgegeven.find(partij, id)
 * e.d., die dat wél doen. Partij.getIdentificaties() valt om dezelfde reden onder deze regel: die
 * geeft de rauwe, ongefilterde @OneToMany-collectie terug. Verwart een latere refactor de twee, dan
 * komt een soft deleted rij weer tevoorschijn — resurrection, expliciet verboden, zie
 * PartijService.findOrCreatePartij.
 * <p>
 * Panache's find/list/count/stream met een HQL-string vallen er ook onder: die schrijven de filter
 * zelf en kunnen hem dus weglaten. De gelijknamige statics van de entiteiten zelf (eerste parameter
 * is geen string) blijven buiten schot; dat zijn juist de gefilterde varianten.
 * <p>
 * De uitzonderingen staan in {@code UITZONDERINGEN}: RetentieScheduler.cascadeDeleteLegePartijen
 * gebruikt Partij.findById op een id dat net uit een eigen, al-gefilterde reconciliatiequery komt,
 * PartijService.deleteLegePartij gebruikt Partij.getIdentificaties() om de nog actieve
 * identificaties van een net-verwijderde partij te cascaden, en de overige vijf schrijven hun eigen
 * HQL met de filter erin.
 * <p>
 * Wat deze regel niet doet: ArchUnit ziet de inhoud van een string-argument niet, dus of de HQL van
 * een uitgezonderde methode (of van het entity-pakket zelf) de filter écht bevat, blijft een zaak
 * van code review. De regel vangt alleen nieuwe aanroepen buiten die uitzonderingen om.
 * <p>
 * Geen dekking voor Panache's getEntityManager(): een handmatige JPQL-query daarop omzeilt deze
 * regel net zo goed als hij verbiedt. Productiecode filtert daar zelf, zie RetentieScheduler en
 * PartijService.deleteLegePartij.
 * <p>
 * VerwijderbareEntiteit zelf is package-private (niet aanwijsbaar vanuit dit package): het
 * predicaat werkt daarom op de volledige klassenaam als string, niet op de {@code Class} zelf.
 */
class OngefilterdeFinderTest {

    private static final String ENTITY_PAKKET = "nl.rijksoverheid.moz.entity..";
    private static final String VERWIJDERBARE_ENTITEIT = "nl.rijksoverheid.moz.entity.VerwijderbareEntiteit";
    private static final String RETENTIE_SCHEDULER = "nl.rijksoverheid.moz.job.RetentieScheduler";
    private static final String PARTIJ_SERVICE = "nl.rijksoverheid.moz.services.PartijService";

    // Afgeleid van VerwijderbareEntiteit i.p.v. een losse, hand-onderhouden lijst: een vijfde
    // soft-deletable entiteit valt hiermee automatisch onder de regel zodra ze de basisklasse
    // extendt, zonder dat iemand deze test hoeft bij te werken.
    private static final DescribedPredicate<JavaClass> IS_ONGEFILTERDE_ENTITEIT =
            assignableTo(VERWIJDERBARE_ENTITEIT).and(not(DescribedPredicate.describe(
                    "VerwijderbareEntiteit zelf", javaClass -> javaClass.getFullName().equals(VERWIJDERBARE_ENTITEIT))));

    // Alleen de Panache-overloads die de HQL als eerste parameter nemen; zie de klasse-javadoc.
    private static final DescribedPredicate<JavaAccess<?>> DOEL_IS_PANACHE_QUERY =
            DescribedPredicate.describe("een Panache-queryaanroep met een HQL-string",
                    toegang -> toegang.getTarget() instanceof MethodCallTarget doel
                            && doel.getName().matches("find|list|count|stream")
                            && !doel.getRawParameterTypes().isEmpty()
                            && doel.getRawParameterTypes().get(0).isEquivalentTo(String.class));

    private static final DescribedPredicate<JavaAccess<?>> DOEL_IS_ONGEFILTERDE_FINDER =
            target(owner(IS_ONGEFILTERDE_ENTITEIT))
                    .and(target(nameMatching("findById|findByIdOptional|findByIds|findAll|listAll|streamAll"
                            + "|deleteAll|deleteById|update|delete|getIdentificaties"))
                            .or(DOEL_IS_PANACHE_QUERY));

    // (klasse, methode) waarvoor de regel niet geldt; zie de klasse-javadoc voor de motivatie per
    // aanroep. Als set i.p.v. losse predikaten zodat een zesde uitzondering één regel kost.
    private static final Set<List<String>> UITZONDERINGEN = Set.of(
            List.of(RETENTIE_SCHEDULER, "cascadeDeleteLegePartijen"),
            List.of(RETENTIE_SCHEDULER, "verwijderInactieveVoorkeuren"),
            List.of(RETENTIE_SCHEDULER, "verwijderInactieveContactgegevens"),
            List.of(PARTIJ_SERVICE, "deleteLegePartij"),
            List.of(PARTIJ_SERVICE, "getPartijResponseBulk"),
            List.of(PARTIJ_SERVICE, "findFilteredContactgegevens"),
            List.of(PARTIJ_SERVICE, "findFilteredVoorkeuren"));

    private static final DescribedPredicate<JavaAccess<?>> IS_UITZONDERING =
            DescribedPredicate.describe("een expliciet toegestane aanroep",
                    toegang -> UITZONDERINGEN.contains(List.of(
                            toegang.getOrigin().getOwner().getFullName(),
                            toegang.getOrigin().getName())));

    private static final ArchRule REGEL = noClasses()
            .that().resideOutsideOfPackage(ENTITY_PAKKET)
            .should().accessTargetWhere(DOEL_IS_ONGEFILTERDE_FINDER.and(not(IS_UITZONDERING)))
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
     * Losse test van de getIdentificaties-naam in DOEL_IS_ONGEFILTERDE_FINDER:
     * regelDetecteertEenEchteOvertreding alleen bewijst niet dat déze naam ook echt in de regex
     * zit — een tikfout daarin zou door die test heen glippen zolang findById nog werkt.
     */
    @Test
    void regelDetecteertEenOngefilterdeGetterOvertreding() {
        JavaClasses klassen = new ClassFileImporter().importClasses(OvertrederViaGetter.class);

        Assertions.assertThrows(AssertionError.class, () -> REGEL.check(klassen),
                "de regel hoort OvertrederViaGetter.lees() te vlaggen");
    }

    /**
     * Losse test van update/delete in DOEL_IS_ONGEFILTERDE_FINDER — het mechanisme dat een soft
     * deleted rij zou kunnen resurrecten (bv. {@code Contactgegeven.update("verwijderdOp = null
     * WHERE id = ?1", id)}) en dus compileert zonder deze regel.
     */
    @Test
    void regelDetecteertEenOngefilterdeUpdateOvertreding() {
        JavaClasses klassen = new ClassFileImporter().importClasses(OvertrederViaUpdate.class);

        Assertions.assertThrows(AssertionError.class, () -> REGEL.check(klassen),
                "de regel hoort OvertrederViaUpdate.verwijder() te vlaggen");
    }

    /**
     * Losse test van DOEL_IS_PANACHE_QUERY: een eigen HQL-find zonder filter compileert net zo
     * goed als één mét, en zou zonder deze regel alleen door code review te vangen zijn.
     */
    @Test
    void regelDetecteertEenOngefilterdeFindOvertreding() {
        JavaClasses klassen = new ClassFileImporter().importClasses(OvertrederViaFind.class);

        Assertions.assertThrows(AssertionError.class, () -> REGEL.check(klassen),
                "de regel hoort OvertrederViaFind.lees() te vlaggen");
    }

    /**
     * Tegenhanger van OvertrederViaFind: de gefilterde static van de entiteit zelf heet ook find,
     * maar mag niet gevlagd worden — anders zou elke correcte aanroep in de servicelaag een
     * uitzondering nodig hebben en zou de regel niets meer betekenen.
     */
    @Test
    void regelVlagtDeGefilterdeEntityFinderNiet() {
        JavaClasses klassen = new ClassFileImporter().importClasses(GebruiktGefilterdeFinder.class);

        Assertions.assertDoesNotThrow(() -> REGEL.check(klassen),
                "de gefilterde find(partij, id) mag niet als overtreding gelden");
    }

    static class Overtreder {
        Voorkeur lees(UUID id) {
            return Voorkeur.findById(id);
        }
    }

    static class OvertrederViaGetter {
        List<Identificatie> lees(Partij partij) {
            return partij.getIdentificaties();
        }
    }

    static class OvertrederViaUpdate {
        long verwijder(UUID id) {
            return Contactgegeven.update("verwijderdOp = null WHERE id = ?1", id);
        }
    }

    static class OvertrederViaFind {
        List<Contactgegeven> lees(UUID id) {
            return Contactgegeven.list("id = ?1", id);
        }
    }

    static class GebruiktGefilterdeFinder {
        Contactgegeven lees(Partij partij, UUID id) {
            return Contactgegeven.find(partij, id);
        }
    }
}
