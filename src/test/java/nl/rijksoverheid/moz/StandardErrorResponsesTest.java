package nl.rijksoverheid.moz;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class StandardErrorResponsesTest {

    /**
     * POST /partij documenteert zelf geen 500; als die er toch staat, komt hij van
     * de class-level @APIResponse op de controller, inclusief een oplosbare
     * problem+json ref.
     */
    @Test
    void everyOperationDocumentsInterneServerfout() {
        given()
                .accept(ContentType.JSON)
                .when().get("/openapi.json?format=JSON")
                .then()
                .statusCode(200)
                // One operation per controller, to prove the class-level 500 propagated everywhere.
                .body("paths.'/api/profielservice/v1/partij'.post.responses.'500'.description",
                        equalTo("Interne serverfout"))
                .body("paths.'/api/profielservice/v1/partij'.post.responses.'500'.content.'application/problem+json'.schema.'$ref'",
                        containsString("HttpProblem"))
                .body("paths.'/api/profielservice/v1/dienstverlener/{naam}'.get.responses.'500'.description",
                        equalTo("Interne serverfout"))
                .body("paths.'/api/profielservice/v1/emailverificatie/code'.post.responses.'500'.description",
                        equalTo("Interne serverfout"));
    }
}
