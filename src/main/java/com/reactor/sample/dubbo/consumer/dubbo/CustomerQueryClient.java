package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CustomerQueryClient {

    private final NativeDubboMethodInvoker<byte[]> databaseCustomersJson;
    private final NativeDubboMethodInvoker<CustomerSummary> customer;
    private final NativeDubboMethodInvoker<List> customersBySegment;
    private final NativeDubboMethodInvoker<CustomerStats> customerStats;
    private final NativeDubboMethodInvoker<Boolean> customerExists;
    private final NativeDubboMethodInvoker<String> customerDisplayName;

    public CustomerQueryClient(
            NativeDubboMethodInvoker<byte[]> databaseCustomersJson,
            NativeDubboMethodInvoker<CustomerSummary> customer,
            NativeDubboMethodInvoker<List> customersBySegment,
            NativeDubboMethodInvoker<CustomerStats> customerStats,
            NativeDubboMethodInvoker<Boolean> customerExists,
            NativeDubboMethodInvoker<String> customerDisplayName) {
        this.databaseCustomersJson = databaseCustomersJson;
        this.customer = customer;
        this.customersBySegment = customersBySegment;
        this.customerStats = customerStats;
        this.customerExists = customerExists;
        this.customerDisplayName = customerDisplayName;
    }

    public CompletableFuture<byte[]> databaseCustomersJsonAsync() {
        return databaseCustomersJson.invokeAsync();
    }

    public CompletableFuture<CustomerSummary> customerAsync(long customerId) {
        return customer.invokeAsync(customerId);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<List<CustomerSummary>> customersBySegmentAsync(String segment, int limit) {
        return customersBySegment.invokeAsync(segment, limit)
                .thenApply(customers -> (List<CustomerSummary>) customers);
    }

    public CompletableFuture<CustomerStats> customerStatsAsync() {
        return customerStats.invokeAsync();
    }

    public CompletableFuture<Boolean> customerExistsAsync(long customerId) {
        return customerExists.invokeAsync(customerId);
    }

    public CompletableFuture<String> customerDisplayNameAsync(long customerId) {
        return customerDisplayName.invokeAsync(customerId);
    }
}
