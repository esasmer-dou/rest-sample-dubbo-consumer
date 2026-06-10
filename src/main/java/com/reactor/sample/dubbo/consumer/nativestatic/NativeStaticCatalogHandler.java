package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@RequestMapping("/api/v1/catalog")
public final class NativeStaticCatalogHandler {

    private final NativeStaticDubboClient catalogClient;

    public NativeStaticCatalogHandler(NativeStaticDubboClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @GetMapping(value = "/nested", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
        return catalogClient.nestedCatalogJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> unavailable("dubbo_provider_unavailable", error));
    }

    @GetMapping(value = "/dubbo-metrics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> dubboMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                catalogClient.nativeMetricsJson(),
                "application/json; charset=utf-8"));
    }

    private static ResponseEntity<RawResponse> unavailable(String code, Throwable error) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(RawResponse.text(
                        "{\"code\":\"" + code + "\",\"message\":\"" + escape(rootMessage(error)) + "\"}",
                        "application/json; charset=utf-8"
                ));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
