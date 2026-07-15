package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.NativeResponseHandle;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import com.reactor.rust.dubbo.support.DubboConsumerSupport;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CustomerQueryClient {

    private final NativeDubboMethodInvoker<byte[]> databaseCustomersJson;
    private final NativeDubboMethodInvoker<CustomerSummary> customer;
    private final NativeDubboMethodInvoker<List> customersBySegment;
    private final NativeDubboMethodInvoker<CustomerStats> customerStats;
    private final NativeDubboMethodInvoker<Boolean> customerExists;
    private final NativeDubboMethodInvoker<String> customerDisplayName;
    private final boolean retryReadOnConnectionAbort;

    public CustomerQueryClient(
            NativeDubboMethodInvoker<byte[]> databaseCustomersJson,
            NativeDubboMethodInvoker<CustomerSummary> customer,
            NativeDubboMethodInvoker<List> customersBySegment,
            NativeDubboMethodInvoker<CustomerStats> customerStats,
            NativeDubboMethodInvoker<Boolean> customerExists,
            NativeDubboMethodInvoker<String> customerDisplayName,
            boolean retryReadOnConnectionAbort) {
        this.databaseCustomersJson = databaseCustomersJson;
        this.customer = customer;
        this.customersBySegment = customersBySegment;
        this.customerStats = customerStats;
        this.customerExists = customerExists;
        this.customerDisplayName = customerDisplayName;
        this.retryReadOnConnectionAbort = retryReadOnConnectionAbort;
    }

    public static CustomerQueryClient create(
            NativeDubboConsumerClient client,
            DubboConsumerSupport support) {
        DubboReferenceSpec<CustomerQueryService> spec = support.reference(CustomerQueryService.class);
        return new CustomerQueryClient(
                client.method(spec, "getDatabaseCustomersJson", byte[].class),
                client.method(spec, "getCustomer", CustomerSummary.class, long.class),
                client.method(spec, "findCustomersBySegment", List.class, String.class, int.class),
                client.method(spec, "getCustomerStats", CustomerStats.class),
                client.method(spec, "customerExists", Boolean.class, long.class),
                client.method(spec, "getCustomerDisplayName", String.class, long.class),
                support.booleanProperty("sample.dubbo.read-retry-on-io-error", false));
    }

    public CompletableFuture<byte[]> databaseCustomersJsonAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, databaseCustomersJson::invokeAsync);
    }

    public CompletableFuture<NativeResponseHandle> databaseCustomersNativeJsonAsync() {
        return DubboReadRetry.onceOnConnectionAbort(
                retryReadOnConnectionAbort,
                databaseCustomersJson::invokeNativeJsonResponseAsync);
    }

    public CompletableFuture<CustomerSummary> customerAsync(long customerId) {
        return DubboReadRetry.onceOnConnectionAbort(
                retryReadOnConnectionAbort,
                () -> customer.invokeAsync(customerId));
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<List<CustomerSummary>> customersBySegmentAsync(String segment, int limit) {
        return DubboReadRetry.onceOnConnectionAbort(
                        retryReadOnConnectionAbort,
                        () -> customersBySegment.invokeAsync(segment, limit))
                .thenApply(customers -> (List<CustomerSummary>) customers);
    }

    public CompletableFuture<CustomerStats> customerStatsAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, customerStats::invokeAsync);
    }

    public CompletableFuture<Boolean> customerExistsAsync(long customerId) {
        return DubboReadRetry.onceOnConnectionAbort(
                retryReadOnConnectionAbort,
                () -> customerExists.invokeAsync(customerId));
    }

    public CompletableFuture<String> customerDisplayNameAsync(long customerId) {
        return DubboReadRetry.onceOnConnectionAbort(
                retryReadOnConnectionAbort,
                () -> customerDisplayName.invokeAsync(customerId));
    }
}
