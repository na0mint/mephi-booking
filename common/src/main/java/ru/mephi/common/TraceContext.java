package ru.mephi.common;

import java.util.Map;
import org.slf4j.MDC;
/** Restores the previous MDC, including when scopes are nested during orchestration. */
public final class TraceContext implements AutoCloseable {
    private final Map<String, String> previous;
    private TraceContext(String traceId, String bookingId) {
        previous = MDC.getCopyOfContextMap();
        MDC.put("traceId", TraceIds.safe(traceId));
        if (bookingId != null)
            MDC.put("bookingId", bookingId);
    }
    public static TraceContext open(String traceId, String bookingId) {
        return new TraceContext(traceId, bookingId);
    }
    @Override
    public void close() {
        if (previous == null)
            MDC.clear();
        else
            MDC.setContextMap(previous);
    }
}
