package nl.rijksoverheid.moz.helper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.crypto.Mac;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

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
        hashHelper = new HashHelper(Optional.of("test-pepper"));
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
        try (MockedStatic<Mac> mockedMac = mockStatic(Mac.class)) {
            mockedMac.when(() -> Mac.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("HmacSHA256 not available"));

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> hashHelper.hashIdentifier(identifier),
                    "Should throw RuntimeException when algorithm is not available");

            assertEquals("HmacSHA256 could not be initialised", exception.getMessage());
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

    @Test
    void hashIdentifier_SameInputSamePepper_IsStable() {
        // Given
        String identifier = "123456789";

        // When
        String hash1 = hashHelper.hashIdentifier(identifier);
        String hash2 = new HashHelper(Optional.of("test-pepper")).hashIdentifier(identifier);

        // Then
        assertEquals(hash1, hash2, "Same input and pepper should produce the same pseudonym");
    }

    @Test
    void hashIdentifier_SameInputOtherPepper_DiffersFromOriginal() {
        // Given
        String identifier = "123456789";
        HashHelper otherPepper = new HashHelper(Optional.of("ander-pepper"));

        // When
        String hash1 = hashHelper.hashIdentifier(identifier);
        String hash2 = otherPepper.hashIdentifier(identifier);

        // Then
        assertNotEquals(hash1, hash2, "Changing the pepper should change the pseudonym");
    }

    @Test
    void constructor_MissingPepper_Throws() {
        assertThrows(IllegalStateException.class, () -> new HashHelper(Optional.empty()));
    }

    @Test
    void constructor_BlankPepper_Throws() {
        assertThrows(IllegalStateException.class, () -> new HashHelper(Optional.of("  ")));
    }
}
