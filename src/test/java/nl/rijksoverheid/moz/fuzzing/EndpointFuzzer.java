package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.BugDetectors;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone fuzz target for ClusterFuzzLite.
 * Expects Quarkus to be running as a separate process (started by the wrapper
 * script) on port 8081, backed by the PostgreSQL the same script starts. This
 * fuzzer sends coverage-guided HTTP requests to test all REST endpoints.
 */
public class EndpointFuzzer {

    private static final HttpClient client;
    private static final String BASE = "http://localhost:8081/api/profielservice/v1";

    /** Zie fuzzAddDienstToDienstverlener: een naam die gegarandeerd bestaat. */
    private static final String BEKENDE_DIENSTVERLENER = "FuzzDienstverlener";

    /** Blijft false zolang het aanmaken niet is gelukt, zodat een mislukte POST opnieuw gaat. */
    private static final AtomicBoolean bekendeDienstverlenerBestaat = new AtomicBoolean();

    // Keep reference to prevent GC; allows network connections for the main thread.
    @SuppressWarnings("unused")
    private static final AutoCloseable networkAllowed;

    static {
        // Must be called before any HTTP connection to avoid Jazzer's SSRF sanitizer
        networkAllowed = BugDetectors.allowNetworkConnections();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        int endpoint = data.consumeInt(0, 10);
        try {
            switch (endpoint) {
                case 0 -> fuzzGetPartij(data);
                case 1 -> fuzzAddContactgegeven(data);
                case 2 -> fuzzUpdateContactgegeven(data);
                case 3 -> fuzzDeleteContactgegeven(data);
                case 4 -> fuzzAddVoorkeur(data);
                case 5 -> fuzzUpdateVoorkeur(data);
                case 6 -> fuzzDeleteVoorkeur(data);
                case 7 -> fuzzGetDienstenDienstverlener(data);
                case 8 -> fuzzAddDienstverlener(data);
                case 9 -> fuzzAddDienstToDienstverlener(data);
                case 10 -> fuzzPostEmailVerificatie(data);
            }
        } catch (Exception e) {
            // Expected for invalid inputs or connection issues
        }
    }

    private static String enc(String s) {
        // URLEncoder codeert voor een query: een spatie wordt '+', en in een padsegment is dat
        // een letterlijke plus. Een plus in de invoer zelf komt er als %2B uit.
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void get(String path) throws Exception {
        client.send(
            HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build(),
            HttpResponse.BodyHandlers.discarding());
    }

    private static void post(String path, String body) throws Exception {
        client.send(
            HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.discarding());
    }

    private static void put(String path, String body) throws Exception {
        client.send(
            HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.discarding());
    }

    private static void delete(String path) throws Exception {
        client.send(
            HttpRequest.newBuilder().uri(URI.create(BASE + path)).DELETE().build(),
            HttpResponse.BodyHandlers.discarding());
    }

    private static void fuzzGetPartij(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN", "INVALID"});
        String nummer = data.consumeString(20);
        get("/" + enc(type) + "/" + enc(nummer));
    }

    private static void fuzzAddContactgegeven(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        String json = data.consumeRemainingAsString();
        post("/contactgegeven/" + enc(type) + "/" + enc(nummer), json);
    }

    private static void fuzzUpdateContactgegeven(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        String json = data.consumeRemainingAsString();
        put("/contactgegeven/" + enc(type) + "/" + enc(nummer) + "/", json);
    }

    private static void fuzzDeleteContactgegeven(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        long id = data.consumeLong();
        delete("/contactgegeven/" + enc(type) + "/" + enc(nummer) + "/" + id);
    }

    private static void fuzzAddVoorkeur(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        String json = data.consumeRemainingAsString();
        post("/voorkeur/" + enc(type) + "/" + enc(nummer), json);
    }

    private static void fuzzUpdateVoorkeur(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        String json = data.consumeRemainingAsString();
        put("/voorkeur/" + enc(type) + "/" + enc(nummer) + "/", json);
    }

    private static void fuzzDeleteVoorkeur(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        long id = data.consumeLong();
        delete("/voorkeur/" + enc(type) + "/" + enc(nummer) + "/" + id);
    }

    private static void fuzzGetDienstenDienstverlener(FuzzedDataProvider data) throws Exception {
        String naam = data.consumeString(50);
        get("/dienstverlener/" + enc(naam));
    }

    private static void fuzzAddDienstverlener(FuzzedDataProvider data) throws Exception {
        String json = data.consumeRemainingAsString();
        post("/dienstverlener/", json);
    }

    /**
     * Een onbekende dienstverlener strandt sinds MinBZK/MijnOverheidZakelijk#967 op een 404 nog
     * voordat de body wordt gelezen; de helft van de invoer gebruikt daarom een naam die bestaat,
     * anders bereikt deze target de dienstlogica erachter nooit meer.
     */
    private static void fuzzAddDienstToDienstverlener(FuzzedDataProvider data) throws Exception {
        boolean bekendeDienstverlener = data.consumeBoolean();
        String naam = bekendeDienstverlener ? BEKENDE_DIENSTVERLENER : data.consumeString(50);
        String json = data.consumeRemainingAsString();

        if (bekendeDienstverlener && !bekendeDienstverlenerBestaat.get()) {
            post("/dienstverlener/", "{\"naam\":\"" + BEKENDE_DIENSTVERLENER + "\"}");
            bekendeDienstverlenerBestaat.set(true);
        }

        post("/dienstverlener/" + enc(naam) + "/diensten", json);
    }

    private static void fuzzPostEmailVerificatie(FuzzedDataProvider data) throws Exception {
        String json = data.consumeRemainingAsString();
        post("/emailverificatie", json);
    }
}
