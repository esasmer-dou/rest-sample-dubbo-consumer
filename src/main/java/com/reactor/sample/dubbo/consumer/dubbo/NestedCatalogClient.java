package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;

import java.util.concurrent.CompletableFuture;

public final class NestedCatalogClient {

    private final NativeDubboMethodInvoker<byte[]> nestedCatalogJson;
    private final NativeDubboMethodInvoker<byte[]> databaseCustomersJson;
    private final NativeDubboConsumerClient client;

    public NestedCatalogClient(
            NativeDubboMethodInvoker<byte[]> nestedCatalogJson,
            NativeDubboMethodInvoker<byte[]> databaseCustomersJson,
            NativeDubboConsumerClient client) {
        this.nestedCatalogJson = nestedCatalogJson;
        this.databaseCustomersJson = databaseCustomersJson;
        this.client = client;
    }

    public CompletableFuture<byte[]> nestedCatalogJsonAsync() {
        return nestedCatalogJson.invokeAsync();
    }

    public CompletableFuture<byte[]> databaseCustomersJsonAsync() {
        return databaseCustomersJson.invokeAsync();
    }

    public String nativeMetricsJson() {
        return client.nativeMetricsJson();
    }
}
