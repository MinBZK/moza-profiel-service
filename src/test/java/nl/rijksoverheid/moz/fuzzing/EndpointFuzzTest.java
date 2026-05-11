package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;

@QuarkusTest
public class EndpointFuzzTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void setup() {
        // You can configure RestAssured here if needed
    }

    @FuzzTest
    public void fuzzGetPartij(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN", "INVALID"});
        String identificatieNummer = data.consumeString(20);
        String dienstverlener = data.consumeString(50);
        String oin = data.consumeString(20);
        String dienstBeschrijving = data.consumeString(100);

        RestAssured.given()
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .queryParam("dienstverlener", dienstverlener)
                .queryParam("oin", oin)
                .queryParam("dienstBeschrijving", dienstBeschrijving)
                .when()
                .get("/api/profielservice/v1/{identificatieType}/{identificatieNummer}")
                .then()
                .extract().response();
        
        // We don't necessarily assert success, we just want to see if it crashes the JVM
    }

    @FuzzTest
    public void fuzzAddContactgegeven(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String identificatieNummer = data.consumeString(20);
        
        String type = data.consumeString(10);
        String waarde = data.consumeString(50);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", type);
        body.put("waarde", waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .when()
                .post("/api/profielservice/v1/contactgegeven/{identificatieType}/{identificatieNummer}")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzUpdateContactgegeven(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String identificatieNummer = data.consumeString(20);
        long id = data.consumeLong();
        String type = data.consumeString(10);
        String waarde = data.consumeString(50);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("id", id);
        body.put("type", type);
        body.put("waarde", waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .when()
                .put("/api/profielservice/v1/contactgegeven/{identificatieType}/{identificatieNummer}/")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzDeleteContactgegeven(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String identificatieNummer = data.consumeString(20);
        long contactgegevenId = data.consumeLong();

        RestAssured.given()
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .pathParam("contactgegevenId", contactgegevenId)
                .when()
                .delete("/api/profielservice/v1/contactgegeven/{identificatieType}/{identificatieNummer}/{contactgegevenId}")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzAddVoorkeur(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String identificatieNummer = data.consumeString(20);
        String voorkeurType = data.consumeString(10);
        String waarde = data.consumeString(50);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("voorkeurType", voorkeurType);
        body.put("waarde", waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .when()
                .post("/api/profielservice/v1/voorkeur/{identificatieType}/{identificatieNummer}")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzUpdateVoorkeur(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String identificatieNummer = data.consumeString(20);
        long id = data.consumeLong();
        String voorkeurType = data.consumeString(10);
        String waarde = data.consumeString(50);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("id", id);
        body.put("voorkeurType", voorkeurType);
        body.put("waarde", waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .when()
                .put("/api/profielservice/v1/voorkeur/{identificatieType}/{identificatieNummer}/")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzDeleteVoorkeur(FuzzedDataProvider data) {
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String identificatieNummer = data.consumeString(20);
        long voorkeurId = data.consumeLong();

        RestAssured.given()
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .pathParam("voorkeurId", voorkeurId)
                .when()
                .delete("/api/profielservice/v1/voorkeur/{identificatieType}/{identificatieNummer}/{voorkeurId}")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzGetDienstenDienstverlener(FuzzedDataProvider data) {
        String naam = data.consumeString(50);

        RestAssured.given()
                .pathParam("naam", naam)
                .when()
                .get("/api/profielservice/v1/dienstverlener/{naam}")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzAddDienstverlener(FuzzedDataProvider data) {
        String naam = data.consumeString(50);
        String oin = data.consumeString(20);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("naam", naam);
        body.put("oin", oin);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .when()
                .post("/api/profielservice/v1/dienstverlener/")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzAddDienstToDienstverlener(FuzzedDataProvider data) {
        String dienstverlenerNaam = data.consumeString(50);
        String beschrijving = data.consumeString(100);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("beschrijving", beschrijving);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .pathParam("DienstverlenerNaam", dienstverlenerNaam)
                .when()
                .post("/api/profielservice/v1/dienstverlener/{DienstverlenerNaam}/diensten")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzPostEmailVerificatie(FuzzedDataProvider data) {
        String email = data.consumeString(50);
        String identificatieNummer = data.consumeString(20);
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String verificatieCode = data.consumeString(10);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email);
        body.put("identificatieNummer", identificatieNummer);
        body.put("identificatieType", identificatieType);
        body.put("verificatieCode", verificatieCode);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body.toString())
                .when()
                .post("/api/profielservice/v1/emailverificatie")
                .then()
                .extract().response();
    }
}
