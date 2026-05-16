package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;

import java.util.concurrent.CompletableFuture;

public final class NestedCatalogClient {

    private final NativeDubboMethodInvoker<byte[]> nestedCatalogJson;
    private final NativeDubboConsumerClient client;

    public NestedCatalogClient(
            NativeDubboMethodInvoker<byte[]> nestedCatalogJson,
            NativeDubboConsumerClient client) {
        this.nestedCatalogJson = nestedCatalogJson;
        this.client = client;
    }

    public CompletableFuture<byte[]> nestedCatalogJsonAsync() {
        return nestedCatalogJson.invokeAsync();
    }

    public String nativeMetricsJson() {
        return client.nativeMetricsJson();
    }
}
