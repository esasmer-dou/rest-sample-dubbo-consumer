package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.concurrent.CompletableFuture;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.unavailable;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CATALOG_READ;

@Component
@RequestMapping("/api/v1/catalog")
@RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CATALOG_READ)
public final class CatalogHandler {

    @Autowired
    private NestedCatalogClient catalogClient;

    @GetMapping(value = "/nested", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
        return catalogClient.getNestedCatalogJsonNativeJsonAsync()
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_provider_unavailable", error));
    }

    @GetMapping(value = "/title", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogTitle() {
        return catalogClient.getCatalogTitleAsync()
                .thenApply(title -> ResponseEntity.ok(JsonResponseSupport.stringField("title", title)))
                .exceptionally(error -> unavailable("dubbo_catalog_title_unavailable", error));
    }

    @GetMapping(value = "/count", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogItemCount() {
        return catalogClient.countCatalogItemsAsync()
                .thenApply(count -> ResponseEntity.ok(JsonResponseSupport.intField("itemCount", count)))
                .exceptionally(error -> unavailable("dubbo_catalog_count_unavailable", error));
    }

    @GetMapping(value = "/info", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogInfo() {
        return catalogClient.getCatalogInfoAsync()
                .thenApply(info -> ResponseEntity.ok(JsonResponseSupport.catalogInfo(info)))
                .exceptionally(error -> unavailable("dubbo_catalog_info_unavailable", error));
    }

    @GetMapping(value = "/items", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> featuredItems(
            @RequestParam(value = "limit", defaultValue = "3") int limit) {
        return catalogClient.listFeaturedItemsAsync(limit)
                .thenApply(items -> ResponseEntity.ok(JsonResponseSupport.catalogItems(items)))
                .exceptionally(error -> unavailable("dubbo_catalog_items_unavailable", error));
    }

    @GetMapping(value = "/attributes", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogAttributes() {
        return catalogClient.getCatalogAttributesAsync()
                .thenApply(attributes -> ResponseEntity.ok(JsonResponseSupport.catalogAttributes(attributes)))
                .exceptionally(error -> unavailable("dubbo_catalog_attributes_unavailable", error));
    }

    @GetMapping(value = "/dubbo-metrics", responseType = RawResponse.class)
    @RouteWorkload(RouteWorkload.Type.STANDARD)
    public ResponseEntity<RawResponse> dubboMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                catalogClient.nativeMetricsJson(),
                "application/json; charset=utf-8"));
    }

}
