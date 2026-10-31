package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.DeleteMapping;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.KeyAdmission;
import com.reactor.rust.annotations.MaxRequestBodySize;
import com.reactor.rust.annotations.PatchMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.PostMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RequestBody;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.concurrent.LongKeyAdmission;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.dubbo.http.DubboResponses;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.unavailable;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_RAW_CREATE;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_RAW_MUTATION;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_RAW_READ;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_TYPED_CREATE;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_TYPED_MUTATION;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CUSTOMER_TYPED_READ;

@RestController("/api/v1/customers")
@RequiresProperty(name = "sample.consumer.surface", value = "full", matchIfMissing = true)
public final class CustomerHandler {

    private final CustomerQueryClient customerQueryClient;
    private final CustomerCommandClient customerCommandClient;
    private final SegmentQuery segmentQuery;

    @FunctionalInterface
    private interface SegmentQuery {
        CompletableFuture<ResponseEntity<RawResponse>> query(String segment, int limit);
    }

    @Autowired
    public CustomerHandler(
            CustomerQueryClient customerQueryClient,
            CustomerCommandClient customerCommandClient,
            LongKeyAdmission customerCommandKeyAdmission,
            Optional<NativeSegmentTutorialHandler> nativeSegmentTutorial) {
        this.customerQueryClient = customerQueryClient;
        this.customerCommandClient = customerCommandClient;
        this.segmentQuery = nativeSegmentTutorial.isPresent()
                ? nativeSegmentTutorial.get()::customersBySegment
                : this::typedCustomersBySegment;
    }

    /** Retains source and binary compatibility; admission is now bound by generated startup. */
    public CustomerHandler(
            CustomerQueryClient customerQueryClient,
            CustomerCommandClient customerCommandClient,
            LongKeyAdmission customerCommandKeyAdmission) {
        this(customerQueryClient, customerCommandClient, customerCommandKeyAdmission, Optional.empty());
    }

    @GetMapping("/db")
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> databaseCustomers() {
        return DubboResponses.replyAsync(
                customerQueryClient.getDatabaseCustomersJsonNativeJsonAsync()
                        .thenApply(DubboResponses::ok)
                        .exceptionally(error -> unavailable("dubbo_customer_provider_unavailable", error)),
                response -> response);
    }

    @GetMapping("/db/stats")
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_TYPED_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerStats() {
        return customerQueryClient.getCustomerStatsAsync()
                .thenApply(stats -> ResponseEntity.ok(JsonResponseSupport.customerStats(stats)))
                .exceptionally(error -> unavailable("dubbo_customer_stats_unavailable", error));
    }

    @GetMapping("/db/by-segment")
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_TYPED_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customersBySegment(
            @RequestParam(value = "segment", defaultValue = "standard") String segment,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return segmentQuery.query(segment, limit);
    }

    private CompletableFuture<ResponseEntity<RawResponse>> typedCustomersBySegment(String segment, int limit) {
        return customerQueryClient.findCustomersBySegmentAsync(segment, limit)
                .thenApply(customers -> ResponseEntity.ok(JsonResponseSupport.customerList(customers)))
                .exceptionally(error -> unavailable("dubbo_customer_segment_query_unavailable", error));
    }

    @GetMapping("/db/{id}")
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerById(@PathVariable("id") long customerId) {
        return customerQueryClient.getCustomerAsync(customerId)
                .thenApply(customer -> customer == null
                        ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(JsonResponseSupport.error("customer_not_found", Long.toString(customerId)))
                        : ResponseEntity.ok(JsonResponseSupport.customerSummary(customer)))
                .exceptionally(error -> unavailable("dubbo_customer_get_unavailable", error));
    }

    @GetMapping("/{id}/exists")
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerExists(@PathVariable("id") long customerId) {
        return customerQueryClient.customerExistsAsync(customerId)
                .thenApply(exists -> ResponseEntity.ok(JsonResponseSupport.booleanField("exists", exists)))
                .exceptionally(error -> unavailable("dubbo_customer_exists_unavailable", error));
    }

