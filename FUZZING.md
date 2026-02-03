# Fuzz Testing in Moza Profiel Service

To boost the security of our application, we have implemented fuzz testing for our REST endpoints. Fuzzing is a testing technique that provides semi-random data as input to the application to find bugs, crashes, or security vulnerabilities.

## What has been implemented?

1.  **Dependency**: Added `com.code-intelligence:jazzer-junit` to the `pom.xml`. Jazzer is a coverage-guided fuzzer for the JVM.
2.  **Fuzz Test Suite**: Created `src/test/java/nl/rijksoverheid/moz/fuzzing/EndpointFuzzTest.java`. This class contains examples of how to fuzz our REST endpoints using Jazzer and QuarkusTest. It covers all primary controllers: `ProfielController`, `DienstverlenerController`, and `EmailVerificatieController`.
3.  **RestAssured Integration**: The fuzz tests use `RestAssured` to send fuzzed data to the running Quarkus application.

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

To add a new fuzz test:

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
    -   **PR Mode**: Every pull request triggers a short fuzzing session (5 minutes) to catch regressions before they are merged. This is configured to run on all pull requests regardless of the source or target branch.
    -   **Batch Mode**: A longer daily fuzzing session (1 hour) runs on the `main` branch to discover deeper issues.
    -   **Manual Trigger**: Fuzzing workflows can also be triggered manually via the GitHub Actions "Run workflow" button.
3.  **Scorecard Recognition**: By having these workflows in place and running them, the OpenSSF Scorecard will recognize that the project is being actively fuzzed, which improves our security score.

### Configuration

-   `.clusterfuzzlite/Dockerfile`: Defines the build environment (based on `oss-fuzz-base`).
-   `.clusterfuzzlite/build.sh`: Script that compiles the application and prepares the fuzzing targets for Jazzer.
-   `.github/workflows/cflite_pr.yml`: GitHub Action for PR fuzzing.
-   `.github/workflows/cflite_batch.yml`: GitHub Action for scheduled batch fuzzing.
