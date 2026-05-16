package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequestMapping("/api/v1/catalog")
public final class CatalogHandler {

    @Autowired
    private NestedCatalogClient catalogClient;

    @GetMapping(value = "/nested", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
        return catalogClient.nestedCatalogJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_provider_unavailable", rootMessage(error))));
    }

    @GetMapping(value = "/db/customers", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> databaseCustomers() {
        return catalogClient.databaseCustomersJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_database_provider_unavailable", rootMessage(error))));
    }

    @GetMapping(value = "/dubbo-metrics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> dubboMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                catalogClient.nativeMetricsJson(),
                "application/json; charset=utf-8"));
    }

    private static RawResponse error(String code, String message) {
        return RawResponse.text(
                "{\"code\":\"" + escapeJson(code) + "\",\"message\":\"" + escapeJson(message) + "\"}",
                "application/json; charset=utf-8");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
