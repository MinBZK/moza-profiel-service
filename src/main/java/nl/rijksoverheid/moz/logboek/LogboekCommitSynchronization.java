package nl.rijksoverheid.moz.logboek;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.LogboekContext;
import nl.mijnoverheidzakelijk.ldv.logboekdataverwerking.ProcessingHandler;
import nl.rijksoverheid.moz.helper.HashHelper;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.function.Consumer;

// Eigen try/catch per identiteit: dit draait in de afterCompletion-callback, waar een exception
// nergens meer naartoe kan — zonder vangst stopt één falende span de rest van de batch.
class LogboekCommitSynchronization implements Synchronization {

    private static final Logger LOG = Logger.getLogger(LogboekCommitSynchronization.class);

    private final HashHelper hashHelper;
    private final ProcessingHandler processingHandler;
    private final List<GeauditeerdeIdentiteit> identiteiten;
    private final String naam;
    private final String processingActivityId;
    private final Consumer<Exception> opFout;

    LogboekCommitSynchronization(HashHelper hashHelper, ProcessingHandler processingHandler,
            List<GeauditeerdeIdentiteit> identiteiten, String naam, String processingActivityId,
            Consumer<Exception> opFout) {
        this.hashHelper = hashHelper;
        this.processingHandler = processingHandler;
        this.identiteiten = identiteiten;
        this.naam = naam;
        this.processingActivityId = processingActivityId;
        this.opFout = opFout;
    }

    @Override
    public void beforeCompletion() {
    }

    @Override
    public void afterCompletion(int status) {
        if (status == Status.STATUS_ROLLEDBACK) {
            return;
        }

        // Elke andere niet-gecommitte uitkomst (bv. STATUS_UNKNOWN bij een 2PC in doubt) is geen
        // verwachte afloop en zou anders niet te onderscheiden zijn van "nooit gedraaid".
        if (status != Status.STATUS_COMMITTED) {
            LOG.warn("Logboek: transactie eindigde met status " + status + "; " + identiteiten.size()
                    + " vermelding(en) niet geëmitteerd");

            return;
        }

        for (GeauditeerdeIdentiteit identiteit : identiteiten) {
            try {
                LogboekContext ctx = new LogboekContext();
                ctx.setProcessingActivityId(processingActivityId);
                ctx.setDataSubjectId(hashHelper.hashIdentifier(identiteit.identificatieNummer()));
                ctx.setDataSubjectType(String.valueOf(identiteit.identificatieType()));
                ctx.setStatus(StatusCode.OK);
                Span span = processingHandler.startSpan(naam, Context.current());
                processingHandler.addLogboekContextToSpan(span, ctx);
                span.end();
            } catch (Exception e) {
                opFout.accept(e);
            }
        }
    }
}
