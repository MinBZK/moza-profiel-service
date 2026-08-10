package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Houdt de update-schema's gelijk aan hun create-tegenhanger.
 *
 * <p>Op {@code main} was dit een taalgarantie: {@code ContactgegevenUpdateRequest extends
 * ContactgegevenRequest}. In het contract zijn het twee losse, volledig uitgeschreven schema's.
 * Dat is met opzet — de oude subtypering was onjuist, want een update-request draagt een
 * verplichte {@code id} en is dus geen geldig create-request, terwijl hij wel als zodanig kon
 * worden doorgegeven — maar er staat nu niets meer tegenover de duplicatie. Een veld toevoegen
 * of een pattern aanscherpen op het ene schema laat het andere stilzwijgend achter.
 *
 * <p>De test eist niet dat de twee gelijk zijn, maar dat het verschil precies de bekende
 * uitbreiding is: elke property van het create-schema komt letterlijk terug in het
 * update-schema, en wat er extra in staat is opgesomd in {@link #schemaParen()}. Zo blijft een
 * bewuste uitbreiding een bewerking van deze lijst in plaats van een stille afwijking.
 *
 * <p>{@code allOf} zou de duplicatie in de YAML weghalen zonder de subtypering terug te brengen
 * — {@code jaxrs-spec} vlakt {@code allOf} af, dus de gegenereerde klassen blijven los van
 * elkaar. Zolang dat niet gebeurd is, doet deze test het werk.
 */
class UpdateSchemaPariteitTest {

    private static Stream<Arguments> schemaParen() {
        return Stream.of(
                Arguments.of("ContactgegevenRequest", "ContactgegevenUpdateRequest",
                        Set.of("id", "isDefault")),
                Arguments.of("VoorkeurRequest", "VoorkeurUpdateRequest",
                        Set.of("id")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("schemaParen")
    void updateSchemaHerhaaltHetCreateSchemaEnVoegtAlleenHetBekendeToe(
            String createNaam, String updateNaam, Set<String> verwachteExtras) throws Exception {

        JsonNode schemas;

        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            schemas = new ObjectMapper(new YAMLFactory()).readTree(in).path("components").path("schemas");
        }

        JsonNode create = schemas.path(createNaam).path("properties");
        JsonNode update = schemas.path(updateNaam).path("properties");

        Assertions.assertTrue(create.isObject() && !create.isEmpty(),
                createNaam + " ontbreekt in het contract of heeft geen properties");
        Assertions.assertTrue(update.isObject() && !update.isEmpty(),
                updateNaam + " ontbreekt in het contract of heeft geen properties");

        List<String> bevindingen = new ArrayList<>();
        var createVelden = create.fieldNames();

        while (createVelden.hasNext()) {
            String veld = createVelden.next();

            if (!update.has(veld)) {
                bevindingen.add(updateNaam + " mist de property '" + veld + "' die "
                        + createNaam + " wel heeft");
                continue;
            }

            if (!create.get(veld).equals(update.get(veld))) {
                bevindingen.add("'" + veld + "' is anders gedefinieerd in " + updateNaam
                        + " dan in " + createNaam + ": " + update.get(veld) + " tegenover "
                        + create.get(veld));
            }
        }

        Set<String> extras = new TreeSet<>();
        update.fieldNames().forEachRemaining(veld -> {
            if (!create.has(veld)) {
                extras.add(veld);
            }
        });

        Assertions.assertEquals(new TreeSet<>(verwachteExtras), extras,
                updateNaam + " hoort naast de properties van " + createNaam + " precies "
                        + verwachteExtras + " te dragen");

        // Wat bij het aanmaken verplicht is, blijft dat bij het bijwerken: het update-request
        // vervangt de waarden en stuurt dus een volledig gegeven mee, geen patch.
        Set<String> createVerplicht = verplichteVelden(schemas.path(createNaam));
        Set<String> updateVerplicht = verplichteVelden(schemas.path(updateNaam));

        for (String veld : createVerplicht) {
            if (!updateVerplicht.contains(veld)) {
                bevindingen.add("'" + veld + "' is verplicht in " + createNaam + " maar niet in "
                        + updateNaam);
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }

    private static Set<String> verplichteVelden(JsonNode schema) {
        Set<String> velden = new TreeSet<>();
        schema.path("required").forEach(veld -> velden.add(veld.asText()));

        return velden;
    }
}
