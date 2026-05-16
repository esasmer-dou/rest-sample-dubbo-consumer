package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.NativeDubboMethodInvoker;

import java.util.concurrent.CompletableFuture;

public final class CustomerQueryClient {

    private final NativeDubboMethodInvoker<byte[]> databaseCustomersJson;

    public CustomerQueryClient(NativeDubboMethodInvoker<byte[]> databaseCustomersJson) {
        this.databaseCustomersJson = databaseCustomersJson;
    }

    public CompletableFuture<byte[]> databaseCustomersJsonAsync() {
        return databaseCustomersJson.invokeAsync();
    }
}
