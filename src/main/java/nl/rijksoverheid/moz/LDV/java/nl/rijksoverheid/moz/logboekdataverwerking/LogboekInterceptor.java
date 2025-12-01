package LDV.java.nl.rijksoverheid.moz.logboekdataverwerking;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

@Logboek
@Interceptor
public class LogboekInterceptor {

    @Inject
    LogboekContext logboekContext;

    @Context
    HttpHeaders headers;

    @Inject
    ProcessingHandler handler;

    @AroundInvoke
    public Object log(InvocationContext context) throws Exception {

        var propagatorInstance = W3CTraceContextPropagator.getInstance();
        io.opentelemetry.context.Context traceContext = propagatorInstance.extract(
                io.opentelemetry.context.Context.current(), 
                headers, 
                new HttpHeadersGetter()
        );

        Logboek annotation = context.getMethod().getAnnotation(Logboek.class);
        String name = annotation.name();
        String processingActivityId = annotation.processingActivityId();

        Span span = handler.startSpan(name, traceContext);

        try (var scoped = span.makeCurrent()) {
            return context.proceed();
        } catch (Exception e){
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            throw e;
        }
        finally {
            SpanData spanData = ((ReadableSpan) span).toSpanData();

            if (headers.getHeaderString("traceparent") != null) {
                span.setAttribute("dpl.core.foreign_operation.span_id", spanData.getParentSpanId());

                //todo hoe krijgen we de url, header ofzo. komt nog
                span.setAttribute("dpl.core.foreign_operation.processor", headers.getHeaderString("traceparent-processor"));
            }

            logboekContext.setProcessingActivityId(processingActivityId);
            handler.addLogboekContextToSpan(span, logboekContext);
            span.end();
        }
    }


    private static class HttpHeadersGetter implements TextMapGetter<HttpHeaders> {

        @Override
        public Iterable<String> keys(HttpHeaders httpHeaders) {
            return httpHeaders.getRequestHeaders().keySet();
        }

        @Override
        public String get(HttpHeaders httpHeaders, String key) {
            assert httpHeaders != null;
            return httpHeaders.getHeaderString(key);
        }
    }
}


