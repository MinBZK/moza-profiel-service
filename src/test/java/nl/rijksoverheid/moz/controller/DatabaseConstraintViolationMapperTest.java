package nl.rijksoverheid.moz.controller;

import jakarta.ws.rs.core.Response;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Deze mapper vangt de race die de pre-checks in PartijService niet kunnen afvangen: twee
 * gelijktijdige writes die pas bij flush op een unique index botsen. Zonder de mapper zou
 * quarkus-http-problem er een 500 van maken. Het gedrag is daarom apart vastgelegd; via HTTP
 * is deze situatie niet betrouwbaar te reproduceren omdat de pre-check er normaal vóór zit.
 */
class DatabaseConstraintViolationMapperTest {

    private final DatabaseConstraintViolationMapper mapper = new DatabaseConstraintViolationMapper();

    private static ConstraintViolationException violation(String constraintName) {
        return new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("duplicate key", "23505"),
                constraintName);
    }

    @Test
    void maptConstraintViolationNaar409ProblemJson() {
        Response response = mapper.toResponse(violation("uq_contactgegeven_partij_type_waarde"));

        assertEquals(409, response.getStatus());
        assertEquals("application/problem+json", response.getMediaType().toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("Conflict", body.get("title"));
        assertEquals(409, body.get("status"));
        assertEquals("Resource bestaat al of conflicteert met een unique constraint", body.get("detail"));
    }

    @Test
    void zonderConstraintNaamNogSteeds409() {
        // Niet elke driver geeft een constraint-naam terug; de mapper mag daar niet op stuklopen.
        Response response = mapper.toResponse(violation(null));

        assertEquals(409, response.getStatus());
    }

    @Test
    void lektGeenDatabaseDetailsInHetAntwoord() {
        // De SQL-melding kan tabel- en kolomnamen bevatten. Die horen niet in een publiek
        // API-antwoord thuis, dus het detail moet de generieke tekst blijven.
        Response response = mapper.toResponse(violation("uq_geheime_tabel_kolom"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        String detail = String.valueOf(body.get("detail"));
        assertFalse(detail.contains("uq_geheime_tabel_kolom"));
        assertFalse(detail.contains("duplicate key"));
    }
}
