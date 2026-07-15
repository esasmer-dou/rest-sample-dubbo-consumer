package com.reactor.sample.dubbo.consumer.http;

import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.JsonResponses;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.rust.logging.FrameworkLogger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** Keeps internal provider details in logs and returns stable public errors. */
public final class ConsumerErrorResponses {

    private static final String PUBLIC_UNAVAILABLE = "Upstream service is temporarily unavailable";
    private static final long LOG_INTERVAL_MS = 5_000L;
    private static final ConcurrentHashMap<String, AtomicLong> LAST_LOG_BY_CODE = new ConcurrentHashMap<>();
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|pwd|token|authorization|api[-_]?key|secret)(\\s*[=:]\\s*)([^,;\\s]+)");
    private static final Pattern URI_CREDENTIALS = Pattern.compile("(://[^:/\\s]+:)([^@\\s]+)(@)");

    private ConsumerErrorResponses() {
    }

    public static ResponseEntity<RawResponse> unavailable(String code, Throwable error) {
        record(code, error);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(JsonResponses.error(code, PUBLIC_UNAVAILABLE));
    }

    public static String dependencyUnavailable(String code, Throwable error) {
        record(code, error);
        return PUBLIC_UNAVAILABLE;
    }

    private static void record(String code, Throwable error) {
        long now = System.currentTimeMillis();
        AtomicLong last = LAST_LOG_BY_CODE.computeIfAbsent(code, ignored -> new AtomicLong());
        long previous = last.get();
        if (now - previous < LOG_INTERVAL_MS || !last.compareAndSet(previous, now)) {
            return;
        }
        Throwable root = root(error);
        String message = root.getMessage();
        FrameworkLogger.error(
                "[DubboConsumer] " + code + ": " + root.getClass().getName()
                        + (message == null || message.isBlank() ? "" : ": " + sanitizeForLog(message)));
    }

    private static Throwable root(Throwable error) {
        if (error == null) {
            return new IllegalStateException("Unknown upstream failure");
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String bounded(String value) {
        String singleLine = value.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= 512 ? singleLine : singleLine.substring(0, 512);
    }

    static String sanitizeForLog(String value) {
        String redacted = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1$2<redacted>");
        redacted = URI_CREDENTIALS.matcher(redacted).replaceAll("$1<redacted>$3");
        return bounded(redacted);
    }

}
