package nl.rijksoverheid.moz.services;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test het circuit-breaker gedrag van {@link VerificatieServiceGuard}.
 *
 * <p>De guard wordt hier direct via de constructor opgebouwd, met een lagere drempel dan de
 * geconfigureerde. Daarmee is dit de enige plek die aantoont dat de meegegeven
 * {@code requestVolumeThreshold} ook echt in het juiste veld terechtkomt — de vier
 * constructor-parameters zijn positioneel en van vrijwel hetzelfde type. Het openen van de
 * breaker op zichzelf wordt daarnaast al gedekt door
 * {@code EmailVerificatieServiceTest.circuitBreaker_OpensAfterThresholdExceeded}, dat via de
 * CDI-beheerde guard op de geconfigureerde drempel van 5 loopt.
 *
 * <p>Draait als {@code @QuarkusTest} omdat de smallrye Guard-API via {@code CDI.current()}
 * wordt opgelost ({@code CdiSpi}); zonder container faalt {@code Guard.create()} met een
 * misleidende "Could not find implementation of Spi"-fout.
 */
@QuarkusTest
class VerificatieServiceGuardTest {

    private static final int DREMPEL = 2;
    private static final double ALLE_AANROEPEN_MISLUKT = 1.0;
    private static final int SUCCESS_THRESHOLD = 2;

    /** Ruim langer dan de testduur, zodat de breaker tijdens de test niet vanzelf half-open gaat. */
    private static final long LANGE_DELAY_SECONDEN = 30;

    private static final Callable<String> FALENDE_AANROEP = () -> {
        throw new IllegalStateException("aanroep mislukt");
    };

    private static VerificatieServiceGuard nieuweGuard() {
        return new VerificatieServiceGuard(DREMPEL, ALLE_AANROEPEN_MISLUKT, LANGE_DELAY_SECONDEN, SUCCESS_THRESHOLD);
    }

    /** Maakt de drempel vol met mislukte aanroepen; daarna hoort de breaker open te staan. */
    private static void vulDrempelMetMislukteAanroepen(VerificatieServiceGuard verificatieServiceGuard) {
        for (int i = 0; i < DREMPEL; i++) {
            assertThrows(IllegalStateException.class,
                    () -> verificatieServiceGuard.get().call(FALENDE_AANROEP, String.class));
        }
    }

    @Test
    void get_ZonderFouten_LevertWerkendeGuardOp() throws Exception {
        VerificatieServiceGuard verificatieServiceGuard = nieuweGuard();

        assertEquals("ok", verificatieServiceGuard.get().call(() -> "ok", String.class));
    }

    /**
     * Elke {@code get()} moet dezelfde guard opleveren: bij een nieuwe guard per aanroep zou de
     * teller telkens opnieuw beginnen en zou de breaker nooit openen.
     */
    @Test
    void get_NaBereikenVanDrempel_OpentCircuitBreaker() {
        VerificatieServiceGuard verificatieServiceGuard = nieuweGuard();

        vulDrempelMetMislukteAanroepen(verificatieServiceGuard);

        // De breaker staat nu open; de aanroep wordt niet meer uitgevoerd.
        assertThrows(CircuitBreakerOpenException.class,
                () -> verificatieServiceGuard.get().call(FALENDE_AANROEP, String.class));
    }

    @Test
    void reset_NaOpenenVanCircuitBreaker_LevertWerkendeGuardOp() throws Exception {
        VerificatieServiceGuard verificatieServiceGuard = nieuweGuard();
        vulDrempelMetMislukteAanroepen(verificatieServiceGuard);
        assertThrows(CircuitBreakerOpenException.class,
                () -> verificatieServiceGuard.get().call(FALENDE_AANROEP, String.class));

        verificatieServiceGuard.reset();

        assertEquals("ok", verificatieServiceGuard.get().call(() -> "ok", String.class));
    }
}
