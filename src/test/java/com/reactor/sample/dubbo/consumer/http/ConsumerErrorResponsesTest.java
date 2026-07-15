package com.reactor.sample.dubbo.consumer.http;

import com.reactor.rust.http.ResponseEntity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerErrorResponsesTest {

    @Test
    void hidesInternalProviderDetailsFromPublicResponse() {
        ResponseEntity<?> response = ConsumerErrorResponses.unavailable(
                "dubbo_provider_unavailable",
                new IllegalStateException("password=secret host=10.0.0.7"));

        assertEquals(503, response.getStatus().getCode());
        String body = new String(
                ((com.reactor.rust.http.RawResponse) response.getBody()).getBody(),
                StandardCharsets.UTF_8);
        assertTrue(body.contains("dubbo_provider_unavailable"));
        assertTrue(body.contains("temporarily unavailable"));
        assertFalse(body.contains("password=secret"));
        assertFalse(body.contains("10.0.0.7"));
    }

    @Test
    void redactsCredentialsFromInternalDiagnosticMessage() {
        String sanitized = ConsumerErrorResponses.sanitizeForLog(
                "password=secret token:abc redis://user:p4ss@redis:6379");

        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("abc"));
        assertFalse(sanitized.contains("p4ss"));
        assertTrue(sanitized.contains("<redacted>"));
    }
}
