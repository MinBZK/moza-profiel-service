package nl.rijksoverheid.moz.services;

import io.smallrye.faulttolerance.api.Guard;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class VerificatieServiceGuard {

    private final int requestVolumeThreshold;
    private final double failureRatio;
    private final long delay;
    private final int successThreshold;

    private Guard guard;

    public VerificatieServiceGuard(
            @ConfigProperty(name = "verificatie-service.circuit-breaker.request-volume-threshold", defaultValue = "5") int requestVolumeThreshold,
            @ConfigProperty(name = "verificatie-service.circuit-breaker.failure-ratio", defaultValue = "1.0") double failureRatio,
            @ConfigProperty(name = "verificatie-service.circuit-breaker.delay", defaultValue = "30") long delay,
            @ConfigProperty(name = "verificatie-service.circuit-breaker.success-threshold", defaultValue = "2") int successThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
        this.failureRatio = failureRatio;
        this.delay = delay;
        this.successThreshold = successThreshold;
        this.guard = buildGuard();
    }

    public Guard get() {
        return guard;
    }

    public void reset() {
        guard = buildGuard();
    }

    private Guard buildGuard() {
        return Guard.create()
                .withCircuitBreaker()
                    .requestVolumeThreshold(requestVolumeThreshold)
                    .failureRatio(failureRatio)
                    .delay(delay, ChronoUnit.SECONDS)
                    .successThreshold(successThreshold)
                    .done()
                .build();
    }
}
