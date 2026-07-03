package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.dubbo.NativeDubboBridge;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

public final class NativeStaticHealthHandler {

    private final NativeStaticDubboClient dubboClient;

    public NativeStaticHealthHandler(NativeStaticDubboClient dubboClient) {
        this.dubboClient = dubboClient;
    }

    @GetMapping(value = "/app/health", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"UP\",\"app\":\"rest-sample-dubbo-consumer\",\"image\":\"native-static\"}",
                "application/json; charset=utf-8"));
    }

    @GetMapping(value = "/app/ready", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> ready() {
        return dubboClient.nestedCatalogJsonAsync()
                .thenApply(bytes -> readyResponse(true, bytes == null ? 0 : bytes.length, ""))
                .exceptionally(error -> readyResponse(false, 0, rootMessage(error)));
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
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"reset\"}",
                "application/json; charset=utf-8"));
    }

    private static ResponseEntity<RawResponse> readyResponse(boolean up, int bytes, String message) {
        String body = "{\"status\":\"" + (up ? "UP" : "DOWN") + "\","
                + "\"app\":\"rest-sample-dubbo-consumer\","
                + "\"image\":\"native-static\","
                + "\"checks\":[{\"name\":\"catalog\","
                + "\"status\":\"" + (up ? "UP" : "DOWN") + "\","
                + "\"required\":true,"
                + "\"bytes\":" + bytes + ","
                + "\"message\":\"" + escape(message) + "\"}]}";
        RawResponse response = RawResponse.text(body, "application/json; charset=utf-8");
        return up
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
