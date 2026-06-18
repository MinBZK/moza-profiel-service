package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.Response;
import nl.rijksoverheid.moz.exception.AuthorizationException;
import nl.rijksoverheid.moz.exception.BusinessException;
import nl.rijksoverheid.moz.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainExceptionMapperTest {

    private DomainExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DomainExceptionMapper();
    }

    @Test
    void mapBusinessException_NotFoundKind_Returns404() {
        BusinessException exception = new BusinessException(BusinessException.Kind.NOT_FOUND, "Partij niet gevonden");

        Response response = mapper.mapBusinessException(exception);

        assertEquals(404, response.getStatus());
        assertProblemBody(response, "Not Found", "Partij niet gevonden");
    }

    @Test
    void mapBusinessException_ConflictKind_Returns409() {
        BusinessException exception = new BusinessException(BusinessException.Kind.CONFLICT, "Partij bestaat al");

        Response response = mapper.mapBusinessException(exception);

        assertEquals(409, response.getStatus());
        assertProblemBody(response, "Conflict", "Partij bestaat al");
    }

    @Test
    void mapBusinessException_BadRequestKind_Returns400() {
        BusinessException exception = new BusinessException(BusinessException.Kind.BAD_REQUEST, "Ongeldige invoer");

        Response response = mapper.mapBusinessException(exception);

        assertEquals(400, response.getStatus());
        assertProblemBody(response, "Bad Request", "Ongeldige invoer");
    }

    @Test
    void mapTechnicalException_Returns500() {
        TechnicalException exception = new TechnicalException("Interne fout bij verwerken", new RuntimeException());

        Response response = mapper.mapTechnicalException(exception);

        assertEquals(500, response.getStatus());
        assertProblemBody(response, "Internal Server Error", "Interne fout bij verwerken");
    }

    @Test
    void mapAuthorizationException_Returns403() {
        AuthorizationException exception = new AuthorizationException("Geen toegang tot deze Partij");

        Response response = mapper.mapAuthorizationException(exception);

        assertEquals(403, response.getStatus());
        assertProblemBody(response, "Forbidden", "Geen toegang tot deze Partij");
    }

    @Test
    void mapUnhandledException_Returns500() {
        Exception exception = new RuntimeException("Onverwachte fout");

        Response response = mapper.mapUnhandledException(exception);

        assertEquals(500, response.getStatus());
        assertProblemBody(response, "Internal Server Error", "Er is een onverwachte fout opgetreden");
    }

    @SuppressWarnings("unchecked")
    private void assertProblemBody(Response response, String expectedTitle, String expectedDetail) {
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("about:blank", body.get("type"));
        assertEquals(expectedTitle, body.get("title"));
        assertEquals(response.getStatus(), body.get("status"));
        assertEquals(expectedDetail, body.get("detail"));
    }
}
