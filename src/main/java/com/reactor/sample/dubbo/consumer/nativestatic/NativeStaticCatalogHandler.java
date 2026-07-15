package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.unavailable;

@RequestMapping("/api/v1/catalog")
public final class NativeStaticCatalogHandler {

    private final NativeStaticDubboClient catalogClient;

    public NativeStaticCatalogHandler(NativeStaticDubboClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @GetMapping(value = "/nested", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
        return catalogClient.nestedCatalogNativeJsonAsync()
                .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())))
                .exceptionally(error -> unavailable("dubbo_provider_unavailable", error));
    }

    @GetMapping(value = "/dubbo-metrics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> dubboMetrics() {
        return ResponseEntity.ok(RawResponse.text(
                catalogClient.nativeMetricsJson(),
                "application/json; charset=utf-8"));
    }

}
