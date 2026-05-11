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

/**
 * Standalone fuzz target for ClusterFuzzLite.
 * Expects Quarkus to be running as a separate process (started by the wrapper
 * script) with H2 in-memory database on port 8081. This fuzzer sends
 * coverage-guided HTTP requests to test all REST endpoints.
 */
public class EndpointFuzzer {

    private static final HttpClient client;
    private static final String BASE = "http://localhost:8081/api/profielservice/v1";

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
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
        get("/identificaties/" + enc(type) + "/" + enc(nummer));
    }

    private static void fuzzAddContactgegeven(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        String json = data.consumeRemainingAsString();
        post("/contactgegevens/" + enc(type) + "/" + enc(nummer), json);
    }

    private static void fuzzUpdateContactgegeven(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        long id = data.consumeLong();
        String json = data.consumeRemainingAsString();
        put("/contactgegevens/" + enc(type) + "/" + enc(nummer) + "/" + id, json);
    }

    private static void fuzzDeleteContactgegeven(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        long id = data.consumeLong();
        delete("/contactgegevens/" + enc(type) + "/" + enc(nummer) + "/" + id);
    }

    private static void fuzzAddVoorkeur(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        String json = data.consumeRemainingAsString();
        post("/voorkeuren/" + enc(type) + "/" + enc(nummer), json);
    }

    private static void fuzzUpdateVoorkeur(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        long id = data.consumeLong();
        String json = data.consumeRemainingAsString();
        put("/voorkeuren/" + enc(type) + "/" + enc(nummer) + "/" + id, json);
    }

    private static void fuzzDeleteVoorkeur(FuzzedDataProvider data) throws Exception {
        String type = data.pickValue(new String[]{"BSN", "KVK", "RSIN"});
        String nummer = data.consumeString(20);
        long id = data.consumeLong();
        delete("/voorkeuren/" + enc(type) + "/" + enc(nummer) + "/" + id);
    }

    private static void fuzzGetDienstenDienstverlener(FuzzedDataProvider data) throws Exception {
        String naam = data.consumeString(50);
        get("/dienstverleners/" + enc(naam));
    }

    private static void fuzzAddDienstverlener(FuzzedDataProvider data) throws Exception {
        String json = data.consumeRemainingAsString();
        post("/dienstverleners", json);
    }

    private static void fuzzAddDienstToDienstverlener(FuzzedDataProvider data) throws Exception {
        String naam = data.consumeString(50);
        String json = data.consumeRemainingAsString();
        post("/dienstverleners/" + enc(naam) + "/diensten", json);
    }

    private static void fuzzPostEmailVerificatie(FuzzedDataProvider data) throws Exception {
        String json = data.consumeRemainingAsString();
        post("/email-verificaties", json);
    }
}
