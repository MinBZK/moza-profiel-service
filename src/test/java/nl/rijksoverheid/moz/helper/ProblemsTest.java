package nl.rijksoverheid.moz.helper;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Problems is de enige plek waar RFC 9457-antwoorden worden samengesteld. De statuscodes
 * en het media type liggen vast in het API-contract (ADR + OpenAPI), dus die worden hier
 * vastgepind in plaats van per endpoint opnieuw te controleren.
 */
class ProblemsTest {

    @Test
    void notFound_HeeftStatus404EnBehoudtTitelEnDetail() {
        HttpProblem problem = Problems.notFound("Niet gevonden", "Partij bestaat niet");

        assertEquals(404, problem.getStatusCode());
        assertEquals("Niet gevonden", problem.getTitle());
        assertEquals("Partij bestaat niet", problem.getDetail());
    }

    @Test
    void badRequest_HeeftStatus400EnBehoudtTitelEnDetail() {
        HttpProblem problem = Problems.badRequest("Ongeldig", "identificatieNummer ontbreekt");

        assertEquals(400, problem.getStatusCode());
        assertEquals("Ongeldig", problem.getTitle());
        assertEquals("identificatieNummer ontbreekt", problem.getDetail());
    }

    @Test
    void serviceUnavailable_HeeftStatus503EnBehoudtTitelEnDetail() {
        HttpProblem problem = Problems.serviceUnavailable("Niet beschikbaar", "NotifyNL antwoordt niet");

        assertEquals(503, problem.getStatusCode());
        assertEquals("Niet beschikbaar", problem.getTitle());
        assertEquals("NotifyNL antwoordt niet", problem.getDetail());
    }

    @Test
    void problemResponse_LevertProblemJsonMetVolledigRfc9457Lichaam() {
        Response response = Problems.problemResponse(
                Response.Status.CONFLICT, "Conflict", "Resource bestaat al");

        assertEquals(409, response.getStatus());
        assertEquals("application/problem+json", response.getMediaType().toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertNotNull(body);
        assertEquals("about:blank", body.get("type"));
        assertEquals("Conflict", body.get("title"));
        assertEquals(409, body.get("status"));
        assertEquals("Resource bestaat al", body.get("detail"));
    }

    @Test
    void problemResponse_StatusInLichaamVolgtDeMeegegevenStatus() {
        // Het status-veld in de body en de HTTP-status mogen niet uit elkaar lopen;
        // RFC 9457 schrijft voor dat ze gelijk zijn.
        for (Response.Status status : new Response.Status[]{
                Response.Status.BAD_REQUEST, Response.Status.FORBIDDEN, Response.Status.NOT_FOUND}) {
            Response response = Problems.problemResponse(status, status.getReasonPhrase(), "detail");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getEntity();
            assertEquals(status.getStatusCode(), response.getStatus());
            assertEquals(status.getStatusCode(), body.get("status"));
        }
    }
}
