package nl.rijksoverheid.moz.services;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.Guard;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test de circuit-breaker configuratie van {@link VerificatieServiceGuard}.
 *
 * <p>De guard wordt hier direct via de constructor opgebouwd met testwaarden, zodat het
 * openen van de circuit breaker getest kan worden zonder de applicatie-configuratie aan te
 * passen. Draait als {@code @QuarkusTest} omdat {@code Guard.create()} de CDI-container
 * nodig heeft.
 */
@QuarkusTest
class VerificatieServiceGuardTest {

    private static final long LANGE_DELAY_SECONDEN = 30;

    private static VerificatieServiceGuard guardMetDrempel(int requestVolumeThreshold) {
        return new VerificatieServiceGuard(requestVolumeThreshold, 1.0, LANGE_DELAY_SECONDEN, 2);
    }

    private static Callable<String> falendeAanroep() {
        return () -> {
            throw new IllegalStateException("aanroep mislukt");
        };
    }

    @Test
    void get_NaConstructie_LevertGuardOp() {
        VerificatieServiceGuard verificatieServiceGuard = guardMetDrempel(5);

        assertNotNull(verificatieServiceGuard.get());
    }

    @Test
    void get_MeerdereAanroepen_LevertZelfdeGuardOp() {
        VerificatieServiceGuard verificatieServiceGuard = guardMetDrempel(5);

        assertSame(verificatieServiceGuard.get(), verificatieServiceGuard.get());
    }

    @Test
    void call_ZonderFouten_LevertResultaatOp() throws Exception {
        VerificatieServiceGuard verificatieServiceGuard = guardMetDrempel(5);

        assertEquals("ok", verificatieServiceGuard.get().call(() -> "ok", String.class));
    }

    @Test
    void call_NaBereikenVanDrempel_OpentCircuitBreaker() {
        VerificatieServiceGuard verificatieServiceGuard = guardMetDrempel(2);
        Guard guard = verificatieServiceGuard.get();
        Callable<String> falend = falendeAanroep();

        // requestVolumeThreshold = 2 en failureRatio = 1.0: twee mislukte aanroepen openen de breaker.
        assertThrows(IllegalStateException.class, () -> guard.call(falend, String.class));
        assertThrows(IllegalStateException.class, () -> guard.call(falend, String.class));

        // De breaker staat nu open; de aanroep wordt niet meer uitgevoerd.
        assertThrows(CircuitBreakerOpenException.class, () -> guard.call(falend, String.class));
    }

    @Test
    void reset_NaOpenenVanCircuitBreaker_LevertWerkendeGuardOp() throws Exception {
        VerificatieServiceGuard verificatieServiceGuard = guardMetDrempel(2);
        Guard geopendeGuard = verificatieServiceGuard.get();
        Callable<String> falend = falendeAanroep();

        assertThrows(IllegalStateException.class, () -> geopendeGuard.call(falend, String.class));
        assertThrows(IllegalStateException.class, () -> geopendeGuard.call(falend, String.class));
        assertThrows(CircuitBreakerOpenException.class, () -> geopendeGuard.call(falend, String.class));

        verificatieServiceGuard.reset();

        assertNotSame(geopendeGuard, verificatieServiceGuard.get());
        assertEquals("ok", verificatieServiceGuard.get().call(() -> "ok", String.class));
    }
}
