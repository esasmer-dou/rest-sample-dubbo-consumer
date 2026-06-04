package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.DeleteMapping;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.MaxRequestBodySize;
import com.reactor.rust.annotations.PatchMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.PostMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RequestBody;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequestMapping("/api/v1/customers")
public final class CustomerHandler {

    @Autowired
    private CustomerQueryClient customerQueryClient;

    @Autowired
    private CustomerCommandClient customerCommandClient;

    @GetMapping(value = "/db", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> databaseCustomers() {
        return customerQueryClient.databaseCustomersJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_customer_provider_unavailable", rootMessage(error))));
    }

    @PostMapping(value = "", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(32768)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody byte[] body) {
        return customerCommandClient.createCustomerAsync(body)
                .thenApply(json -> ResponseEntity.created(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_customer_create_unavailable", rootMessage(error))));
    }

    @PatchMapping(value = "/{id}/segment", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(16384)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerSegment(
            @PathVariable("id") long customerId,
            @RequestBody byte[] body) {
        return customerCommandClient.patchCustomerSegmentAsync(customerId, body)
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_customer_segment_unavailable", rootMessage(error))));
    }

    @PatchMapping(value = "/{id}/status", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(16384)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerStatus(
            @PathVariable("id") long customerId,
            @RequestBody byte[] body) {
        return customerCommandClient.patchCustomerStatusAsync(customerId, body)
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_customer_status_unavailable", rootMessage(error))));
    }

    @DeleteMapping(value = "/{id}", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(8192)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> deleteCustomer(
            @PathVariable("id") long customerId,
            @RequestBody(required = false) byte[] body) {
        byte[] command = body == null ? new byte[0] : body;
        return customerCommandClient.deleteCustomerAsync(customerId, command)
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("dubbo_customer_delete_unavailable", rootMessage(error))));
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
