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
 *       {@code UnexpectedTypeException}. Die valt buiten de violation-afhandeling en komt via de
 *       catch-all uit op een 500, waar een 400 hoort. Het gebeurt lazy, dus de build en de
 *       deploy blijven groen.</li>
 *   <li>Alleen {@code x-implements} en niet de annotatie: er wordt stilzwijgend niets
 *       gevalideerd, precies het gat dat MinBZK/MijnOverheidZakelijk#923 beschrijft.</li>
 * </ul>
 *
 * <p>Deze test leest het contract rechtstreeks — niet het gepubliceerde document — omdat het de
 * bron is die de codegen voedt. Dat de twee gelijk zijn bewaakt {@code OpenApiContractDriftTest}.
 */
class ValidatieExtensiesTest {

    private static final String ANNOTATIE = "ValidIdentificatieNummer";
    private static final String INTERFACE = "HeeftIdentificatie";

    @Test
    void annotatieEnInterfaceStaanAltijdSamen() throws Exception {
        JsonNode schemas;

        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            schemas = new ObjectMapper(new YAMLFactory()).readTree(in).path("components").path("schemas");
        }

        Assertions.assertFalse(schemas.isMissingNode() || schemas.isEmpty(),
                "Geen schema's gevonden om te controleren");

        List<String> bevindingen = new ArrayList<>();
        var namen = schemas.fieldNames();

        while (namen.hasNext()) {
            String naam = namen.next();
            JsonNode schema = schemas.get(naam);

            boolean heeftAnnotatie = schema.path("x-class-extra-annotation").asText("").contains(ANNOTATIE);
            boolean heeftInterface = schema.path("x-implements").toString().contains(INTERFACE);

            if (heeftAnnotatie && !heeftInterface) {
                bevindingen.add(naam + " draagt @" + ANNOTATIE + " maar implementeert " + INTERFACE
                        + " niet; dat levert bij de eerste validatie een 500 op in plaats van een 400");
            }

            if (heeftInterface && !heeftAnnotatie) {
                bevindingen.add(naam + " implementeert " + INTERFACE + " maar draagt @" + ANNOTATIE
                        + " niet; er wordt dan stilzwijgend niets gevalideerd");
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
    }
}
