package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.dubbo.NativeDubboBridge;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.admission.CustomerCommandKeyAdmission;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.concurrent.CompletableFuture;

@Component
public final class HealthHandler {

    @Autowired
    private NestedCatalogClient catalogClient;

    @Autowired
    private CustomerQueryClient customerQueryClient;

    @Autowired
    private CustomerCommandKeyAdmission customerCommandKeyAdmission;

    public HealthHandler() {
    }

    public HealthHandler(NestedCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @GetMapping(value = "/app/health", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"UP\",\"app\":\"rest-sample-dubbo-consumer\"}",
                "application/json; charset=utf-8"));
    }

    @GetMapping(value = "/app/ready", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> ready() {
        CompletableFuture<DependencyCheck> catalog = checkCatalog();
        CompletableFuture<DependencyCheck> customers = checkCustomers();
        return catalog.thenCombine(customers, HealthHandler::readyResponse);
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

    @GetMapping(value = "/app/command-key-admission", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> commandKeyAdmission() {
        String body = customerCommandKeyAdmission == null
                ? "{\"enabled\":false,\"accepted\":0,\"rejected\":0}"
                : customerCommandKeyAdmission.metricsJson();
        return ResponseEntity.ok(RawResponse.text(body, "application/json; charset=utf-8"));
    }

    @GetMapping(value = "/app/metrics/reset", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> resetMetrics() {
        NativeBridge.nativeResetMetrics();
        NativeDubboBridge.resetMetrics();
        if (customerCommandKeyAdmission != null) {
            customerCommandKeyAdmission.reset();
        }
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"reset\"}",
                "application/json; charset=utf-8"));
    }

    private CompletableFuture<DependencyCheck> checkCatalog() {
        if (catalogClient == null) {
            return CompletableFuture.completedFuture(DependencyCheck.skipped("catalog"));
        }
        return catalogClient.nestedCatalogJsonAsync()
                .thenApply(bytes -> DependencyCheck.up("catalog", bytes == null ? 0 : bytes.length))
                .exceptionally(error -> DependencyCheck.down("catalog", rootMessage(error)));
    }

    private CompletableFuture<DependencyCheck> checkCustomers() {
        if (customerQueryClient == null) {
            return CompletableFuture.completedFuture(DependencyCheck.skipped("customers"));
        }
        return customerQueryClient.databaseCustomersJsonAsync()
                .thenApply(bytes -> DependencyCheck.up("customers", bytes == null ? 0 : bytes.length))
                .exceptionally(error -> DependencyCheck.down("customers", rootMessage(error)));
    }

    private static ResponseEntity<RawResponse> readyResponse(DependencyCheck catalog, DependencyCheck customers) {
        boolean up = catalog.healthyForReadiness() && customers.healthyForReadiness();
        String body = "{\"status\":\"" + (up ? "UP" : "DOWN") + "\","
                + "\"app\":\"rest-sample-dubbo-consumer\","
                + "\"checks\":[" + catalog.toJson() + "," + customers.toJson() + "]}";
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

    private record DependencyCheck(String name, String status, boolean required, int bytes, String message) {

        static DependencyCheck up(String name, int bytes) {
            return new DependencyCheck(name, "UP", true, bytes, "");
        }

        static DependencyCheck down(String name, String message) {
            return new DependencyCheck(name, "DOWN", true, 0, message);
        }

        static DependencyCheck skipped(String name) {
            return new DependencyCheck(name, "SKIPPED", false, 0, "");
        }

        boolean healthyForReadiness() {
            return !required || "UP".equals(status);
        }

        String toJson() {
            return "{\"name\":\"" + escape(name) + "\","
                    + "\"status\":\"" + status + "\","
                    + "\"required\":" + required + ","
                    + "\"bytes\":" + bytes + ","
                    + "\"message\":\"" + escape(message) + "\"}";
        }
    }
}
