package nl.rijksoverheid.moz.architectuur;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Een binnenkomend request is niets anders dan wat de aanroeper heeft gestuurd. Wie het
 * onderweg aanpast, laat de rest van de aanroepketen naar iets kijken dat nooit over de
 * lijn is gekomen — en dat is niet meer te herleiden uit het request zelf.
 *
 * <p>De gegenereerde DTO's kunnen die eigenschap niet zelf afdwingen; een architectuurregel legt
 * haar daarom vast. De regels staan in {@link RequestOnveranderbaarheidRegels}, inclusief wat ze
 * buiten beschouwing laten; hun dekking wordt bewezen in {@link RegelDekkingTest}. Deze klasse
 * past ze alleen toe op de productiecode.
 *
 * <p>Eén gat dat de regels niet kunnen dichten: een lijst-getter geeft de levende collectie
 * terug, dus {@code request.getIdentificaties().clear()} muteert het request terwijl het
 * aanroepdoel {@code java.util.List} is en daarmee buiten de eigenaarsfilter valt. Groen
 * betekent dus "niet via de DTO gemuteerd", niet "onveranderlijk". Wie een lijst uit een
 * request bewerkt, kopieert hem eerst.
 */
class RequestDtoOnveranderbaarheidTest {

    private static JavaClasses productieklassen() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("nl.rijksoverheid.moz");
    }

    @Test
    void productiecodeMuteertGeenBinnenkomendRequest() {
        RequestOnveranderbaarheidRegels.GEEN_MUTERENDE_NAAM.check(productieklassen());
    }

    @Test
    void productiecodeRoeptGeenParameterdragendeMethodeAanOpEenRequest() {
        RequestOnveranderbaarheidRegels.GEEN_AANROEP_MET_PARAMETERS.check(productieklassen());
    }
}
