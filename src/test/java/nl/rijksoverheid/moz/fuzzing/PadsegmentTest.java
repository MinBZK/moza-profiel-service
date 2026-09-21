package nl.rijksoverheid.moz.fuzzing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Zonder de spatiecorrectie verstuurt de fuzzer nooit een blanco padnaam. */
class PadsegmentTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "' '|%20",
            "+|%2B",
            "a/b|a%2Fb",
    })
    void encode_CodeertVoorEenPadsegment(String invoer, String verwacht) {
        assertEquals(verwacht, Padsegment.encode(invoer));
    }
}
