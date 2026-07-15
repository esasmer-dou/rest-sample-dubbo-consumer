package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.dubbo.NativeDubboBridge;
import com.reactor.rust.dubbo.sample.dto.ApplicationReadinessResponse;
import com.reactor.rust.dubbo.sample.dto.ApplicationRuntimeHealthResponse;
import com.reactor.rust.dubbo.sample.dto.DependencyCheckResponse;
import com.reactor.rust.dubbo.sample.dto.StatusResponse;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.reactor.rust.http.JsonResponses.body;
import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.dependencyUnavailable;

public final class NativeStaticHealthHandler {

    private final NativeStaticDubboClient dubboClient;

    public NativeStaticHealthHandler(NativeStaticDubboClient dubboClient) {
        this.dubboClient = dubboClient;
    }

    @GetMapping(value = "/app/health", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(body(new ApplicationRuntimeHealthResponse(
                "UP",
                "rest-sample-dubbo-consumer",
                "native-static")));
    }

    @GetMapping(value = "/app/ready", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> ready() {
        return dubboClient.nestedCatalogJsonAsync()
                .thenApply(bytes -> readyResponse(true, bytes == null ? 0 : bytes.length, ""))
                .exceptionally(error -> readyResponse(
                        false,
                        0,
                        dependencyUnavailable("dubbo_catalog_readiness_unavailable", error)));
    }

    @GetMapping(value = "/app/native-metrics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> nativeMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                NativeBridge.nativeMetricsPrometheus(),
                "text/plain; charset=utf-8"));
    }

    @GetMapping(value = "/app/native-diagnostics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> nativeDiagnostics() {
        return ResponseEntity.ok(RawResponse.text(
                NativeBridge.nativeMemoryDiagnosticsJson(),
                "application/json; charset=utf-8"));
    }

    @GetMapping(value = "/app/metrics/reset", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> resetMetrics() {
        NativeBridge.nativeResetMetrics();
        NativeDubboBridge.resetMetrics();
        return ResponseEntity.ok(body(new StatusResponse("reset")));
    }

    private static ResponseEntity<RawResponse> readyResponse(boolean up, int bytes, String message) {
        String status = up ? "UP" : "DOWN";
        RawResponse response = body(new ApplicationReadinessResponse(
                status,
                "rest-sample-dubbo-consumer",
                List.of(new DependencyCheckResponse("catalog", status, true, bytes, message))));
        return up
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
