package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bewaakt dat de enum-waarden in het contract gelijk zijn aan die in de domeintypes.
 *
 * <p>De {@code schemaMappings} in {@code pom.xml} laten de generator deze drie schema's
 * overslaan en verwijzen naar de bestaande enums in {@code nl.rijksoverheid.moz.common}. Dat
 * scheelt een conversielaag, maar het haalt tegelijk de enige koppeling weg die er tussen de
 * twee lijsten was: niets in de build vergelijkt ze nog, in geen van beide richtingen.
 *
 * <ul>
 *   <li>Een waarde die alleen in Java staat wordt door Jackson gewoon geaccepteerd — er is
 *       server-side geen schemavalidatie op de request-body — terwijl het gepubliceerde contract
 *       hem niet noemt.</li>
 *   <li>Een waarde die alleen in het contract staat wordt geadverteerd, waarna Jackson hem bij
 *       de eerste aanroep afwijst met een 400.</li>
 * </ul>
 *
 * <p>Beide gaan vandaag stil voorbij. {@code OpenApiContractDriftTest} helpt hier niet: die
 * vergelijkt het contract met het gepubliceerde document, en dat is sinds
 * {@code mp.openapi.scan.disable=true} hetzelfde bestand.
 *
 * <p>{@code IdentificatieType} is toevallig deels beschermd doordat de {@code switch} in
 * {@code IdentificatieNummerValidator} geen {@code default} heeft en dus niet meer compileert
 * zodra er een waarde bij komt. Voor de andere twee bestaat zo'n vangnet niet, en op toeval
 * hoort deze eigenschap sowieso niet te rusten.
 */
class EnumPariteitTest {

    private static Stream<Arguments> enums() {
        return Stream.of(
                Arguments.of("IdentificatieType", namen(IdentificatieType.values())),
                Arguments.of("ContactType", namen(ContactType.values())),
                Arguments.of("VoorkeurType", namen(VoorkeurType.values())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enums")
    void contractEnDomeintypeKennenDezelfdeWaarden(String schemaNaam, List<String> javaWaarden) throws Exception {
        JsonNode schemas;

        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            schemas = new ObjectMapper(new YAMLFactory()).readTree(in).path("components").path("schemas");
        }

        JsonNode enumKnoop = schemas.path(schemaNaam).path("enum");

        Assertions.assertTrue(enumKnoop.isArray() && !enumKnoop.isEmpty(),
                schemaNaam + " ontbreekt in het contract of is geen enum. Het staat in"
                        + " schemaMappings, dus de generator gaat ervan uit dat het schema bestaat"
                        + " en op het domeintype wijst.");

        List<String> contractWaarden = new ArrayList<>();
        enumKnoop.forEach(waarde -> contractWaarden.add(waarde.asText()));

        // Op volgorde vergelijken: die is voor de werking betekenisloos, maar het contract en de
        // enum staan vandaag in dezelfde volgorde en dat is de goedkoopste manier om ze naast
        // elkaar leesbaar te houden. Klopt de volgorde niet meer, dan is dat een bewuste
        // aanpassing van een van de twee.
        Assertions.assertEquals(javaWaarden, contractWaarden,
                schemaNaam + ": de waarden in META-INF/openapi.yaml wijken af van"
                        + " nl.rijksoverheid.moz.common." + schemaNaam + ". Omdat schemaMappings dit"
                        + " schema niet laat genereren, merkt de build het verschil verder nergens.");
    }

    private static List<String> namen(Enum<?>[] waarden) {
        return Arrays.stream(waarden).map(Enum::name).toList();
    }
}
