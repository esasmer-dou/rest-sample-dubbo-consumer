package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.concurrent.LongKeyAdmission;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.dubbo.NativeDubboBridge;
import com.reactor.rust.dubbo.sample.dto.ApplicationHealthResponse;
import com.reactor.rust.dubbo.sample.dto.ApplicationReadinessResponse;
import com.reactor.rust.dubbo.sample.dto.DependencyCheckResponse;
import com.reactor.rust.dubbo.sample.dto.StatusResponse;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.JsonResponses;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.dependencyUnavailable;

@RestController
public final class HealthHandler {

    private final NestedCatalogClient catalogClient;
    private final CustomerQueryClient customerQueryClient;
    private final LongKeyAdmission customerCommandKeyAdmission;

    @Autowired
    public HealthHandler(
            NestedCatalogClient catalogClient,
            Optional<CustomerQueryClient> customerQueryClient,
            Optional<LongKeyAdmission> customerCommandKeyAdmission) {
        this.catalogClient = catalogClient;
        this.customerQueryClient = customerQueryClient.orElse(null);
        this.customerCommandKeyAdmission = customerCommandKeyAdmission.orElse(null);
    }

    @GetMapping("/app/health")
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(JsonResponses.body(
                new ApplicationHealthResponse("UP", "rest-sample-dubbo-consumer")));
    }

    @GetMapping("/app/ready")
    public CompletableFuture<ResponseEntity<RawResponse>> ready() {
        CompletableFuture<DependencyCheckResponse> catalog = checkCatalog();
        CompletableFuture<DependencyCheckResponse> customers = checkCustomers();
        return catalog.thenCombine(customers, HealthHandler::readyResponse);
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

    @GetMapping("/app/command-key-admission")
    public ResponseEntity<RawResponse> commandKeyAdmission() {
        String body = customerCommandKeyAdmission == null
                ? "{\"enabled\":false,\"accepted\":0,\"rejected\":0}"
                : customerCommandKeyAdmission.metricsJson();
        return ResponseEntity.ok(RawResponse.text(body, "application/json; charset=utf-8"));
    }

    @GetMapping("/app/metrics/reset")
    public ResponseEntity<RawResponse> resetMetrics() {
        NativeBridge.nativeResetMetrics();
        NativeDubboBridge.resetMetrics();
        if (customerCommandKeyAdmission != null) {
            customerCommandKeyAdmission.reset();
        }
        return ResponseEntity.ok(JsonResponses.body(new StatusResponse("reset")));
    }

    private CompletableFuture<DependencyCheckResponse> checkCatalog() {
        if (catalogClient == null) {
            return CompletableFuture.completedFuture(skipped("catalog"));
        }
        return catalogClient.getNestedCatalogJsonAsync()
                .thenApply(bytes -> up("catalog", bytes == null ? 0 : bytes.length))
                .exceptionally(error -> down(
                        "catalog",
                        dependencyUnavailable("dubbo_catalog_readiness_unavailable", error)));
    }

    private CompletableFuture<DependencyCheckResponse> checkCustomers() {
        if (customerQueryClient == null) {
            return CompletableFuture.completedFuture(skipped("customers"));
        }
        return customerQueryClient.getDatabaseCustomersJsonAsync()
                .thenApply(bytes -> up("customers", bytes == null ? 0 : bytes.length))
                .exceptionally(error -> down(
                        "customers",
                        dependencyUnavailable("dubbo_customer_readiness_unavailable", error)));
    }

    private static ResponseEntity<RawResponse> readyResponse(
            DependencyCheckResponse catalog,
            DependencyCheckResponse customers) {
        boolean up = healthy(catalog) && healthy(customers);
        RawResponse response = JsonResponses.body(new ApplicationReadinessResponse(
                up ? "UP" : "DOWN",
                "rest-sample-dubbo-consumer",
                List.of(catalog, customers)));
        return up
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private static DependencyCheckResponse up(String name, int bytes) {
        return new DependencyCheckResponse(name, "UP", true, bytes, "");
    }

    private static DependencyCheckResponse down(String name, String message) {
        return new DependencyCheckResponse(name, "DOWN", true, 0, message);
    }

    private static DependencyCheckResponse skipped(String name) {
        return new DependencyCheckResponse(name, "SKIPPED", false, 0, "");
    }

    private static boolean healthy(DependencyCheckResponse check) {
        return !check.required() || "UP".equals(check.status());
    }
}
