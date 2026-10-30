package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.config.ConsumerReadinessContracts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HealthHandlerTest {
    @Test
    void fullReadinessStartsBothAsyncChecksAndWaitsForBoth() {
        var catalog = new CompletableFuture<byte[]>();
        var customers = new CompletableFuture<byte[]>();
        var calls = new AtomicInteger();
        var handler = new HealthHandler(ConsumerReadinessContracts.FULL,
                () -> { calls.incrementAndGet(); return catalog; },
                () -> { calls.incrementAndGet(); return customers; }, null);

        var response = handler.ready();
        assertEquals(2, calls.get());
        assertFalse(response.isDone());
        catalog.complete(new byte[3]);
        assertFalse(response.isDone());
        customers.complete(new byte[5]);
        assertEquals(200, response.join().getStatus().getCode());
        String json = body(response.join());
        assertTrue(json.contains("\"catalog\""));
        assertTrue(json.contains("\"customers\""));
        assertFalse(json.contains("SKIPPED"));
    }

    @Test
    void catalogOnlyDoesNotInvokeCustomerProbe() {
        var response = new HealthHandler(ConsumerReadinessContracts.CATALOG_ONLY,
                up(), () -> fail("customer contract must not be called"), null).ready().join();
        assertEquals(200, response.getStatus().getCode());
        assertTrue(body(response).contains("\"status\":\"SKIPPED\""));
        assertTrue(body(response).contains("\"required\":false"));
    }

    @Test
    void queryOnlyDoesNotNeedCatalogClientOrInvokeCatalogProbe() {
        for (Supplier<CompletableFuture<byte[]>> catalog : Arrays
                .<Supplier<CompletableFuture<byte[]>>>asList(null, () -> fail("catalog must not be called"))) {
            var response = new HealthHandler(ConsumerReadinessContracts.QUERY_ONLY,
                    catalog, up(), null).ready().join();
            assertEquals(200, response.getStatus().getCode());
            assertTrue(body(response).contains("\"status\":\"SKIPPED\""));
            assertTrue(body(response).contains("\"required\":false"));
            assertTrue(body(response).contains("\"required\":true"));
        }
    }

    @Test
    void missingRequiredClientIsDownNotSkipped() {
        assertDown(new HealthHandler(ConsumerReadinessContracts.FULL, null, up(), null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.FULL, up(), null, null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.CATALOG_ONLY, null, up(), null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.QUERY_ONLY, up(), null, null));
    }

    @Test
    void injectedMissingClientsAreReportedForEveryReadinessSelection() {
        for (ConsumerReadinessContracts contracts : ConsumerReadinessContracts.values()) {
            assertDown(new HealthHandler(Optional.empty(), Optional.empty(), Optional.empty(), contracts));
        }
    }

    @Test
    void requiredRpcFailuresAreDownInEverySelectionAndHideInternalDetails() {
        Supplier<CompletableFuture<byte[]>> failed = () -> CompletableFuture.failedFuture(
                new IllegalStateException("password=secret host=10.0.0.7"));
        assertDown(new HealthHandler(ConsumerReadinessContracts.FULL, failed, up(), null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.FULL, up(), failed, null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.CATALOG_ONLY, failed, up(), null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.QUERY_ONLY, up(), failed, null));
    }

    @Test
    void synchronousDispatchFailureAndNullFutureAreDown() {
        assertDown(new HealthHandler(ConsumerReadinessContracts.QUERY_ONLY, null,
                () -> { throw new IllegalStateException("dispatch failed"); }, null));
        assertDown(new HealthHandler(ConsumerReadinessContracts.QUERY_ONLY, null, () -> null, null));
    }

    @Test
    void requiredProviderFailureRecoversAndLaterFailureReturnsDownWithoutRestart() {
        for (ConsumerReadinessContracts contracts : ConsumerReadinessContracts.values()) {
            var available = new java.util.concurrent.atomic.AtomicBoolean();
            var calls = new AtomicInteger();
            Supplier<CompletableFuture<byte[]>> provider = () -> {
                calls.incrementAndGet();
                return available.get()
                        ? CompletableFuture.completedFuture(new byte[1])
                        : CompletableFuture.failedFuture(new IllegalStateException("provider unavailable"));
            };
            var handler = new HealthHandler(contracts, provider, provider, null);

            assertDown(handler);
            available.set(true);
            var recovered = handler.ready().join();
            assertEquals(200, recovered.getStatus().getCode());
            assertTrue(body(recovered).contains("\"status\":\"UP\""));
            available.set(false);
            assertDown(handler);
            assertEquals(contracts == ConsumerReadinessContracts.FULL ? 6 : 3, calls.get());
        }
    }

    @Test
    void livenessNeverCallsDependencies() {
        var handler = new HealthHandler(ConsumerReadinessContracts.FULL,
                () -> fail("liveness must stay local"), () -> fail("liveness must stay local"), null);
        assertEquals(200, handler.health().getStatus().getCode());
    }

    private static Supplier<CompletableFuture<byte[]>> up() {
        return () -> CompletableFuture.completedFuture(new byte[1]);
    }

    private static void assertDown(HealthHandler handler) {
        var response = handler.ready().join();
        assertEquals(503, response.getStatus().getCode());
        String json = body(response);
        assertTrue(json.contains("\"status\":\"DOWN\""));
        assertTrue(json.contains("\"required\":true"));
        assertFalse(json.contains("password=secret"));
        assertFalse(json.contains("10.0.0.7"));
    }

    private static String body(ResponseEntity<RawResponse> response) {
        return new String(response.getBody().getBody(), StandardCharsets.UTF_8);
    }
}
