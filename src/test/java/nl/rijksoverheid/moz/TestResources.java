package nl.rijksoverheid.moz;

import io.quarkus.test.common.QuarkusTestResource;

/**
 * Registers global test resources. Quarkus discovers @QuarkusTestResource on
 * any class in the test module; it applies to every @QuarkusTest.
 */
@QuarkusTestResource(EmbeddedPostgresTestResource.class)
public final class TestResources {

    private TestResources() {
    }
}
