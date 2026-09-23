package nl.rijksoverheid.moz.fuzzing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Los van EndpointFuzzer, zodat een unittest hem kan laden zonder de Jazzer-initialisatie. */
final class Padsegment {

    private Padsegment() {
    }

    static String encode(String s) {
        // URLEncoder codeert voor een query: een spatie wordt '+', en in een padsegment is dat
        // een letterlijke plus. Een plus in de invoer zelf komt er als %2B uit.
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
