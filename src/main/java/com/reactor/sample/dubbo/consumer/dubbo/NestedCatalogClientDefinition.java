package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.codegen.GenerateNativeDubboClient;
import com.reactor.rust.dubbo.sample.NestedCatalogService;

@GenerateNativeDubboClient(
        service = NestedCatalogService.class,
        generatedName = "NestedCatalogClient",
        retryReads = true,
        retryProperty = "sample.dubbo.read-retry-on-io-error",
        exposeMetrics = true)
final class NestedCatalogClientDefinition {

    private NestedCatalogClientDefinition() {}
}
