package nl.rijksoverheid.moz.logboek;

import io.opentelemetry.api.trace.Span;
import jakarta.transaction.Status;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.common.IdentificatieType;
import nl.rijksoverheid.moz.helper.HashHelper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

/**
 * afterCompletion beslist als enige óf er een vermelding komt. De service-tests committen altijd,
 * dus de rollback- en in-doubt-tak zijn alleen hier te raken.
 */
class LogboekCommitSynchronizationTest {

    private static final List<GeauditeerdeIdentiteit> IDENTITEITEN = List.of(
            new GeauditeerdeIdentiteit("123456789", IdentificatieType.BSN),
            new GeauditeerdeIdentiteit("87654321", IdentificatieType.KVK));

    private final ProcessingHandler processingHandler = Mockito.mock(ProcessingHandler.class);

    private LogboekCommitSynchronization synchronisatie() {
        Mockito.doReturn(Mockito.mock(Span.class))
                .when(processingHandler).startSpan(Mockito.anyString(), Mockito.any());

        return new LogboekCommitSynchronization(new HashHelper(Optional.of("test-pepper")), processingHandler, IDENTITEITEN,
                "verwijderPartij", "https://mijnoverheidzakelijk.nl/verwerkingsactiviteiten/PS-900",
                e -> {
                    throw new AssertionError("opFout hoort hier niet aangeroepen te worden", e);
                });
    }

    @Test
    void committed_EmitteertEenSpanPerIdentiteit() {
        synchronisatie().afterCompletion(Status.STATUS_COMMITTED);

        Mockito.verify(processingHandler, Mockito.times(IDENTITEITEN.size()))
                .startSpan(Mockito.eq("verwijderPartij"), Mockito.any());
    }

    @Test
    void rolledBack_EmitteertNiets() {
        synchronisatie().afterCompletion(Status.STATUS_ROLLEDBACK);

        Mockito.verify(processingHandler, Mockito.never()).startSpan(Mockito.anyString(), Mockito.any());
    }

    /** Alleen het uitblijven van spans is te toetsen; de waarschuwing zelf gaat naar de log. */
    @Test
    void onbekendeStatus_EmitteertNiets() {
        synchronisatie().afterCompletion(Status.STATUS_UNKNOWN);

        Mockito.verify(processingHandler, Mockito.never()).startSpan(Mockito.anyString(), Mockito.any());
    }
}
