package nl.rijksoverheid.moz.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import nl.rijksoverheid.moz.common.ContactType;
import nl.rijksoverheid.moz.entity.Contactgegeven;
import nl.rijksoverheid.moz.entity.Dienst;
import nl.rijksoverheid.moz.entity.Dienstverlener;
import nl.rijksoverheid.moz.entity.DienstverlenerDienst;
import nl.rijksoverheid.moz.entity.Identificatie;
import nl.rijksoverheid.moz.entity.Partij;
import nl.rijksoverheid.moz.entity.ScopeContactgegeven;
import nl.rijksoverheid.moz.entity.ScopeVoorkeur;
import nl.rijksoverheid.moz.entity.Voorkeur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static nl.rijksoverheid.moz.common.IdentificatieType.BSN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

/**
 * Verplichte tekstvelden moeten één regel zijn en mogen niet uit alleen witruimte bestaan.
 * Het contract legt dat vast met {@code pattern: "^[^\r\n]*\S[^\r\n]*$"} op de betreffende
 * velden, waaruit de generator een {@code @Pattern} op de DTO maakt. Zonder pattern liet
 * {@code @NotNull} een lege string ongehinderd door (MinBZK/MijnOverheidZakelijk#766).
 *
 * <p>De expressie is bewust geankerd. JSON Schema {@code pattern} zoekt een deelstring,
 * terwijl Jakarta {@code @Pattern} een full-match doet; zonder {@code ^} en {@code $} keurt
 * het gepubliceerde contract dus waarden goed die de server weigert. Twee alternatieven zijn
 * afgevallen: {@code ".*\S.*"} laat die divergentie bestaan, en {@code "(?s).*\S.*"} heft hem
 * op voor Java maar is geen geldige ECMA 262-regex — daarmee wordt het contract onbruikbaar
 * voor consumers en valt de contractvalidatie in deze suite op élk endpoint om.
 *
 * <p>{@code validationFilter} hangt alleen aan de tests die een contractgeldig request sturen.
 * De filter valideert namelijk ook het request, en doet dat client-side: een opzettelijk
 * ongeldige body wordt geweigerd voordat hij de server bereikt, waarmee de negatieve test
 * niet meer test wat hij moet testen. Die tests controleren de responsevorm daarom zelf.
 */
@QuarkusTest
class BlancoWaardenIntegrationTest extends OpenApiValidationTest {

    @AfterEach
    @Transactional
    void tearDown() {
        ScopeContactgegeven.deleteAll();
        ScopeVoorkeur.deleteAll();
        Contactgegeven.deleteAll();
        Voorkeur.deleteAll();
        DienstverlenerDienst.deleteAll();
        Dienst.deleteAll();
        Identificatie.deleteAll();
        Partij.deleteAll();
        Dienstverlener.deleteAll();
    }

