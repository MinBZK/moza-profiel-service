package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.BugDetectors;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.quarkus.runtime.Quarkus;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Standalone fuzz target for ClusterFuzzLite.
 * Starts Quarkus in-process (same JVM as Jazzer) with H2 in-memory database,
 * so Jazzer can instrument all endpoint/service/repository code for
 * coverage-guided fuzzing of the REST API.
 */
public class EndpointFuzzer {

    private static final HttpClient client;
    private static final String BASE = "http://localhost:8081/api/profielservice/v1";

    static {
        // Configure Quarkus for fuzzing: H2 database, dummy external services
        System.setProperty("quarkus.http.port", "8081");
        System.setProperty("quarkus.log.level", "WARN");
        System.setProperty("quarkus.rest-client.basisprofiel-api.url", "http://localhost:9999");
        System.setProperty("quarkus.rest-client.email-api.url", "http://localhost:9999");

        // Start Quarkus on a background thread (Quarkus.run blocks)
        Thread quarkusThread = new Thread(() -> Quarkus.run(new String[]{}));
        quarkusThread.setDaemon(true);
        quarkusThread.start();

        // Wait for the HTTP server to accept connections
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        boolean ready = false;
        for (int i = 0; i < 120; i++) {
            try {
                Thread.sleep(250);
                client.send(
                    HttpRequest.newBuilder().uri(URI.create("http://localhost:8081/")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
                ready = true;
                break;
            } catch (Exception e) {
                // Not ready yet
            }
        }
        if (!ready) {
            throw new RuntimeException("Quarkus failed to start within 30 seconds");
        }
    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        try (var ignored = BugDetectors.allowNetworkConnections()) {
            int endpoint = data.consumeInt(0, 10);
            switch (endpoint) {
                case 0 -> fuzzGetPartij(data);
                case 1 -> fuzzAddContactgegeven(data);
                case 2 -> fuzzUpdateContactgegeven(data);
                case 3 -> fuzzDeleteContactgegeven(data);
                case 4 -> fuzzAddVoorkeur(data);
                case 5 -> fuzzUpdateVoorkeur(data);
                case 6 -> fuzzDeleteVoorkeur(data);
                case 7 -> fuzzGetAfdelingenDienstverlener(data);
                case 8 -> fuzzAddDienstverlener(data);
                case 9 -> fuzzAddAfdelingToDienstverlener(data);
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

    private static void fuzzGetAfdelingenDienstverlener(FuzzedDataProvider data) throws Exception {
        String naam = data.consumeString(50);
        get("/dienstverlener/" + enc(naam));
    }

    private static void fuzzAddDienstverlener(FuzzedDataProvider data) throws Exception {
        String json = data.consumeRemainingAsString();
        post("/dienstverlener/", json);
    }

    private static void fuzzAddAfdelingToDienstverlener(FuzzedDataProvider data) throws Exception {
        String naam = data.consumeString(50);
        String json = data.consumeRemainingAsString();
        post("/dienstverlener/" + enc(naam) + "/afdelingen", json);
    }

    private static void fuzzPostEmailVerificatie(FuzzedDataProvider data) throws Exception {
        String json = data.consumeRemainingAsString();
        post("/emailverificatie", json);
    }
}
