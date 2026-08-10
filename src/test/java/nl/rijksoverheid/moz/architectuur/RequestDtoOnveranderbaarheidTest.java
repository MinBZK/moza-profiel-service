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
 * <p>De gegenereerde DTO's kunnen die eigenschap niet zelf afdwingen: {@code jaxrs-spec}
 * emitteert in openapi-generator 7.10.0 onvoorwaardelijk setters (zowel {@code setX} als de
 * fluent variant {@code x}) en kent geen schakelaar om er records of onveranderbare klassen
 * van te maken; {@code generateBuilders} zet er een builder naast in plaats van de setters
 * te vervangen. Zonder eigen mustache-template — met het onderhoud dat daarbij hoort — is
 * een architectuurregel de manier om de eigenschap vast te leggen. Bij een upgrade van de
 * generator is die aanname het narekenen waard.
 *
 * <p>De regels staan in {@link RequestOnveranderbaarheidRegels}; hun dekking wordt bewezen in
 * {@link RegelDekkingTest}. Deze klasse past ze alleen toe op de productiecode.
 *
 * <p>Drie uitzonderingen, alle drie bewust:
 *
 * <ul>
 *   <li>De {@code *Response}-typen vallen erbuiten omdat MapStruct ze via setters vult;
 *       de gegenereerde mapper-implementatie zou de regel per definitie overtreden.</li>
 *   <li>Tests vallen erbuiten omdat die hun request-bodies juist mét setters opbouwen.</li>
 *   <li>De gegenereerde modellen zelf vallen erbuiten. Sinds {@code accessTargetWhere}
 *       tellen ook veldtoegangen mee, en vrijwel elke methode van zo'n model leest of schrijft
 *       zijn privévelden rechtstreeks, tot en met de getters. Daar komt bij dat
 *       {@code toString} de private {@code toIndentedString} aanroept.</li>
 * </ul>
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
