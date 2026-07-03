package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.NativeResponseHandle;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class NestedCatalogClient {

    private final NativeDubboMethodInvoker<byte[]> nestedCatalogJson;
    private final NativeDubboMethodInvoker<String> catalogTitle;
    private final NativeDubboMethodInvoker<Integer> catalogItemCount;
    private final NativeDubboMethodInvoker<CatalogInfo> catalogInfo;
    private final NativeDubboMethodInvoker<List> featuredItems;
    private final NativeDubboMethodInvoker<Map> catalogAttributes;
    private final NativeDubboConsumerClient client;
    private final boolean retryReadOnConnectionAbort;

    public NestedCatalogClient(
            NativeDubboMethodInvoker<byte[]> nestedCatalogJson,
            NativeDubboMethodInvoker<String> catalogTitle,
            NativeDubboMethodInvoker<Integer> catalogItemCount,
            NativeDubboMethodInvoker<CatalogInfo> catalogInfo,
            NativeDubboMethodInvoker<List> featuredItems,
            NativeDubboMethodInvoker<Map> catalogAttributes,
            NativeDubboConsumerClient client,
            boolean retryReadOnConnectionAbort) {
        this.nestedCatalogJson = nestedCatalogJson;
        this.catalogTitle = catalogTitle;
        this.catalogItemCount = catalogItemCount;
        this.catalogInfo = catalogInfo;
        this.featuredItems = featuredItems;
        this.catalogAttributes = catalogAttributes;
        this.client = client;
        this.retryReadOnConnectionAbort = retryReadOnConnectionAbort;
    }

    public CompletableFuture<byte[]> nestedCatalogJsonAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, nestedCatalogJson::invokeAsync);
    }

    public CompletableFuture<NativeResponseHandle> nestedCatalogNativeJsonAsync() {
        return DubboReadRetry.onceOnConnectionAbort(
                retryReadOnConnectionAbort,
                nestedCatalogJson::invokeNativeJsonResponseAsync);
    }

    public CompletableFuture<String> catalogTitleAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, catalogTitle::invokeAsync);
    }

    public CompletableFuture<Integer> catalogItemCountAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, catalogItemCount::invokeAsync);
    }

    public CompletableFuture<CatalogInfo> catalogInfoAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, catalogInfo::invokeAsync);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<List<CatalogItem>> featuredItemsAsync(int limit) {
        return DubboReadRetry.onceOnConnectionAbort(
                        retryReadOnConnectionAbort,
                        () -> featuredItems.invokeAsync(limit))
                .thenApply(items -> (List<CatalogItem>) items);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, String>> catalogAttributesAsync() {
        return DubboReadRetry.onceOnConnectionAbort(retryReadOnConnectionAbort, catalogAttributes::invokeAsync)
                .thenApply(attributes -> (Map<String, String>) attributes);
    }

    public String nativeMetricsJson() {
        return client.nativeMetricsJson();
    }
}
