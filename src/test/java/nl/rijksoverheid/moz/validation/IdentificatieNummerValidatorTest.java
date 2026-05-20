package nl.rijksoverheid.moz.validation;

import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.dto.request.EmailVerificatieRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentificatieNummerValidatorTest {

    private IdentificatieNummerValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IdentificatieNummerValidator();
    }

    @Test
    void isValid_NullRequest_ReturnsTrue() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void isValid_NullFields_ReturnsTrue() {
        EmailVerificatieRequest request = new EmailVerificatieRequest();
        assertTrue(validator.isValid(request, null));
    }

    @Test
    void isValid_NullType_ReturnsTrue() {
        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieNummer = "123456782";
        request.identificatieType = null;
        assertTrue(validator.isValid(request, null));
    }

    @Test
    void isValid_BSNAllZeros_ReturnsFalse() {
        EmailVerificatieRequest request = createRequest("000000000", IdentificatieType.BSN);
        assertFalse(validator.isValid(request, null));
    }

    @Test
    void isValid_BSNLastDigitNine_ReturnsFalse() {
        // sum = 0*9 + ... + 0*2 + 9*(-1) = -9
        // -9 % 11 = -9
        EmailVerificatieRequest request = createRequest("000000009", IdentificatieType.BSN);
        assertFalse(validator.isValid(request, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"111222333", "123456782"})
    void isValid_ValidBSN_ReturnsTrue(String bsn) {
        EmailVerificatieRequest request = createRequest(bsn, IdentificatieType.BSN);
        assertTrue(validator.isValid(request, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678a", "1234567890", "", "12345678", "abcdefghi", "123456789012345"})
    void isValid_InvalidBSN_ReturnsFalse(String bsn) {
        EmailVerificatieRequest request = createRequest(bsn, IdentificatieType.BSN);
        assertFalse(validator.isValid(request, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "12345678901", "00000000", "12345678000"})
    void isValid_ValidKVK_ReturnsTrue(String kvk) {
        EmailVerificatieRequest request = createRequest(kvk, IdentificatieType.KVK);
        assertTrue(validator.isValid(request, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "123456789", "1234567890", "123456789012", "1234567a"})
    void isValid_InvalidKVK_ReturnsFalse(String kvk) {
        EmailVerificatieRequest request = createRequest(kvk, IdentificatieType.KVK);
        assertFalse(validator.isValid(request, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"111222333", "123456782"})
    void isValid_ValidRSIN_ReturnsTrue(String rsin) {
        EmailVerificatieRequest request = createRequest(rsin, IdentificatieType.RSIN);
        assertTrue(validator.isValid(request, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678a", "1234567890", "", "12345678", "123456789012345"})
    void isValid_InvalidRSIN_ReturnsFalse(String rsin) {
        EmailVerificatieRequest request = createRequest(rsin, IdentificatieType.RSIN);
        assertFalse(validator.isValid(request, null));
    }

    private EmailVerificatieRequest createRequest(String nummer, IdentificatieType type) {
        EmailVerificatieRequest request = new EmailVerificatieRequest();
        request.identificatieNummer = nummer;
        request.identificatieType = type;
        return request;
    }
}
