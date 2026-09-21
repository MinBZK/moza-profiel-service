package nl.rijksoverheid.moz.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessExceptionTest {

    @Test
    void zonderEigenTitel_GebruiktReasonPhrase() {
        BusinessException e = new BusinessException(BusinessException.Kind.CONFLICT, "Bestaat al");

        assertEquals("Conflict", e.getTitle());
    }

    /** Een lege titel zou als {@code "title": ""} in de problem-body belanden. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void withTitle_BlancoTitel_WordtGeweigerd(String titel) {
        assertThrows(IllegalArgumentException.class,
                () -> BusinessException.withTitle(BusinessException.Kind.NOT_FOUND, titel, "Bestaat niet"));
    }
}
