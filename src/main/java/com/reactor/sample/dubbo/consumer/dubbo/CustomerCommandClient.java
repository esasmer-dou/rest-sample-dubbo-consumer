package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.NativeResponseHandle;
import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.rust.dubbo.support.DubboConsumerSupport;

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

    public static CustomerCommandClient create(
            NativeDubboConsumerClient client,
            DubboConsumerSupport support) {
        DubboReferenceSpec<CustomerCommandService> spec = support.reference(CustomerCommandService.class);
        return new CustomerCommandClient(
                client.method(spec, "createCustomer", byte[].class, byte[].class),
                client.method(spec, "patchCustomerSegment", byte[].class, long.class, byte[].class),
                client.method(spec, "patchCustomerStatus", byte[].class, long.class, byte[].class),
                client.method(spec, "deleteCustomer", byte[].class, long.class, byte[].class),
                client.method(spec, "createCustomerTyped", CustomerMutationResult.class, CreateCustomerCommand.class),
                client.method(
                        spec,
                        "patchCustomerStatusTyped",
                        CustomerMutationResult.class,
                        long.class,
                        String.class,
                        String.class));
    }

    public CompletableFuture<byte[]> createCustomerAsync(byte[] commandJson) {
        return createCustomer.invokeAsync(commandJson);
    }

    public CompletableFuture<NativeResponseHandle> createCustomerNativeJsonAsync(byte[] commandJson) {
        return createCustomer.invokeNativeJsonResponseAsync(commandJson);
    }

    public CompletableFuture<byte[]> patchCustomerSegmentAsync(long customerId, byte[] commandJson) {
        return patchCustomerSegment.invokeAsync(customerId, commandJson);
    }

    public CompletableFuture<NativeResponseHandle> patchCustomerSegmentNativeJsonAsync(long customerId, byte[] commandJson) {
        return patchCustomerSegment.invokeNativeJsonResponseAsync(customerId, commandJson);
    }

    public CompletableFuture<byte[]> patchCustomerStatusAsync(long customerId, byte[] commandJson) {
        return patchCustomerStatus.invokeAsync(customerId, commandJson);
    }

    public CompletableFuture<NativeResponseHandle> patchCustomerStatusNativeJsonAsync(long customerId, byte[] commandJson) {
        return patchCustomerStatus.invokeNativeJsonResponseAsync(customerId, commandJson);
    }

    public CompletableFuture<byte[]> deleteCustomerAsync(long customerId, byte[] commandJson) {
        return deleteCustomer.invokeAsync(customerId, commandJson);
    }

    public CompletableFuture<NativeResponseHandle> deleteCustomerNativeJsonAsync(long customerId, byte[] commandJson) {
        return deleteCustomer.invokeNativeJsonResponseAsync(customerId, commandJson);
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
