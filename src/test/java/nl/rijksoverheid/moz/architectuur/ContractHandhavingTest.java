package nl.rijksoverheid.moz.architectuur;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Legt vast dat het contract de regels die het declareert ook werkelijk afdwingt, door bodies
 * rechtstreeks tegen het document te valideren.
 *
 * <p>{@code BlancoWaardenIntegrationTest} kan dit niet: die hangt de validatiefilter bewust alleen
 * aan contract-geldige requests, omdat de filter client-side keurt. De positieve tegenhangers
 * horen erbij, anders ziet een contract dat álles afwijst er net zo groen uit.
 */
class ContractHandhavingTest {

    private static final String PAD = "/api/profielservice/v1/contactgegeven";

    private static OpenApiInteractionValidator validator;

    @BeforeAll
    static void laadContract() throws Exception {
        try (InputStream in = ContractHandhavingTest.class.getResourceAsStream("/META-INF/openapi.yaml")) {
            Assertions.assertNotNull(in, "META-INF/openapi.yaml hoort op het classpath te staan");
            validator = OpenApiInteractionValidator
                    .createForInlineApiSpecification(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .build();
        }
    }

    @Test
    void blancoScopeWordtDoorHetContractAfgewezen() {
        Assertions.assertTrue(heeftFouten(contactgegeven("\"scope\":{\"dienstverlenerNaam\":\"   \"}")),
                "Het contract hoort een blanco dienstverlenerNaam af te wijzen via de pattern"
                        + " op ScopeRequest");
    }

    @Test
    void onbekendVeldInScopeWordtDoorHetContractAfgewezen() {
        Assertions.assertTrue(heeftFouten(contactgegeven("\"scope\":{\"onbekendVeld\":1}")),
                "ScopeRequest hoort geen onbekende velden toe te laten");
    }

    @Test
    void geldigeScopeWordtGeaccepteerd() {
        Assertions.assertFalse(
                heeftFouten(contactgegeven("\"scope\":{\"dienstverlenerNaam\":\"Gemeente Amsterdam\"}")),
                "Een gevulde scope hoort gewoon door het contract te komen");
    }

    /** Weglaten mag: scope is optioneel. */
    @Test
    void zonderScopeWordtGeaccepteerd() {
        Assertions.assertFalse(heeftFouten(contactgegeven(null)),
                "scope is optioneel");
    }

    /**
     * De contractvalidatie evalueert de {@code if}/{@code then} echt; de server dwingt de regel
     * apart af, want de generator maakt er geen Jakarta-constraint van
     * ({@code EmailFormaatIntegrationTest}).
     */
    @Test
    void ongeldigEmailadresWordtDoorHetContractAfgewezen() {
        Assertions.assertTrue(heeftFouten(contactgegeven("Email", "geen adres")),
                "Bij type Email hoort het contract een waarde af te wijzen die geen e-mailadres is");
    }

    @Test
    void geldigEmailadresWordtDoorHetContractGeaccepteerd() {
        Assertions.assertFalse(heeftFouten(contactgegeven("Email", "jan@rijksoverheid.nl")),
                "Een geldig e-mailadres hoort gewoon door het contract te komen");
    }

    /**
     * De tegenhanger die de regel voorwaardelijk houdt. Zou de e-mailregel ooit als {@code format}
     * op {@code waarde} zelf belanden in plaats van in de {@code then}-tak, dan strandt het
     * telefoonnummer hieronder — precies de valse afwijzing die #766 wil vermijden. De blanco
     * waarde erna laat zien wat het contract wél voor elk type afdwingt.
     */
    @Test
    void nietEmailWaardeWordtDoorHetContractGeaccepteerd() {
        Assertions.assertFalse(heeftFouten(contactgegeven("Telefoonnummer", "0612345678")),
                "Een telefoonnummer hoort niet aan de e-mailregel te worden gehouden");
        Assertions.assertTrue(heeftFouten(contactgegeven("Telefoonnummer", "   ")),
                "De pattern op waarde geldt wel voor elk type");
    }

    private static String contactgegeven(String extraVeld) {
        String basis = "\"identificatieType\":\"BSN\",\"identificatieNummer\":\"111111104\","
                + "\"type\":\"Email\",\"waarde\":\"test@example.com\"";

        return "{" + basis + (extraVeld == null ? "" : "," + extraVeld) + "}";
    }

    /**
     * De contractkant van de divergentie die {@code EmailFormaatIntegrationTest} aan de serverkant
     * vastlegt: {@code format: email} is strenger dan Jakarta {@code @Email}. Het gepubliceerde
     * contract wijst dus adressen af die de service zou opslaan. Alle divergentie loopt deze kant
     * op; klapt dat ooit om, dan belooft het contract minder dan de server afdwingt en valt deze
     * test om.
     */
    @Test
    void hetContractIsStrengerDanDeServer() {
        Assertions.assertTrue(heeftFouten(contactgegeven("Email", "jan@123.45.67.89")),
                "Het contract hoort een bare-IPv4-domein af te wijzen; de server accepteert het");
        Assertions.assertTrue(heeftFouten(contactgegeven("Email", "a@b.c")),
                "Het contract hoort een TLD van één teken af te wijzen; de server accepteert het");
    }

    /**
     * {@code ContactgegevenUpdateRequest} draagt dezelfde {@code if}/{@code then}, en die moet ook
     * op de PUT-route gelden. Zonder deze twee gevallen is dat schema alleen structureel gepind.
     */
    @Test
    void deEmailregelGeldtOokOpDeUpdateRoute() {
        Assertions.assertTrue(heeftFoutenBijUpdate("Email", "geen adres"),
                "Bij type Email hoort het contract ook op de PUT een ongeldig adres af te wijzen");
        Assertions.assertFalse(heeftFoutenBijUpdate("Email", "jan@rijksoverheid.nl"),
                "Een geldig adres hoort ook op de PUT door het contract te komen");
        Assertions.assertFalse(heeftFoutenBijUpdate("Telefoonnummer", "0612345678"),
                "Een telefoonnummer hoort ook op de PUT niet aan de e-mailregel te worden gehouden");
        Assertions.assertFalse(heeftFoutenBijUpdate("ApplicatieId", "geen adres"),
                "Een applicatie-ID evenmin; server-side dekt @EnumSource beide types, hier niet");
    }

    private static String contactgegeven(String type, String waarde) {
        return "{\"identificatieType\":\"BSN\",\"identificatieNummer\":\"111111104\","
                + "\"type\":\"" + type + "\",\"waarde\":\"" + waarde + "\"}";
    }

    private static boolean heeftFouten(String body) {
        ValidationReport rapport = validator.validateRequest(
                SimpleRequest.Builder.post(PAD)
                        .withContentType("application/json")
                        .withBody(body)
                        .build());

        return rapport.hasErrors();
    }

    private static boolean heeftFoutenBijUpdate(String type, String waarde) {
        String body = "{\"identificatieType\":\"BSN\",\"identificatieNummer\":\"111111104\","
                + "\"id\":\"3f1a2b4c-5d6e-7f80-9a1b-2c3d4e5f6071\","
                + "\"type\":\"" + type + "\",\"waarde\":\"" + waarde + "\"}";

        ValidationReport rapport = validator.validateRequest(
                SimpleRequest.Builder.put(PAD)
                        .withContentType("application/json")
                        .withBody(body)
                        .build());

        return rapport.hasErrors();
    }
}
