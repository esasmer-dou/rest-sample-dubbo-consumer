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
import com.reactor.sample.dubbo.consumer.config.ConsumerReadinessContracts;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.dependencyUnavailable;

@RestController
public final class HealthHandler {

    private final ConsumerReadinessContracts contracts;
    private final Supplier<CompletableFuture<byte[]>> catalogProbe;
    private final Supplier<CompletableFuture<byte[]>> customerProbe;
    private final LongKeyAdmission customerCommandKeyAdmission;

    // AOT may read generated client types before their generation round; keep them qualified.
    @Autowired
    public HealthHandler(
            Optional<com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient> catalogClient,
            Optional<com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient> customerQueryClient,
            Optional<LongKeyAdmission> customerCommandKeyAdmission,
            ConsumerReadinessContracts contracts) {
        this(contracts,
                catalogClient.isPresent() ? catalogClient.get()::getNestedCatalogJsonAsync : null,
                customerQueryClient.isPresent() ? customerQueryClient.get()::getDatabaseCustomersJsonAsync : null,
                customerCommandKeyAdmission.orElse(null));
    }

    HealthHandler(ConsumerReadinessContracts contracts,
            Supplier<CompletableFuture<byte[]>> catalogProbe,
            Supplier<CompletableFuture<byte[]>> customerProbe,
            LongKeyAdmission customerCommandKeyAdmission) {
        this.contracts = Objects.requireNonNull(contracts, "contracts");
        this.catalogProbe = catalogProbe;
        this.customerProbe = customerProbe;
        this.customerCommandKeyAdmission = customerCommandKeyAdmission;
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
        return check("catalog", contracts.catalogRequired(), catalogProbe,
                "dubbo_catalog_readiness_unavailable");
    }

    private CompletableFuture<DependencyCheckResponse> checkCustomers() {
        return check("customers", contracts.customersRequired(), customerProbe,
                "dubbo_customer_readiness_unavailable");
    }

    private static CompletableFuture<DependencyCheckResponse> check(String name, boolean required,
            Supplier<CompletableFuture<byte[]>> probe, String failureCode) {
        if (!required) {
            return CompletableFuture.completedFuture(skipped(name));
        }
        if (probe == null) {
            return CompletableFuture.completedFuture(down(name, failureCode));
        }
        try {
            return Objects.requireNonNull(probe.get(), "readiness future")
                    .thenApply(bytes -> up(name, bytes == null ? 0 : bytes.length))
                    .exceptionally(error -> down(name, dependencyUnavailable(failureCode, error)));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(down(name, dependencyUnavailable(failureCode, error)));
        }
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
