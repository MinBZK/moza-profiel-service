package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;

@QuarkusTest
public class EndpointFuzzTest {

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
        String afdelingBeschrijving = data.consumeString(100);

        RestAssured.given()
                .pathParam("identificatieType", identificatieType)
                .pathParam("identificatieNummer", identificatieNummer)
                .queryParam("dienstverlener", dienstverlener)
                .queryParam("oin", oin)
                .queryParam("afdelingBeschrijving", afdelingBeschrijving)
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

        String body = String.format("""
                {
                  "type": "%s",
                  "waarde": "%s"
                }
                """, type, waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
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
        long afdelingId = data.consumeLong();

        String body = String.format("""
                {
                  "id": %d,
                  "type": "%s",
                  "waarde": "%s",
                  "afdelingId": %d
                }
                """, id, type, waarde, afdelingId);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
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

        String body = String.format("""
                {
                  "voorkeurType": "%s",
                  "waarde": "%s"
                }
                """, voorkeurType, waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
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

        String body = String.format("""
                {
                  "id": %d,
                  "voorkeurType": "%s",
                  "waarde": "%s"
                }
                """, id, voorkeurType, waarde);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
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
    public void fuzzGetAfdelingenDienstverlener(FuzzedDataProvider data) {
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

        String body = String.format("""
                {
                  "naam": "%s",
                  "oin": "%s"
                }
                """, naam, oin);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/profielservice/v1/dienstverlener/")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzAddAfdelingToDienstverlener(FuzzedDataProvider data) {
        String dienstverlenerNaam = data.consumeString(50);
        String beschrijving = data.consumeString(100);

        String body = String.format("""
                {
                  "beschrijving": "%s"
                }
                """, beschrijving);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .pathParam("DienstverlenerNaam", dienstverlenerNaam)
                .when()
                .post("/api/profielservice/v1/dienstverlener/{DienstverlenerNaam}/afdelingen")
                .then()
                .extract().response();
    }

    @FuzzTest
    public void fuzzPostEmailVerificatie(FuzzedDataProvider data) {
        String email = data.consumeString(50);
        String identificatieNummer = data.consumeString(20);
        String identificatieType = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String verificatieCode = data.consumeString(10);

        String body = String.format("""
                {
                  "email": "%s",
                  "identificatieNummer": "%s",
                  "identificatieType": "%s",
                  "verificatieCode": "%s"
                }
                """, email, identificatieNummer, identificatieType, verificatieCode);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/profielservice/v1/emailverificatie")
                .then()
                .extract().response();
    }
}
