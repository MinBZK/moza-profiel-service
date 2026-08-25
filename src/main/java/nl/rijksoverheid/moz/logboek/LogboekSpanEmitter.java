package nl.rijksoverheid.moz.logboek;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.TransactionSynchronizationRegistry;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.helper.HashHelper;

import java.util.List;
import java.util.function.Consumer;

// Emitteert pas ná commit: een logboek-span mag nooit een verwijdering claimen die niet heeft
// plaatsgevonden. Garandeert alleen de volgorde, niet dat de span de exporter haalt.
@ApplicationScoped
public class LogboekSpanEmitter {

    private final HashHelper hashHelper;
    private final ProcessingHandler processingHandler;
    private final TransactionSynchronizationRegistry txSyncRegistry;

    public LogboekSpanEmitter(HashHelper hashHelper, ProcessingHandler processingHandler,
            TransactionSynchronizationRegistry txSyncRegistry) {
        this.hashHelper = hashHelper;
        this.processingHandler = processingHandler;
        this.txSyncRegistry = txSyncRegistry;
    }

    // opFout: aanroeper bepaalt zelf hoe een gemiste span gemeld wordt (loggen, eventueel een
    // eigen metriek) — deze klasse kent de telemetrie-behoefte van de aanroepers niet.
    public void registreerNaCommit(List<GeauditeerdeIdentiteit> identiteiten, String naam,
            String processingActivityId, Consumer<Exception> opFout) {
        if (identiteiten.isEmpty()) {
            return;
        }

        txSyncRegistry.registerInterposedSynchronization(
                new LogboekCommitSynchronization(hashHelper, processingHandler, identiteiten, naam, processingActivityId, opFout));
    }
}
