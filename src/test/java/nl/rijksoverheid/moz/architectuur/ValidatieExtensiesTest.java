package nl.rijksoverheid.moz.architectuur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code @ValidIdentificatieNummer} en {@code @ValidEmailWaarde} zijn class-level constraints met
 * een {@code ConstraintValidator} op een interface. Een schema krijgt zo'n constraint daarom via
 * twee vendor-extensies in het contract, en die horen onlosmakelijk bij elkaar:
 *
 * <ul>
 *   <li>Alleen {@code x-class-extra-annotation} en niet {@code x-implements}: Hibernate Validator
 *       vindt geen toepasbare validator en gooit bij de eerste validatie van dat type een
 *       {@code UnexpectedTypeException} ({@code HV000030}). De aanroeper krijgt dan een 500 waar
 *       een 400 hoort. Het gebeurt lazy, bij de eerste validatie, dus build en deploy blijven
 *       groen.</li>
 *   <li>Alleen {@code x-implements} en niet de annotatie: er wordt stilzwijgend niets
 *       gevalideerd.</li>
 * </ul>
 *
 * <p>De test pint per constraint vast wélke schema's hem dragen, niet alleen hoevéél er zijn:
 * verhuizen naar een ander schema is ook een gedragswijziging. Uitbreiden hoort een bewuste
 * bewerking van {@link Constraint} te zijn.
 *
 * <p>De sweep kijkt alleen naar top-level {@code components/schemas}; inline sub-schema's vallen
 * buiten beeld. Hij leest het contract zelf en niet het gepubliceerde document, omdat het contract
 * de codegen voedt.
 */
class ValidatieExtensiesTest {

    /** De class-level constraints in het contract, met de schema's die ze horen te dragen. */
    enum Constraint {
        /** De elfproef op BSN/RSIN en de lengtecontrole op KVK. */
        IDENTIFICATIE_NUMMER(
                "nl.rijksoverheid.moz.validation.ValidIdentificatieNummer",
                "nl.rijksoverheid.moz.validation.HeeftIdentificatie",
                List.of("EmailVerificatieRequest")),
        /** Het e-mailformaat op waarde zodra type Email is; MinBZK/MijnOverheidZakelijk#766. */
        EMAIL_WAARDE(
                "nl.rijksoverheid.moz.validation.ValidEmailWaarde",
                "nl.rijksoverheid.moz.validation.HeeftContactWaarde",
                List.of("ContactgegevenRequest", "ContactgegevenUpdateRequest"));

        private final String annotatie;
        private final String interfaceNaam;
        private final List<String> dragers;

        Constraint(String annotatie, String interfaceNaam, List<String> dragers) {
            this.annotatie = annotatie;
            this.interfaceNaam = interfaceNaam;
            this.dragers = dragers;
        }
    }

    @ParameterizedTest
    @EnumSource(Constraint.class)
    void annotatieEnInterfaceStaanAltijdSamen(Constraint constraint) throws Exception {
        JsonNode schemas = schemas();

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
            boolean heeftAnnotatie =
                    draagtAnnotatie(schema.path("x-class-extra-annotation").toString(), constraint.annotatie);
            boolean heeftInterface = schema.path("x-implements").toString()
                    .contains("\"" + constraint.interfaceNaam + "\"");

            if (heeftAnnotatie && heeftInterface) {
                dragers.add(naam);
            }

            if (heeftAnnotatie && !heeftInterface) {
                bevindingen.add(naam + " draagt @" + constraint.annotatie + " maar implementeert"
                        + " de bijbehorende interface niet; dat levert bij de eerste validatie een 500"
                        + " op in plaats van een 400");
            }

            if (heeftInterface && !heeftAnnotatie) {
                bevindingen.add(naam + " implementeert " + constraint.interfaceNaam + " maar draagt"
                        + " de bijbehorende annotatie niet; er wordt dan stilzwijgend niets gevalideerd");
            }
        }

        Assertions.assertTrue(bevindingen.isEmpty(), String.join("\n", bevindingen));
        dragers.sort(String::compareTo);
        List<String> verwacht = new ArrayList<>(constraint.dragers);
        verwacht.sort(String::compareTo);

        Assertions.assertEquals(verwacht, dragers,
                constraint.annotatie + " hoort precies op " + constraint.dragers + " te staan."
                        + " Verhuist of verdwijnt hij, dan is dat een gedragswijziging aan de"
                        + " buitenkant.");
    }

    /**
     * De {@code if}/{@code then} die de e-mailregel beschrijft is de plek waar een consumer hem
     * machineleesbaar vindt. {@link ContractHandhavingTest} toetst wat hij afdwingt; deze test pint
     * de vorm vast, want de generator maakt er geen constraint van en een verhuizing naar een ander
     * schema zou daar onopgemerkt blijven.
     */
    @ParameterizedTest
    @EnumSource(value = Constraint.class, names = "EMAIL_WAARDE")
    void deEmailregelStaatOokAlsIfThenInHetContract(Constraint constraint) throws Exception {
        JsonNode schemas = schemas();

        for (String naam : constraint.dragers) {
            JsonNode schema = schemas.path(naam);

            Assertions.assertEquals("Email", schema.path("if").path("properties").path("type").path("const").asText(),
                    naam + " hoort de conditie op type Email te dragen");
            Assertions.assertEquals("email", schema.path("then").path("properties").path("waarde").path("format").asText(),
                    naam + " hoort in de then-tak format email op waarde te zetten");
        }
    }

    /**
     * De veldroute: {@code email} is niet polymorf, dus daar staat {@code @Email} rechtstreeks op
     * het veld. De generator vertaalt {@code format} niet, dus zonder de vendor-extensie valideert
     * de server niets terwijl het contract de regel wél belooft — en geen enkele andere test in
     * deze klasse kijkt naar velden.
     */
    @ParameterizedTest
    @ValueSource(strings = {"EmailVerificatieRequest", "EmailVerificatieCodeAanvraagRequest"})
    void hetEmailveldDraagtFormatEnDeAnnotatie(String schemaNaam) throws Exception {
        JsonNode veld = schemas().path(schemaNaam).path("properties").path("email");

        Assertions.assertEquals("email", veld.path("format").asText(),
                schemaNaam + ".email hoort format email te dragen voor consumers");
        Assertions.assertEquals("@jakarta.validation.constraints.Email",
                veld.path("x-field-extra-annotation").asText(),
                schemaNaam + ".email hoort de constraint via de vendor-extensie te dragen; format"
                        + " alleen levert geen validatie op de server op");
    }

    private JsonNode schemas() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            return new ObjectMapper(new YAMLFactory()).readTree(in).path("components").path("schemas");
        }
    }

    /**
     * De generator neemt de waarde letterlijk over, dus witruimte ervoor is nog steeds een
     * actieve constraint en {@code "// @...Nummer"} juist niet. Erna mag een haakje staan
     * ({@code (groups = {})}) of het sluitende aanhalingsteken; dat laatste sluit een langere
     * naam met dezelfde prefix uit.
     */
    private static boolean draagtAnnotatie(String ruweWaarde, String annotatie) {
        return Pattern.compile("\"\\s*@" + Pattern.quote(annotatie) + "\\s*[\"(]")
                .matcher(ruweWaarde)
                .find();
    }
}
