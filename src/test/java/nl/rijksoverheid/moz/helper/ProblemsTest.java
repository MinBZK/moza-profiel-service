package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.UriInfoStub;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Problems is de enige plek waar RFC 9457-antwoorden worden samengesteld. De statuscodes
 * en het media type liggen vast in het API-contract (ADR + OpenAPI), dus die worden hier
 * vastgepind in plaats van per endpoint opnieuw te controleren.
 */
class ProblemsTest {

    private static final String PAD = "/api/profielservice/v1/partij";

    @Test
    void notFound_HeeftStatus404EnBehoudtTitelEnDetail() {
        HttpProblem problem = Problems.notFound("Niet gevonden", "Partij bestaat niet");

        assertEquals(404, problem.getStatusCode());
        assertEquals("Niet gevonden", problem.getTitle());
        assertEquals("Partij bestaat niet", problem.getDetail());
    }

    @Test
    void problemResponse_LevertProblemJsonMetVolledigRfc9457Lichaam() {
        Response response = Problems.problemResponse(
                Response.Status.CONFLICT, "Conflict", "Resource bestaat al", UriInfoStub.voorPad(PAD));

        assertEquals(409, response.getStatus());
        assertEquals("application/problem+json", response.getMediaType().toString());

        HttpProblem body = (HttpProblem) response.getEntity();
        assertEquals("Conflict", body.getTitle());
        assertEquals(409, body.getStatusCode());
        assertEquals("Resource bestaat al", body.getDetail());
        assertEquals(URI.create(PAD), body.getInstance());
    }

    /**
     * {@code getPath()} geeft het pad gedecodeerd terug, dus met een spatie erin. Wordt daar zelf
     * een {@code URI} van gebouwd, dan werpt dat en valt een nette foutrespons om in een 500.
     */
    @Test
    void problemResponse_PadMetSpatieLevertEenGecodeerdeInstance() {
        String padMetSpatie = "/api/profielservice/v1/dienstverlener/Test DV/diensten";

        Response response = Problems.problemResponse(Response.Status.NOT_FOUND,
                "Dienstverlener niet gevonden", "Bestaat niet", UriInfoStub.voorPad(padMetSpatie));

        HttpProblem body = (HttpProblem) response.getEntity();
        assertEquals("/api/profielservice/v1/dienstverlener/Test%20DV/diensten",
                body.getInstance().toString());
    }

    @Test
    void problemResponse_StatusInLichaamVolgtDeMeegegevenStatus() {
        // Het status-veld in de body en de HTTP-status mogen niet uit elkaar lopen;
        // RFC 9457 schrijft voor dat ze gelijk zijn.
        for (Response.Status status : new Response.Status[]{
                Response.Status.BAD_REQUEST, Response.Status.FORBIDDEN, Response.Status.NOT_FOUND}) {
            Response response = Problems.problemResponse(
                    status, status.getReasonPhrase(), "detail", UriInfoStub.voorPad(PAD));

            HttpProblem body = (HttpProblem) response.getEntity();
            assertEquals(status.getStatusCode(), response.getStatus());
            assertEquals(status.getStatusCode(), body.getStatusCode());
        }
    }
}
