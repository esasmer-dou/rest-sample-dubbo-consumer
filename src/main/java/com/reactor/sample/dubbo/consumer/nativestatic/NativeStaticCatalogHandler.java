package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.unavailable;
import static com.reactor.sample.dubbo.consumer.config.ConsumerRouteBudgets.CATALOG_READ;

@RequestMapping("/api/v1/catalog")
@RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = CATALOG_READ)
public final class NativeStaticCatalogHandler {

    private final NativeStaticCatalogClient catalogClient;

    public NativeStaticCatalogHandler(NativeStaticCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @GetMapping(value = "/nested", responseType = RawResponse.class)
    public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
        return catalogClient.getNestedCatalogJsonNativeJsonAsync()
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_provider_unavailable", error));
    }

    @GetMapping(value = "/dubbo-metrics", responseType = RawResponse.class)
    @RouteWorkload(RouteWorkload.Type.STANDARD)
    public ResponseEntity<RawResponse> dubboMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                catalogClient.nativeMetricsJson(),
                "application/json; charset=utf-8"));
    }

}