    /**
     * Witruimte, leeg en meerregelig gaan alle drie langs dezelfde pattern. De laatste is
     * de reden dat de expressie geankerd is en niet op {@code .*\S.*} steunt.
     */
    @ParameterizedTest
    @ValueSource(strings = {"   ", "", "regel1\\nregel2"})
    void contactgegevenMetOngeldigeWaardeWordtAfgewezen(String waarde) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"%s"}
                        """.formatted(waarde))
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("waarde"));
    }

    /**
     * Een identificatienummer met een afsluitende CR zou anders als aparte partij worden
     * opgeslagen, waarna de echte partij op die sleutel niet meer te vinden is.
     */
    @Test
    void contactgegevenMetMeerregeligIdentificatieNummerWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104\\r",
                         "type":"Email","waarde":"test@example.com"}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("identificatieNummer"));
    }

    @Test
    void dienstverlenerMetBlancoNaamWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"   \",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("naam"));
    }

    @Test
    void voorkeurMetBlancoWaardeWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "voorkeurType":"WebsiteTaal","waarde":" "}
                        """)
                .when().post("/api/profielservice/v1/voorkeur")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("waarde"));
    }

    /**
     * Een blanco scope kwam eerder ongehinderd langs de validatie en strandde pas op een
     * 404 "Dienstverlener bestaat niet" — een foutmelding die naar het verkeerde probleem
     * wijst. Nu draagt {@code ScopeRequest} dezelfde pattern als de rest.
     */
    @Test
    void contactgegevenMetBlancoScopeWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "type":"Email","waarde":"test@example.com",
                         "scope":{"dienstverlenerNaam":"   "}}
                        """)
                .when().post("/api/profielservice/v1/contactgegeven")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem("scope.dienstverlenerNaam"));
    }

    /**
     * De scope-velden op PartijRequest zaten eerder zonder pattern. Een blanco waarde gold
     * daar als "wel opgegeven", waarna erop werd gefilterd en de aanroeper een 200 kreeg met een
     * uitgedund profiel: bij een blanco {@code dienstverlener} vielen alle gescopete gegevens weg,
     * bij een blanco {@code dienstNaam} alleen de dienst-specifieke — DV-brede scopes overleven
     * die filter. De partij en haar identificaties bleven in beide gevallen staan, dus de fout
     * was moeilijk te zien.
     */
    @ParameterizedTest
    @ValueSource(strings = {"dienstverlener", "dienstNaam"})
    void partijMetBlancoScopeVeldWordtAfgewezen(String veld) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "%s":"   "}
                        """.formatted(veld))
                .when().post("/api/profielservice/v1/partij")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem(veld));
    }

    /**
     * De bulk-lijst had op {@code main} een {@code @NotEmpty} en staat nu als {@code required} met
     * {@code minItems: 1} in het contract, waar de generator {@code @NotNull} plus
     * {@code @Size(min=1)} van maakt. Dat is de enige constraint in deze overstap die van
     * annotatie-familie wisselt, dus de lege lijst hoort over HTTP vastgelegd te worden en niet
     * alleen in het gegenereerde bestand te staan.
     */
    @Test
    void bulkMetLegeLijstWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"identificaties\":[]}")
                .when().post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem(containsString("identificaties")));
    }

    /**
     * De bovengrens hoort net zo goed over HTTP vast te liggen als de ondergrens. Een limiet die
     * alleen in het contract staat en niet blijkt te werken is erger dan geen limiet: het
     * document belooft dan een begrenzing die er niet is.
     */
    @Test
    void bulkBovenDeMaximumLengteWordtAfgewezen() {
        String teVeel = IntStream.rangeClosed(0, 100)
                .mapToObj(i -> "{\"identificatieType\":\"BSN\",\"identificatieNummer\":\"1111111%02d\"}"
                        .formatted(i))
                .collect(Collectors.joining(",", "{\"identificaties\":[", "]}"));

        given()
                .contentType(ContentType.JSON)
                .body(teVeel)
                .when().post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem(containsString("identificaties")));
    }

    /**
     * De element-constraints van de bulk-lijst draaiden op {@code main} nooit: daar stond
     * {@code @NotEmpty} zonder {@code @Valid}, dus een lijstelement kwam ongevalideerd door. Het
     * contract levert nu {@code List<@Valid PartijIdentificatieRequest>} op, maar of die
     * type-use-annotatie werkelijk cascadeert hangt van de generator- en Hibernate
     * Validator-versie af. Valt hij stil weg, dan komt een meerregelig identificatienummer in de
     * groepering van {@code getPartijResponseBulk} terecht — precies wat
     * {@link #contactgegevenMetMeerregeligIdentificatieNummerWordtAfgewezen()} voor het
     * enkelvoudige pad afvangt.
     */
    @Test
    void bulkMetBlancoIdentificatieNummerWordtAfgewezen() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"identificaties":[{"identificatieType":"BSN","identificatieNummer":"   "}]}
                        """)
                .when().post("/api/profielservice/v1/partijen/bulk")
                .then()
                .statusCode(BAD_REQUEST)
                .contentType("application/problem+json")
                .body("violations.field", hasItem(containsString("identificatieNummer")));
    }

    /**
     * Positieve tegenhanger voor de scope-velden op PartijRequest. Een tot {@code ^\S+$}
     * aangescherpte pattern op precies deze twee velden zou alle blanco-tests groen laten terwijl
     * "Gemeente Amsterdam" als scope voortaan een 400 oplevert. Een globale aanscherping valt al
     * om over {@code dienstverlenerMetNormaleNaamWordtGeaccepteerd}; deze test dekt de variant
     * die alleen PartijRequest raakt.
     *
     * <p>De fixture bevat bewust twee contactgegevens onder verschillende dienstverleners, en de
     * assertie eist er precies één terug. Met één rij zou over-inclusie onzichtbaar zijn: de
     * filter volledig weghalen leverde dan hetzelfde antwoord op.
     */
    @Test
    void partijMetGevuldeScopeVeldenWordtGeaccepteerd() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dienstverlener dv = new Dienstverlener();
            dv.setNaam("Gemeente Amsterdam");
            dv.persist();
            Dienst dienst = new Dienst();
            dienst.setNaam("Parkeervergunning");
            dienst.persist();
            DienstverlenerDienst link = new DienstverlenerDienst(dv, dienst);
            link.persist();

            Partij p = new Partij();
            p.addIdentificatie(new Identificatie(BSN, "111111104"));
            p.persist();

            Contactgegeven c = new Contactgegeven();
            c.setType(ContactType.Email);
            c.setWaarde("scope@example.com");
            c.setPartij(p);
            c.persist();

            new ScopeContactgegeven(c, link).persist();

            Dienstverlener andereDv = new Dienstverlener();
            andereDv.setNaam("Gemeente Rotterdam");
            andereDv.persist();
            DienstverlenerDienst andereLink = new DienstverlenerDienst(andereDv, null);
            andereLink.persist();

            Contactgegeven buiten = new Contactgegeven();
            buiten.setType(ContactType.Telefoonnummer);
            buiten.setWaarde("0612345678");
            buiten.setPartij(p);
            buiten.persist();

            new ScopeContactgegeven(buiten, andereLink).persist();
        });

        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body("""
                        {"identificatieType":"BSN","identificatieNummer":"111111104",
                         "dienstverlener":"Gemeente Amsterdam","dienstNaam":"Parkeervergunning"}
                        """)
                .when().post("/api/profielservice/v1/partij")
                .then()
                .statusCode(OK)
                .body("contactgegevens.waarde", contains("scope@example.com"));
    }

    /**
     * Positieve tegenhanger: een gewone waarde met een spatie erin hoort door de pattern te
     * komen. Zonder deze test zou een expressie die alles weigert er net zo groen uitzien.
     */
    @Test
    void dienstverlenerMetNormaleNaamWordtGeaccepteerd() {
        given()
                .filter(validationFilter)
                .contentType(ContentType.JSON)
                .body("{\"naam\":\"Gemeente Amsterdam\",\"beschrijving\":\"Beschrijving\"}")
                .when().post("/api/profielservice/v1/dienstverlener")
                .then()
                .statusCode(201)
                .body("naam", equalTo("Gemeente Amsterdam"));
    }
}
