package dev.quicknote.shared.correlation;

/** Where the request-scoped correlation id lives for the duration of a request. */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Request-Id";
    public static final String TRACEPARENT = "traceparent";

    private CorrelationContext() {}

    public static String current() {
        Object value = org.jboss.logmanager.MDC.get(MDC_KEY);
        return value == null ? null : value.toString();
    }

    public static void put(String correlationId) {
        org.jboss.logmanager.MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        org.jboss.logmanager.MDC.remove(MDC_KEY);
    }
}
