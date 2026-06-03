package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequestMapping("/api/v1/customers")
public final class CustomerHandler {

    @Autowired
    private CustomerQueryClient customerQueryClient;

    @GetMapping(value = "/db", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> databaseCustomers() {
        return customerQueryClient.databaseCustomersJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_customer_provider_unavailable", rootMessage(error))));
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
