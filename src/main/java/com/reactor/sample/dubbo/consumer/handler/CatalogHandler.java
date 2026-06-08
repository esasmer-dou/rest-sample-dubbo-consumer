package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.di.annotation.Autowired;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequestMapping("/api/v1/catalog")
public final class CatalogHandler {

    @Autowired
    private NestedCatalogClient catalogClient;

    @Autowired
    private CustomerQueryClient customerQueryClient;

    @GetMapping(value = "/nested", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
        return catalogClient.nestedCatalogJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> unavailable("dubbo_provider_unavailable", error));
    }

    @GetMapping(value = "/title", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogTitle() {
        return catalogClient.catalogTitleAsync()
                .thenApply(title -> ResponseEntity.ok(JsonResponseSupport.stringField("title", title)))
                .exceptionally(error -> unavailable("dubbo_catalog_title_unavailable", error));
    }

    @GetMapping(value = "/count", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogItemCount() {
        return catalogClient.catalogItemCountAsync()
                .thenApply(count -> ResponseEntity.ok(JsonResponseSupport.intField("itemCount", count)))
                .exceptionally(error -> unavailable("dubbo_catalog_count_unavailable", error));
    }

    @GetMapping(value = "/info", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogInfo() {
        return catalogClient.catalogInfoAsync()
                .thenApply(info -> ResponseEntity.ok(JsonResponseSupport.catalogInfo(info)))
                .exceptionally(error -> unavailable("dubbo_catalog_info_unavailable", error));
    }

    @GetMapping(value = "/items", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> featuredItems(
            @RequestParam(value = "limit", defaultValue = "3") int limit) {
        return catalogClient.featuredItemsAsync(limit)
                .thenApply(items -> ResponseEntity.ok(JsonResponseSupport.catalogItems(items)))
                .exceptionally(error -> unavailable("dubbo_catalog_items_unavailable", error));
    }

    @GetMapping(value = "/attributes", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> catalogAttributes() {
        return catalogClient.catalogAttributesAsync()
                .thenApply(attributes -> ResponseEntity.ok(JsonResponseSupport.catalogAttributes(attributes)))
                .exceptionally(error -> unavailable("dubbo_catalog_attributes_unavailable", error));
    }

    @GetMapping(value = "/db/customers", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public CompletableFuture<ResponseEntity<RawResponse>> databaseCustomers() {
        return customerQueryClient.databaseCustomersJsonAsync()
                .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)))
                .exceptionally(error -> unavailable("dubbo_database_provider_unavailable", error));
    }

    @GetMapping(value = "/dubbo-metrics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> dubboMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                catalogClient.nativeMetricsJson(),
                "application/json; charset=utf-8"));
    }

    private static ResponseEntity<RawResponse> unavailable(String code, Throwable error) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(JsonResponseSupport.error(code, rootMessage(error)));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
