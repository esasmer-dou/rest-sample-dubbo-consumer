package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;

import java.util.concurrent.CompletableFuture;

public final class CustomerCommandClient {

    private final NativeDubboMethodInvoker<byte[]> createCustomer;
    private final NativeDubboMethodInvoker<byte[]> patchCustomerSegment;
    private final NativeDubboMethodInvoker<byte[]> patchCustomerStatus;
    private final NativeDubboMethodInvoker<byte[]> deleteCustomer;
    private final NativeDubboMethodInvoker<CustomerMutationResult> createCustomerTyped;
    private final NativeDubboMethodInvoker<CustomerMutationResult> patchCustomerStatusTyped;

    public CustomerCommandClient(
            NativeDubboMethodInvoker<byte[]> createCustomer,
            NativeDubboMethodInvoker<byte[]> patchCustomerSegment,
            NativeDubboMethodInvoker<byte[]> patchCustomerStatus,
            NativeDubboMethodInvoker<byte[]> deleteCustomer,
            NativeDubboMethodInvoker<CustomerMutationResult> createCustomerTyped,
            NativeDubboMethodInvoker<CustomerMutationResult> patchCustomerStatusTyped) {
        this.createCustomer = createCustomer;
        this.patchCustomerSegment = patchCustomerSegment;
        this.patchCustomerStatus = patchCustomerStatus;
        this.deleteCustomer = deleteCustomer;
        this.createCustomerTyped = createCustomerTyped;
        this.patchCustomerStatusTyped = patchCustomerStatusTyped;
    }

    public CompletableFuture<byte[]> createCustomerAsync(byte[] commandJson) {
        return createCustomer.invokeAsync(commandJson);
    }

    public CompletableFuture<byte[]> patchCustomerSegmentAsync(long customerId, byte[] commandJson) {
        return patchCustomerSegment.invokeAsync(customerId, commandJson);
    }

    public CompletableFuture<byte[]> patchCustomerStatusAsync(long customerId, byte[] commandJson) {
        return patchCustomerStatus.invokeAsync(customerId, commandJson);
    }

    public CompletableFuture<byte[]> deleteCustomerAsync(long customerId, byte[] commandJson) {
        return deleteCustomer.invokeAsync(customerId, commandJson);
    }

    public CompletableFuture<CustomerMutationResult> createCustomerTypedAsync(CreateCustomerCommand command) {
        return createCustomerTyped.invokeAsync(command);
    }

    public CompletableFuture<CustomerMutationResult> patchCustomerStatusTypedAsync(
            long customerId,
            String status,
            String requestId) {
        return patchCustomerStatusTyped.invokeAsync(customerId, status, requestId);
    }
}
