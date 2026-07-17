package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.DeleteMapping;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.MaxRequestBodySize;
import com.reactor.rust.annotations.PatchMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.PostMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RequestBody;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.sample.dubbo.consumer.admission.CustomerCommandKeyAdmission;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;

import java.util.concurrent.CompletableFuture;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.unavailable;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_RAW_CREATE;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_RAW_MUTATION;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_RAW_READ;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_TYPED_CREATE;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_TYPED_MUTATION;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_TYPED_READ;

@Component
@RequestMapping("/api/v1/customers")
public final class CustomerHandler {

    @Autowired
    private CustomerQueryClient customerQueryClient;

    @Autowired
    private CustomerCommandClient customerCommandClient;

    @Autowired
    private CustomerCommandKeyAdmission customerCommandKeyAdmission;

    @GetMapping(value = "/db", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> databaseCustomers() {
        return customerQueryClient.getDatabaseCustomersJsonNativeJsonAsync()
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_customer_provider_unavailable", error));
    }

    @GetMapping(value = "/db/stats", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_TYPED_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerStats() {
        return customerQueryClient.getCustomerStatsAsync()
                .thenApply(stats -> ResponseEntity.ok(JsonResponseSupport.customerStats(stats)))
                .exceptionally(error -> unavailable("dubbo_customer_stats_unavailable", error));
    }

    @GetMapping(value = "/db/by-segment", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_TYPED_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customersBySegment(
            @RequestParam(value = "segment", defaultValue = "standard") String segment,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return customerQueryClient.findCustomersBySegmentAsync(segment, limit)
                .thenApply(customers -> ResponseEntity.ok(JsonResponseSupport.customerList(customers)))
                .exceptionally(error -> unavailable("dubbo_customer_segment_query_unavailable", error));
    }

    @GetMapping(value = "/db/{id}", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerById(@PathVariable("id") long customerId) {
        return customerQueryClient.getCustomerAsync(customerId)
                .thenApply(customer -> customer == null
                        ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(JsonResponseSupport.error("customer_not_found", Long.toString(customerId)))
                        : ResponseEntity.ok(JsonResponseSupport.customerSummary(customer)))
                .exceptionally(error -> unavailable("dubbo_customer_get_unavailable", error));
    }

    @GetMapping(value = "/{id}/exists", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerExists(@PathVariable("id") long customerId) {
        return customerQueryClient.customerExistsAsync(customerId)
                .thenApply(exists -> ResponseEntity.ok(JsonResponseSupport.booleanField("exists", exists)))
                .exceptionally(error -> unavailable("dubbo_customer_exists_unavailable", error));
    }

    @GetMapping(value = "/{id}/display-name", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerDisplayName(@PathVariable("id") long customerId) {
        return customerQueryClient.getCustomerDisplayNameAsync(customerId)
                .thenApply(name -> name == null || name.isBlank()
                        ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(JsonResponseSupport.error("customer_not_found", Long.toString(customerId)))
                        : ResponseEntity.ok(JsonResponseSupport.stringField("displayName", name)))
                .exceptionally(error -> unavailable("dubbo_customer_display_name_unavailable", error));
    }

    @PostMapping(value = "", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(32768)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_CREATE)
    public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody byte[] body) {
        return customerCommandClient.createCustomerNativeJsonAsync(body)
                .thenApply(handle -> ResponseEntity.created(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_customer_create_unavailable", error));
    }

    @PostMapping(value = "/typed", requestType = CreateCustomerCommand.class, responseType = RawResponse.class)
    @MaxRequestBodySize(32768)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_TYPED_CREATE)
    public CompletableFuture<ResponseEntity<RawResponse>> createCustomerTyped(
            @RequestBody CreateCustomerCommand command) {
        return customerCommandClient.createCustomerTypedAsync(command)
                .thenApply(result -> result.success()
                        ? ResponseEntity.created(JsonResponseSupport.mutation(result))
                        : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(JsonResponseSupport.mutation(result)))
                .exceptionally(error -> unavailable("dubbo_customer_create_typed_unavailable", error));
    }

    @PatchMapping(value = "/{id}/segment", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(16384)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerSegment(
            @PathVariable("id") long customerId,
            @RequestBody byte[] body) {
        return customerCommandKeyAdmission.execute(
                        customerId,
                        () -> customerCommandClient.patchCustomerSegmentNativeJsonAsync(customerId, body))
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_customer_segment_unavailable", error));
    }

    @PatchMapping(value = "/{id}/status", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(16384)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerStatus(
            @PathVariable("id") long customerId,
            @RequestBody byte[] body) {
        return customerCommandKeyAdmission.execute(
                        customerId,
                        () -> customerCommandClient.patchCustomerStatusNativeJsonAsync(customerId, body))
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_customer_status_unavailable", error));
    }

    @PatchMapping(value = "/{id}/status/typed", responseType = RawResponse.class)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_TYPED_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerStatusTyped(
            @PathVariable("id") long customerId,
            @RequestParam("status") String status,
            @RequestParam(value = "requestId", required = false, defaultValue = "") String requestId) {
        return customerCommandKeyAdmission.execute(
                        customerId,
                        () -> customerCommandClient.patchCustomerStatusTypedAsync(customerId, status, requestId))
                .thenApply(result -> result.success()
                        ? ResponseEntity.ok(JsonResponseSupport.mutation(result))
                        : ResponseEntity.status(HttpStatus.NOT_FOUND).body(JsonResponseSupport.mutation(result)))
                .exceptionally(error -> unavailable("dubbo_customer_status_typed_unavailable", error));
    }

    @DeleteMapping(value = "/{id}", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(8192)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> deleteCustomer(
            @PathVariable("id") long customerId,
            @RequestBody(required = false) byte[] body) {
        byte[] command = body == null ? new byte[0] : body;
        return customerCommandKeyAdmission.execute(
                        customerId,
                        () -> customerCommandClient.deleteCustomerNativeJsonAsync(customerId, command))
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_customer_delete_unavailable", error));
    }

}