    @GetMapping("/{id}/display-name")
    @RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CUSTOMER_RAW_READ)
    public CompletableFuture<ResponseEntity<RawResponse>> customerDisplayName(@PathVariable("id") long customerId) {
        return customerQueryClient.getCustomerDisplayNameAsync(customerId)
                .thenApply(name -> name == null || name.isBlank()
                        ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(JsonResponseSupport.error("customer_not_found", Long.toString(customerId)))
                        : ResponseEntity.ok(JsonResponseSupport.stringField("displayName", name)))
                .exceptionally(error -> unavailable("dubbo_customer_display_name_unavailable", error));
    }

    @PostMapping("")
    @MaxRequestBodySize(32768)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_CREATE)
    public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody byte[] body) {
        return DubboResponses.replyAsync(
                customerCommandClient.createCustomerNativeJsonAsync(body)
                        .thenApply(DubboResponses::created)
                        .exceptionally(error -> unavailable("dubbo_customer_create_unavailable", error)),
                response -> response);
    }

    @PostMapping("/typed")
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

    @PatchMapping("/{id}/segment")
    @KeyAdmission(bean = "customerCommandKeyAdmission", key = "customerId", onRejected = "segmentRejected")
    @MaxRequestBodySize(16384)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerSegment(
            @PathVariable("id") long customerId,
            @RequestBody byte[] body) {
        return DubboResponses.replyAsync(invokeAsync(() -> customerCommandClient.patchCustomerSegmentNativeJsonAsync(customerId, body))
                        .thenApply(DubboResponses::ok)
                        .exceptionally(error -> unavailable("dubbo_customer_segment_unavailable", error)),
                response -> response);
    }

    @PatchMapping("/{id}/status")
    @KeyAdmission(bean = "customerCommandKeyAdmission", key = "customerId", onRejected = "statusRejected")
    @MaxRequestBodySize(16384)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerStatus(
            @PathVariable("id") long customerId,
            @RequestBody byte[] body) {
        return DubboResponses.replyAsync(invokeAsync(() -> customerCommandClient.patchCustomerStatusNativeJsonAsync(customerId, body))
                        .thenApply(DubboResponses::ok)
                        .exceptionally(error -> unavailable("dubbo_customer_status_unavailable", error)),
                response -> response);
    }

    @PatchMapping("/{id}/status/typed")
    @KeyAdmission(bean = "customerCommandKeyAdmission", key = "customerId", onRejected = "typedStatusRejected")
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_TYPED_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> patchCustomerStatusTyped(
            @PathVariable("id") long customerId,
            @RequestParam("status") String status,
            @RequestParam(value = "requestId", required = false, defaultValue = "") String requestId) {
        return invokeAsync(() -> customerCommandClient.patchCustomerStatusTypedAsync(customerId, status, requestId))
                .thenApply(result -> result.success()
                        ? ResponseEntity.ok(JsonResponseSupport.mutation(result))
                        : ResponseEntity.status("idempotency_key_conflict".equals(result.message()) ? HttpStatus.CONFLICT
                                : "request_id_required".equals(result.message()) ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND)
                                .body(JsonResponseSupport.mutation(result)))
                .exceptionally(error -> unavailable("dubbo_customer_status_typed_unavailable", error));
    }

    @DeleteMapping("/{id}")
    @KeyAdmission(bean = "customerCommandKeyAdmission", key = "customerId", onRejected = "deleteRejected")
    @MaxRequestBodySize(8192)
    @RouteWorkload(value = RouteWorkload.Type.RPC_COMMAND, budget = CUSTOMER_RAW_MUTATION)
    public CompletableFuture<ResponseEntity<RawResponse>> deleteCustomer(
            @PathVariable("id") long customerId,
            @RequestBody(required = false) byte[] body) {
        byte[] command = body == null ? new byte[0] : body;
        return DubboResponses.replyAsync(invokeAsync(() -> customerCommandClient.deleteCustomerNativeJsonAsync(customerId, command))
                        .thenApply(DubboResponses::ok)
                        .exceptionally(error -> unavailable("dubbo_customer_delete_unavailable", error)),
                response -> response);
    }

    private static <T> CompletableFuture<T> invokeAsync(Supplier<CompletableFuture<T>> invocation) {
        try {
            return invocation.get();
        } catch (Throwable error) {
            // Lazy reference creation can fail before a future exists; retain the route's 503 mapping.
            return CompletableFuture.failedFuture(error);
        }
    }

    public CompletableFuture<ResponseEntity<RawResponse>> segmentRejected(Throwable error) {
        return CompletableFuture.completedFuture(unavailable("dubbo_customer_segment_unavailable", error));
    }

    public CompletableFuture<ResponseEntity<RawResponse>> statusRejected(Throwable error) {
        return CompletableFuture.completedFuture(unavailable("dubbo_customer_status_unavailable", error));
    }

    public CompletableFuture<ResponseEntity<RawResponse>> typedStatusRejected(Throwable error) {
        return CompletableFuture.completedFuture(unavailable("dubbo_customer_status_typed_unavailable", error));
    }

    public CompletableFuture<ResponseEntity<RawResponse>> deleteRejected(Throwable error) {
        return CompletableFuture.completedFuture(unavailable("dubbo_customer_delete_unavailable", error));
    }
}
