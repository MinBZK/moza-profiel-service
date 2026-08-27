package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code @ValidIdentificatieNummer} is een class-level constraint met
 * {@code ConstraintValidator<ValidIdentificatieNummer, HeeftIdentificatie>}. Een schema krijgt
 * hem daarom via twee vendor-extensies in het contract, en die horen onlosmakelijk bij elkaar:
 *
 * <ul>
 *   <li>Alleen {@code x-class-extra-annotation} en niet {@code x-implements}: Hibernate Validator
 *       vindt geen toepasbare validator en gooit bij de eerste validatie van dat type een
 *       {@code UnexpectedTypeException}. Dat is een {@code ValidationException}, en Quarkus'
 *       eigen {@code ExceptionMapper<ValidationException>} wint van onze catch-all — de
 *       aanroeper krijgt dus een kale 500 zonder problem-body, waar een 400 hoort. Het gebeurt
 *       lazy, bij de eerste validatie van dat type, dus build en deploy blijven groen.</li>
 *   <li>Alleen {@code x-implements} en niet de annotatie: er wordt stilzwijgend niets
 *       gevalideerd.</li>
 * </ul>
 *
 * <p>De test pint vast wélk schema de elfproef draagt, niet alleen hoevéél er zijn: verhuizen naar
 * een ander schema is ook een gedragswijziging. Uitbreiden hoort een bewuste bewerking van
 * {@link #DRAGERS} te zijn.
 *
 * <p>De sweep kijkt alleen naar top-level {@code components/schemas}; inline sub-schema's vallen
 * buiten beeld. Hij leest het contract zelf en niet het gepubliceerde document, omdat het contract
 * de codegen voedt.
 */
class ValidatieExtensiesTest {

    private static final String ANNOTATIE = "nl.rijksoverheid.moz.validation.ValidIdentificatieNummer";
    private static final String INTERFACE = "nl.rijksoverheid.moz.validation.HeeftIdentificatie";

    /** De schema's die de elfproef horen te dragen. Volgorde doet niet ter zake. */
    private static final List<String> DRAGERS = List.of("EmailVerificatieRequest");

    private static final Pattern DRAGER =
            Pattern.compile("\"\\s*@" + Pattern.quote(ANNOTATIE) + "\\s*[\"(]");

    @Test
    void annotatieEnInterfaceStaanAltijdSamen() throws Exception {
        JsonNode schemas;

        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            schemas = new ObjectMapper(new YAMLFactory()).readTree(in).path("components").path("schemas");
        }

        Assertions.assertTrue(schemas.isObject() && !schemas.isEmpty(),
                "components/schemas ontbreekt of is geen object");

        List<String> bevindingen = new ArrayList<>();
        List<String> dragers = new ArrayList<>();
        var namen = schemas.fieldNames();

        while (namen.hasNext()) {
            String naam = namen.next();
            JsonNode schema = schemas.get(naam);

            // toString() op beide: x-implements is per definitie een lijst, x-class-extra-annotation
            // een enkele string. Symmetrisch lezen houdt de twee takken gelijk en blijft werken als
            // een van de vormen ooit verandert; asText() geeft op een lijst leeg terug en zou dan
            // precies het omgekeerde melden van wat er aan de hand is.
            // De annotatiekant wordt met een expressie gelezen, de interfacekant met een simpele
            // vergelijking; zie draagtAnnotatie hieronder voor waarom de vorm daar losser moet
            // zijn dan hier.
            boolean heeftAnnotatie = draagtAnnotatie(schema.path("x-class-extra-annotation").toString());
            boolean heeftInterface = schema.path("x-implements").toString()
                    .contains("\"" + INTERFACE + "\"");

            if (heeftAnnotatie && heeftInterface) {
                dragers.add(naam);
            }

            if (heeftAnnotatie && !heeftInterface) {
                bevindingen.add(naam + " draagt @ValidIdentificatieNummer maar implementeert"
                        + " HeeftIdentificatie niet; dat levert bij de eerste validatie een 500 op"
                        + " in plaats van een 400");
            }

            if (heeftInterface && !heeftAnnotatie) {
                bevindingen.add(naam + " implementeert HeeftIdentificatie maar draagt"
                        + " @ValidIdentificatieNummer niet; er wordt dan stilzwijgend niets gevalideerd");
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
        dragers.sort(String::compareTo);
        List<String> verwacht = new ArrayList<>(DRAGERS);
        verwacht.sort(String::compareTo);

        Assertions.assertEquals(verwacht, dragers,
                "De elfproef hoort precies op " + DRAGERS + " te staan. Verhuist of verdwijnt hij,"
                        + " dan is dat een gedragswijziging aan de buitenkant.");
    }

    /**
     * De generator neemt de waarde letterlijk over, dus witruimte ervoor is nog steeds een
     * actieve constraint en {@code "// @...Nummer"} juist niet. Erna mag een haakje staan
     * ({@code (groups = {})}) of het sluitende aanhalingsteken; dat laatste sluit een langere
     * naam met dezelfde prefix uit.
     */
    private static boolean draagtAnnotatie(String ruweWaarde) {
        return DRAGER.matcher(ruweWaarde).find();
    }
}
