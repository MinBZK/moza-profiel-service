# Fuzz Testing in Moza Profiel Service

To boost the security of our application, we have implemented fuzz testing for our REST endpoints and core components. Fuzzing is a testing technique that provides semi-random data as input to the application to find bugs, crashes, or security vulnerabilities.

## What has been implemented?

We have implemented **two types** of fuzz tests:

### 1. JUnit-based Fuzz Tests (for local development)

-   **EndpointFuzzTest.java**: A JUnit test class using `@FuzzTest` annotations and `RestAssured` to fuzz REST endpoints in a running Quarkus test instance.
-   **Purpose**: Quick feedback during development and as part of the regular test suite.
-   **Runs**: As part of `mvn test`, with configurable duration using `-Djazzer.duration=<time>`.

### 2. Standalone Fuzz Targets (for ClusterFuzzLite)

These are lightweight, standalone classes that implement the `fuzzerTestOneInput` method expected by Jazzer's native driver. They run in ClusterFuzzLite's continuous fuzzing pipeline:

-   **EndpointFuzzer.java**: Coverage-guided fuzzing of all REST endpoints by starting Quarkus as a subprocess and sending HTTP requests.
-   **HashHelperFuzzer.java**: Tests the SHA-256 hashing functionality with arbitrary string inputs to verify determinism and detect edge cases.
-   **JsonDeserializationFuzzer.java**: Fuzzes JSON deserialization of all request DTOs (ContactgegevenRequest, VoorkeurRequest, DienstverlenerRequest, etc.) to find parsing vulnerabilities.

**Key Difference**: The JUnit tests run Quarkus in-process with `@QuarkusTest`, while the ClusterFuzzLite targets are optimized for long-running, coverage-guided fuzzing in CI/CD pipelines.

## How to run Fuzz Tests

By default, fuzz tests run as part of the normal test suite but with only a few iterations. To run them for a longer period (which is recommended for finding real issues), you can use the following Maven command:

```bash
mvn test -Dtest=EndpointFuzzTest -Djazzer.duration=1m -Djacoco.skip=true
```

The `-Djazzer.duration=1m` flag tells Jazzer to run for 1 minute. You can increase this (e.g., `10m`, `1h`) for more thorough testing.

> **Note**: We use `-Djacoco.skip=true` because running only a subset of tests (like just the fuzz tests) will likely fail the JaCoCo coverage check, as the overall project coverage threshold won't be met.

### Running a specific Fuzz Test

To run only one specific fuzz test method:

```bash
mvn test -Dtest=EndpointFuzzTest#fuzzGetPartij -Djazzer.duration=1m -Djacoco.skip=true
```

## Adding more Fuzz Tests

### Adding a JUnit-based Fuzz Test

To add a new fuzz test to `EndpointFuzzTest`:

1.  Add a method to `EndpointFuzzTest` (or create a new test class).
2.  Annotate it with `@FuzzTest`.
3.  Use `FuzzedDataProvider` to generate semi-random input data.
4.  Use `RestAssured` to send this data to your endpoint.

Example:

```java
@FuzzTest
public void fuzzMyNewEndpoint(FuzzedDataProvider data) {
    String input = data.consumeRemainingAsString();

    RestAssured.given()
            .body(input)
            .when()
            .post("/api/my-endpoint")
            .then()
            .extract().response();
}
```

### Adding a Standalone Fuzzer for ClusterFuzzLite

To add a new standalone fuzzer that runs in the CI/CD pipeline:

1.  Create a new class in `src/test/java/nl/rijksoverheid/moz/fuzzing/`.
2.  Implement a `public static void fuzzerTestOneInput(FuzzedDataProvider data)` method.
3.  Use `data` to consume input and test your functionality.
4.  The fuzzer will automatically be detected by `.clusterfuzzlite/build.sh` and included in continuous fuzzing.

Example:

```java
package nl.rijksoverheid.moz.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

public class MyComponentFuzzer {

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String input = data.consumeRemainingAsString();

        // Test your component with the fuzzed input
        MyComponent.process(input);
    }
}
```

## What to look for?

Jazzer will automatically stop and report if it finds:
-   An unhandled exception that causes the JVM to crash (e.g., `OutOfMemoryError`, `StackOverflowError`).
-   Any security-relevant exceptions if configured.
-   Assertions that fail.

If Jazzer finds a "finding", it will create a `fuzz-test-*.repro` file. You can use this file to reproduce the exact input that caused the failure.

## GitHub Scorecard & Continuous Fuzzing

To satisfy the [GitHub Scorecard](https://github.com/ossf/scorecard) "Fuzzing" requirement, this project is configured to use **ClusterFuzzLite (CFL)**.

### How it works:

1.  **ClusterFuzzLite**: We have integrated ClusterFuzzLite via GitHub Actions (see `.clusterfuzzlite/` and `.github/workflows/cflite_*.yml`).
2.  **Continuous Testing**: 
    -   **PR Mode**: Every pull request triggers a short fuzzing session (5 minutes) to catch regressions before they are merged. This is configured to run on all pull requests with the target branch being `main`.
    -   **Batch Mode**: A longer daily fuzzing session (1 hour) runs on the `main` branch to discover deeper issues.
    -   **Manual Trigger**: Fuzzing workflows can also be triggered manually via the GitHub Actions "Run workflow" button.
3.  **Scorecard Recognition**: By having these workflows in place and running them, the OpenSSF Scorecard will recognize that the project is being actively fuzzed, which improves our security score.

### Configuration

-   `.clusterfuzzlite/Dockerfile`: Defines the build environment (based on `oss-fuzz-base`).
-   `.clusterfuzzlite/build.sh`: Script that compiles the application and prepares the fuzzing targets for Jazzer.
-   `.github/workflows/cflite_pr.yml`: GitHub Action for PR fuzzing.
-   `.github/workflows/cflite_batch.yml`: GitHub Action for scheduled batch fuzzing.
