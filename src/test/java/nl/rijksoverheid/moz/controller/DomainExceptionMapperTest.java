package nl.rijksoverheid.moz.controller;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import nl.rijksoverheid.moz.UriInfoStub;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainExceptionMapperTest {

    private static final String PAD = "/api/profielservice/v1/partij";

    private DomainExceptionMapper mapper;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() {
        mapper = new DomainExceptionMapper();
        uriInfo = UriInfoStub.voorPad(PAD);
    }

    /**
     * {@code withTitle} is de enige reden dat {@code getTitle()} niet gewoon de reason phrase
     * teruggeeft. Zonder deze test blijft die tak ongedekt en kan hij stil verdwijnen.
     */
    @Test
    void mapBusinessException_EigenTitel_GebruiktDieTitel() {
        BusinessException exception = BusinessException.withTitle(
                BusinessException.Kind.NOT_FOUND, "Dienstverlener niet gevonden", "Bestaat niet");

        Response response = mapper.mapBusinessException(exception, uriInfo);

        assertEquals(404, response.getStatus());
        assertProblemBody(response, "Dienstverlener niet gevonden", "Bestaat niet");
    }

    @Test
    void mapBusinessException_NotFoundKind_Returns404() {
        BusinessException exception = new BusinessException(BusinessException.Kind.NOT_FOUND, "Partij niet gevonden");

        Response response = mapper.mapBusinessException(exception, uriInfo);

        assertEquals(404, response.getStatus());
        assertProblemBody(response, "Not Found", "Partij niet gevonden");
    }

    @Test
    void mapBusinessException_ConflictKind_Returns409() {
        BusinessException exception = new BusinessException(BusinessException.Kind.CONFLICT, "Partij bestaat al");

        Response response = mapper.mapBusinessException(exception, uriInfo);

        assertEquals(409, response.getStatus());
        assertProblemBody(response, "Conflict", "Partij bestaat al");
    }

    @Test
    void mapBusinessException_BadRequestKind_Returns400() {
        BusinessException exception = new BusinessException(BusinessException.Kind.BAD_REQUEST, "Ongeldige invoer");

        Response response = mapper.mapBusinessException(exception, uriInfo);

        assertEquals(400, response.getStatus());
        assertProblemBody(response, "Bad Request", "Ongeldige invoer");
    }

    @Test
    void mapTechnicalException_Returns500() {
        TechnicalException exception = new TechnicalException("Interne fout bij verwerken", new RuntimeException());

        Response response = mapper.mapTechnicalException(exception, uriInfo);

        assertEquals(500, response.getStatus());
        assertProblemBody(response, "Internal Server Error", "Interne fout bij verwerken");
    }

    @Test
    void mapUnhandledException_Returns500() {
        Exception exception = new RuntimeException("Onverwachte fout");

        Response response = mapper.mapUnhandledException(exception, uriInfo);

        assertEquals(500, response.getStatus());
        assertProblemBody(response, "Internal Server Error", "Er is een onverwachte fout opgetreden");
    }

    /**
     * Dezelfde velden als de routes die via {@code Problems.notFound} lopen, inclusief
     * {@code instance}. Liep dat uiteen, dan kreeg dezelfde fout een ander lichaam naargelang de
     * route die hem opleverde.
     */
    private void assertProblemBody(Response response, String expectedTitle, String expectedDetail) {
        assertEquals("application/problem+json", response.getMediaType().toString());

        HttpProblem body = (HttpProblem) response.getEntity();
        assertEquals(expectedTitle, body.getTitle());
        assertEquals(response.getStatus(), body.getStatusCode());
        assertEquals(expectedDetail, body.getDetail());
        assertEquals(URI.create(PAD), body.getInstance());
    }
}
