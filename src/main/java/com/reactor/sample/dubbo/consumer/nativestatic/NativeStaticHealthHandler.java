package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.dubbo.NativeDubboBridge;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.JsonResponses;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.dependencyUnavailable;

@RestController
public final class NativeStaticHealthHandler {

    private static final RawResponse HEALTH_RESPONSE = RawResponse.json(
            "{\"status\":\"UP\",\"app\":\"rest-sample-dubbo-consumer\",\"image\":\"native-static\"}"
                    .getBytes(StandardCharsets.UTF_8));

    private final NativeStaticCatalogClient dubboClient;

    public NativeStaticHealthHandler(NativeStaticCatalogClient dubboClient) {
        this.dubboClient = dubboClient;
    }

    @GetMapping("/app/health")
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(HEALTH_RESPONSE);
    }

    @GetMapping("/app/ready")
    public CompletableFuture<ResponseEntity<RawResponse>> ready() {
        return dubboClient.getNestedCatalogJsonNativeJsonAsync()
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(JsonResponses.error(
                                "dubbo_catalog_readiness_unavailable",
                                dependencyUnavailable("dubbo_catalog_readiness_unavailable", error))));
    }

    @GetMapping("/app/native-metrics")
    public ResponseEntity<RawResponse> nativeMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                NativeBridge.nativeMetricsPrometheus(),
                "text/plain; charset=utf-8"));
    }

    @GetMapping("/app/native-diagnostics")
    public ResponseEntity<RawResponse> nativeDiagnostics() {
        return ResponseEntity.ok(RawResponse.text(
                NativeBridge.nativeMemoryDiagnosticsJson(),
                "application/json; charset=utf-8"));
    }

    @GetMapping("/app/metrics/reset")
    public ResponseEntity<RawResponse> resetMetrics() {
        NativeBridge.nativeResetMetrics();
        NativeDubboBridge.resetMetrics();
        return ResponseEntity.ok(JsonResponses.stringField("status", "reset"));
    }
}
