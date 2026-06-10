package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.DubboConsumerException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboReadRetryTest {

    @Test
    void retriesConnectionAbortOnceForReadCalls() {
        AtomicInteger attempts = new AtomicInteger();

        String value = DubboReadRetry.onceOnConnectionAbort(true, () -> {
            if (attempts.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new DubboConsumerException(
                        "Dubbo provider 127.0.0.1:20880 I/O error: "
                                + "An established connection was aborted by the software in your host machine. "
                                + "(os error 10053)"
                ));
            }
            return CompletableFuture.completedFuture("ok");
        }).join();

        assertEquals("ok", value);
        assertEquals(2, attempts.get());
    }

    @Test
    void doesNotRetryProviderProtocolErrors() {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = DubboReadRetry.onceOnConnectionAbort(true, () -> {
            attempts.incrementAndGet();
            return CompletableFuture.failedFuture(new DubboConsumerException(
                    "Dubbo provider 127.0.0.1:20880 returned status 40"
            ));
        });

        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, attempts.get());
    }

    @Test
    void leavesRetryDisabledWhenConfiguredOff() {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = DubboReadRetry.onceOnConnectionAbort(false, () -> {
            attempts.incrementAndGet();
            return CompletableFuture.failedFuture(new DubboConsumerException(
                    "Dubbo provider 127.0.0.1:20880 I/O error: Connection reset by peer"
            ));
        });

        assertTrue(result.isCompletedExceptionally());
        assertFalse(result.isDone() && !result.isCompletedExceptionally());
        assertEquals(1, attempts.get());
    }
}
