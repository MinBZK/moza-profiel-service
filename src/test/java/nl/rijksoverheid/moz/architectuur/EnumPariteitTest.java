package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.common.VoorkeurType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bewaakt dat de enum-waarden in het contract gelijk zijn aan die in de domeintypes.
 *
 * <p>{@code schemaMappings} laat de generator deze schema's overslaan en naar de bestaande enums
 * verwijzen. Daarmee vergelijkt niets in de build de twee lijsten nog: een waarde die alleen in
 * Java staat wordt stil geaccepteerd, een waarde die alleen in het contract staat wordt
 * geadverteerd en bij aanroep afgewezen.
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

        // Ook op volgorde: die is functioneel betekenisloos, maar houdt de twee lijsten naast
        // elkaar leesbaar.
        Assertions.assertEquals(javaWaarden, contractWaarden,
                schemaNaam + ": de waarden in META-INF/openapi.yaml wijken af van"
                        + " nl.rijksoverheid.moz.common." + schemaNaam + ". Omdat schemaMappings dit"
                        + " schema niet laat genereren, merkt de build het verschil verder nergens.");
    }

    /**
     * Houdt {@link #enums()} gelijk aan {@code schemaMappings}, zodat een vierde enum-mapping niet
     * ongetoetst blijft. {@code isEnum()} in plaats van een pakketfilter, want de mapping wijst ook
     * naar {@code Instant}, {@code UUID} en {@code HttpProblem}.
     */
    @Test
    void elkGemaptEnumWordtGetoetst() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        Matcher blok = Pattern.compile("<schemaMappings>(.*?)</schemaMappings>", Pattern.DOTALL)
                .matcher(pom);

        Assertions.assertTrue(blok.find(), "Geen <schemaMappings> gevonden in pom.xml");

        List<String> gemapteEnums = new ArrayList<>();

        for (String mapping : blok.group(1).trim().split(",")) {
            String[] delen = mapping.trim().split("=", 2);

            if (delen.length != 2) {
                continue;
            }

            String doel = delen[1].trim();

            try {
                if (Class.forName(doel).isEnum()) {
                    gemapteEnums.add(delen[0].trim());
                }
            } catch (ClassNotFoundException nietOpClasspath) {
                Assertions.fail("schemaMappings verwijst naar " + doel + ", maar die klasse staat"
                        + " niet op het testclasspath. Klopt de naam, of hoort de dependency"
                        + " ruimer dan compile-scope te staan?");
            }
        }

        List<String> getoetst = enums().map(argumenten -> (String) argumenten.get()[0]).sorted().toList();
        gemapteEnums.sort(String::compareTo);

        Assertions.assertEquals(gemapteEnums, getoetst,
                "Elk enum-schema uit schemaMappings hoort in deze test te staan: de generator maakt"
                        + " er geen klasse voor, dus niets anders vergelijkt de waarden.");
    }

    private static List<String> namen(Enum<?>[] waarden) {
        return Arrays.stream(waarden).map(Enum::name).toList();
    }
}
