package nl.rijksoverheid.moz.controller;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieCodeAanvraagRequest;
import nl.rijksoverheid.moz.api.generated.model.EmailVerificatieRequest;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.services.EmailVerificatieService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

/**
 * Bewaakt dat de bean-validatie op de e-mailverificatie-endpoints daadwerkelijk in de
 * HTTP-pipeline zit. Twee dingen kunnen hier stil kapot gaan:
 *
 * <ul>
 *   <li>{@code @ValidIdentificatieNummer} is een class-level constraint en kan dus niet
 *       uit een JSON Schema-property volgen. Het contract hangt hem op de gegenereerde
 *       {@code EmailVerificatieRequest} via {@code x-class-extra-annotation}. Raakt die
 *       vendor-extensie zoek, dan verdwijnt de elfproef zonder dat er iets faalt —
 *       {@code IdentificatieNummerValidatorTest} roept de validator immers rechtstreeks
 *       aan en blijft daarom hoe dan ook groen.</li>
 *   <li>Zonder {@code @Valid} op de controllerparameter draait geen enkele constraint,
 *       ook de gegenereerde {@code @NotNull}/{@code @Pattern} niet.</li>
 * </ul>
 *
 * <p>De elfproef geldt bewust alleen op {@code /emailverificatie} en niet op
 * {@code /emailverificatie/code}; zie de toelichting bij {@code EmailVerificatieCodeAanvraagRequest}
 * in het contract en MinBZK/MijnOverheidZakelijk#923.
 *
 * <p>{@code validationFilter} hangt alleen aan de requests die contractgeldig zijn. De filter
 * valideert namelijk ook het request en doet dat client-side, dus een body die het schema
 * bewust schendt bereikt de server niet eens. Een identificatieNummer dat de elfproef niet
 * doorstaat is schematisch wél geldig — de elfproef staat niet in het schema — dus daar kan
 * de filter gewoon mee.
 */
@QuarkusTest
class EmailVerificatieValidatieIntegrationTest extends OpenApiValidationTest {

    @InjectMock
    EmailVerificatieService emailVerificatieService;

    /**
     * "111111111" doorstaat de elfproef niet (som 43, niet deelbaar door 11).
     *
     * <p>De statuscode en het content-type zijn hier niet onderscheidend: valt de constraint
     * weg, dan komt het request bij de gemockte service, die {@code false} teruggeeft, en dat
     * levert óók een 400 met {@code application/problem+json}. Wat deze test laat omvallen is
     * de assertie op de constraint-melding en de {@code verify(..., never())} eronder. Let op
     * dat {@code not(empty())} hier niet volstaat: RestAssured lost een ontbrekend pad op naar
     * {@code null}, en {@code empty()} is een {@code TypeSafeMatcher} die op {@code null}
     * {@code false} geeft — {@code not(...)} slaagt dan juist.
     */
    @Test
    void emailVerificatieMetOngeldigBsnWordtAfgewezen() {
        var body = new EmailVerificatieRequest();
        body.setEmail("email@email.com");
        body.setVerificatieCode("123456");
        body.setIdentificatieNummer("111111111");
        body.setIdentificatieType(IdentificatieType.BSN);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/profielservice/v1/emailverificatie")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.message", hasItem(containsString("identificatieNummer")));

        Mockito.verify(emailVerificatieService, Mockito.never()).verifieerEmail(Mockito.any());
    }

    /**
     * Contrast met bovenstaande: "123456782" doorstaat de elfproef wel, dus de validatie
     * laat het request door en het 400-antwoord komt uit de service. Dit legt vast dat de
     * constraint selectief is en niet elk request blindelings afwijst.
     */
    @Test
    void emailVerificatieMetGeldigBsnBereiktDeService() {
        Mockito.doReturn(false).when(emailVerificatieService).verifieerEmail(Mockito.any());

        var body = new EmailVerificatieRequest();
        body.setEmail("email@email.com");
        body.setVerificatieCode("123456");
        body.setIdentificatieNummer("123456782");
        body.setIdentificatieType(IdentificatieType.BSN);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/profielservice/v1/emailverificatie")
                .then()
                .statusCode(BAD_REQUEST)
                .body("detail", equalTo("Email verificatie mislukt"));

        Mockito.verify(emailVerificatieService).verifieerEmail(Mockito.any());
    }

    @Test
    void emailVerificatieMetBlancoVerificatieCodeWordtAfgewezen() {
        var body = new EmailVerificatieRequest();
        body.setEmail("email@email.com");
        body.setVerificatieCode("   ");
        body.setIdentificatieNummer("123456782");
        body.setIdentificatieType(IdentificatieType.BSN);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/profielservice/v1/emailverificatie")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem(containsString("verificatieCode")));

        Mockito.verify(emailVerificatieService, Mockito.never()).verifieerEmail(Mockito.any());
    }

    /**
     * Legt de asymmetrie vast die MinBZK/MijnOverheidZakelijk#923 beschrijft. De schrijfpaden
     * controleren het identificatienummer niet, dus er kan een profiel bestaan onder een nummer
     * dat de elfproef niet doorstaat — "111111111" is zo'n nummer (som 43). Zou dit endpoint de
     * controle wél doen, dan kon die gebruiker nooit meer een vervangende code aanvragen. Het
     * request hoort dus gewoon bij de service aan te komen.
     */
    @Test
    void codeAanvraagMetElfproefOngeldigBsnBereiktDeService() {
        Mockito.doReturn(OK).when(emailVerificatieService).vraagEmailVerificatieCodeAan(Mockito.any());

        var body = new EmailVerificatieCodeAanvraagRequest();
        body.setEmail("email@email.com");
        body.setIdentificatieNummer(" 111111111 ");
        body.setIdentificatieType(IdentificatieType.BSN);

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/profielservice/v1/emailverificatie/code")
                .then()
                .statusCode(OK);

        // Captor en niet argThat: verify telt met een matcher alleen de mátchende aanroepen, dus
        // een tweede aanroep met een andere payload — een tweede verificatiemail — zou erdoor
        // glippen. Bovendien toont Mockito bij een mismatch "custom argument matcher" aan de
        // Wanted-kant, waar een captor een echte waardevergelijking geeft.
        //
        // De spaties in het nummer zijn er met opzet: "111111111" is een vast punt van elke
        // plausibele normalisatie — trim, alleen cijfers, nullen aanvullen — en zou dus niets
        // bewijzen. " 111111111 " verandert wél als er onderweg genormaliseerd wordt.
        var captor = ArgumentCaptor.forClass(EmailVerificatieCodeAanvraagRequest.class);
        Mockito.verify(emailVerificatieService).vraagEmailVerificatieCodeAan(captor.capture());
        Assertions.assertEquals(" 111111111 ", captor.getValue().getIdentificatieNummer(),
                "De service hoort het nummer uit het request te zien, niet een genormaliseerde waarde");
    }

    @Test
    void codeAanvraagMetBlancoEmailWordtAfgewezen() {
        var body = new EmailVerificatieCodeAanvraagRequest();
        body.setEmail("   ");
        body.setIdentificatieNummer("123456782");
        body.setIdentificatieType(IdentificatieType.BSN);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/profielservice/v1/emailverificatie/code")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem(containsString("email")));

        Mockito.verify(emailVerificatieService, Mockito.never()).vraagEmailVerificatieCodeAan(Mockito.any());
    }
}
