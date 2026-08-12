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
                "Het contract hoort een blanco dienstverlenerNaam af te wijzen; staat scope als"
                        + " anyOf met een null-tak, dan gebeurt dat niet meer");
    }

    @Test
    void onbekendVeldInScopeWordtDoorHetContractAfgewezen() {
        Assertions.assertTrue(heeftFouten(contactgegeven("\"scope\":{\"onbekendVeld\":1}")),
                "ScopeRequest hoort geen onbekende velden toe te laten");
    }

    @Test
    void ongeldigeTeVerwijderenOpWordtDoorHetContractAfgewezen() {
        Assertions.assertTrue(heeftFouten(contactgegeven("\"teVerwijderenOp\":\"geen datum\"")),
                "teVerwijderenOp hoort op format date-time te stranden");
    }

    @Test
    void geldigeScopeEnVerwijderdatumWordenGeaccepteerd() {
        Assertions.assertFalse(
                heeftFouten(contactgegeven("\"scope\":{\"dienstverlenerNaam\":\"Gemeente Amsterdam\"}")),
                "Een gevulde scope hoort gewoon door het contract te komen");
        Assertions.assertFalse(heeftFouten(contactgegeven("\"teVerwijderenOp\":\"2099-01-01T00:00:00Z\"")),
                "Een geldige verwijderdatum hoort gewoon door het contract te komen");
    }

    /** Weglaten mag: beide velden zijn optioneel. */
    @Test
    void zonderScopeEnVerwijderdatumWordtGeaccepteerd() {
        Assertions.assertFalse(heeftFouten(contactgegeven(null)),
                "scope en teVerwijderenOp zijn optioneel");
    }

    private static String contactgegeven(String extraVeld) {
        String basis = "\"identificatieType\":\"BSN\",\"identificatieNummer\":\"111111104\","
                + "\"type\":\"Email\",\"waarde\":\"test@example.com\"";

        return "{" + basis + (extraVeld == null ? "" : "," + extraVeld) + "}";
    }

    private static boolean heeftFouten(String body) {
        ValidationReport rapport = validator.validateRequest(
                SimpleRequest.Builder.post(PAD)
                        .withContentType("application/json")
                        .withBody(body)
                        .build());

        return rapport.hasErrors();
    }
}
