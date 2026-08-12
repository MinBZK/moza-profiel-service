package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Houdt de update-schema's gelijk aan hun create-tegenhanger.
 *
 * <p>Op {@code main} was dit een taalgarantie via overerving; in het contract zijn het twee losse,
 * volledig uitgeschreven schema's zonder iets dat ze synchroon houdt. De test eist niet dat ze
 * gelijk zijn, maar dat het verschil precies de uitbreiding in {@link #schemaParen()} is.
 *
 * <p>{@code allOf} zou de duplicatie weghalen zonder de (onjuiste) subtypering terug te brengen;
 * zolang dat niet gebeurd is, doet deze test het werk.
 */
class UpdateSchemaPariteitTest {

    /**
     * De extras gaan op node-gelijkheid, zonder {@code description}. Alleen op naam pinnen zou de
     * zwakste plek van deze test leggen op de enige plek waar afwijking is toegestaan.
     */
    private static final String ID_NODE = "{\"$ref\":\"#/components/schemas/UUID\"}";

    private static final String IS_DEFAULT_NODE = "{\"type\":[\"boolean\",\"null\"]}";

    private static Stream<Arguments> schemaParen() {
        return Stream.of(
                Arguments.of("ContactgegevenRequest", "ContactgegevenUpdateRequest",
                        Map.of("id", ID_NODE, "isDefault", IS_DEFAULT_NODE), Set.of("id")),
                Arguments.of("VoorkeurRequest", "VoorkeurUpdateRequest",
                        Map.of("id", ID_NODE), Set.of("id")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("schemaParen")
    void updateSchemaHerhaaltHetCreateSchemaEnVoegtAlleenHetBekendeToe(
            String createNaam,
            String updateNaam,
            Map<String, String> verwachteExtrasMetNode,
            Set<String> verplichteExtras) throws Exception {

        Set<String> verwachteExtras = verwachteExtrasMetNode.keySet();

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

        if (!new TreeSet<>(verwachteExtras).equals(extras)) {
            bevindingen.add(updateNaam + " hoort naast de properties van " + createNaam
                    + " precies " + new TreeSet<>(verwachteExtras) + " te dragen, maar draagt "
                    + extras);
        }

        // De extras worden op node-gelijkheid vergeleken, net als de gedeelde velden hierboven,
        // maar zonder description: beschrijvende tekst mag wijzigen, de vorm niet.
        verwachteExtrasMetNode.forEach((veld, verwachteNode) -> {
            if (!update.has(veld)) {
                return;
            }

            JsonNode gevonden = zonderBeschrijving(update.get(veld));

            if (!lees(verwachteNode).equals(gevonden)) {
                bevindingen.add("'" + veld + "' in " + updateNaam + " is " + gevonden
                        + " in plaats van " + verwachteNode);
            }
        });

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

        // De reden dat deze twee schema's geen overerving meer delen is dat een update-request een
        // verplichte id draagt. Zonder deze controle stond die reden alleen in de javadoc en kon
        // 'id' uit required verdwijnen zonder dat iets omviel.
        for (String veld : verplichteExtras) {
            if (!updateVerplicht.contains(veld)) {
                bevindingen.add("'" + veld + "' hoort verplicht te zijn in " + updateNaam
                        + "; dat is de invariant waarvoor dit schema los staat van " + createNaam);
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }

    private static JsonNode lees(String json) {
        // Een lege verwachting betekende ooit "sla de vergelijking over". Die uitzondering is weg;
        // zonder deze controle zou hij stilzwijgend terugkomen als een MissingNode die nooit gelijk
        // is aan een property, met een onbegrijpelijke melding tot gevolg.
        Assertions.assertFalse(json.isBlank(),
                "Een extra hoort een verwachte node te krijgen, geen lege string");

        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception onmogelijk) {
            throw new IllegalStateException("Vaste JSON in deze test is ongeldig: " + json, onmogelijk);
        }
    }

    private static JsonNode zonderBeschrijving(JsonNode node) {
        return ((ObjectNode) node.deepCopy()).without("description");
    }

    private static Set<String> verplichteVelden(JsonNode schema) {
        Set<String> velden = new TreeSet<>();
        schema.path("required").forEach(veld -> velden.add(veld.asText()));

        return velden;
    }
}
