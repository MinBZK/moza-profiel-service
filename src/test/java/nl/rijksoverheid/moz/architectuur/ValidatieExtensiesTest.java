package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
 *       gevalideerd, precies het gat dat MinBZK/MijnOverheidZakelijk#923 beschrijft.</li>
 * </ul>
 *
 * <p>De test pint vast wélk schema de elfproef draagt. Alleen tellen zou groen blijven als de
 * constraint naar een ánder schema verhuist — de koppeling klopt dan nog, de plaats niet — en zou
 * bovendien nietszeggend worden zodra MinBZK/MijnOverheidZakelijk#923 een tweede drager toevoegt.
 * Uitbreiden hoort een bewuste bewerking van deze lijst te zijn.
 *
 * <p>De sweep kijkt alleen naar {@code components/schemas} op het hoogste niveau. Vandaag verwijst
 * elke requestBody daarheen. Een top-level schema dat {@code allOf} gebruikt wordt gewoon
 * meegenomen; alleen inline sub-schema's — allOf-leden en bodies die direct in een operatie
 * staan — vallen buiten beeld.
 *
 * <p>Deze test leest het contract rechtstreeks — niet het gepubliceerde document — omdat het de
 * bron is die de codegen voedt. Dat de twee gelijk zijn bewaakt {@code OpenApiContractDriftTest}.
 */
class ValidatieExtensiesTest {

    private static final String ANNOTATIE = "nl.rijksoverheid.moz.validation.ValidIdentificatieNummer";
    private static final String INTERFACE = "nl.rijksoverheid.moz.validation.HeeftIdentificatie";

    /** De schema's die de elfproef horen te dragen, in de volgorde waarin het contract ze opsomt. */
    private static final List<String> DRAGERS = List.of("EmailVerificatieRequest");

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
            boolean heeftAnnotatie = schema.path("x-class-extra-annotation").toString().contains(ANNOTATIE);
            boolean heeftInterface = schema.path("x-implements").toString().contains(INTERFACE);

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
        Assertions.assertEquals(DRAGERS, dragers,
                "De elfproef hoort precies op " + DRAGERS + " te staan. Verhuist of verdwijnt hij,"
                        + " dan is dat een gedragswijziging aan de buitenkant; zie"
                        + " MinBZK/MijnOverheidZakelijk#923");
    }
}
