package nl.rijksoverheid.moz.helper;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

public class HashHelperTest {


    private HashHelper hashHelper;

    @BeforeEach
    void setUp() {
        hashHelper = new HashHelper();
    }

    @Test
    public void hashIdentifier_NullIdentifier() {
        Assertions.assertNull(hashHelper.hashIdentifier(null));
    }

    @Test
    void hashIdentifier_AlgorithmNotAvailable() {
        // Given
        String identifier = "test";

        // When & Then
        try (MockedStatic<MessageDigest> mockedMessageDigest = mockStatic(MessageDigest.class)) {
            mockedMessageDigest.when(() -> MessageDigest.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("SHA-256 not available"));

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> hashHelper.hashIdentifier(identifier),
                    "Should throw RuntimeException when algorithm is not available");

            assertEquals("SHA-256 algorithm not available", exception.getMessage());
            assertInstanceOf(NoSuchAlgorithmException.class, exception.getCause());
        }
    }

    @Test
    void hashIdentifier_RunningTwiceDifferentInAndOutput() {
        // Given
        String identifier1 = "123456789";
        String identifier2 = "987654321";

        // When
        String hash1 = hashHelper.hashIdentifier(identifier1);
        String hash2 = hashHelper.hashIdentifier(identifier2);

        // Then
        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }
}
