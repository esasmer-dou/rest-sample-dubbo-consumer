package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.codegen.EnableNativeDubboClients;
import com.reactor.rust.dubbo.codegen.GenerateNativeDubboClient;
import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;

@EnableNativeDubboClients(discoveryProperty = "sample.dubbo.discovery")
@GenerateNativeDubboClient(
        service = NestedCatalogService.class,
        generatedName = "NestedCatalogClient",
        retryReads = true,
        retryProperty = "sample.dubbo.read-retry-on-io-error",
        exposeMetrics = true)
@GenerateNativeDubboClient(
        service = CustomerQueryService.class,
        generatedName = "CustomerQueryClient",
        retryReads = true,
        retryProperty = "sample.dubbo.read-retry-on-io-error",
        enabledProperty = "sample.consumer.surface",
        havingValue = "full",
        matchIfMissing = true)
@GenerateNativeDubboClient(
        service = CustomerCommandService.class,
        generatedName = "CustomerCommandClient",
        enabledProperty = "sample.consumer.surface",
        havingValue = "full",
        matchIfMissing = true)
public final class DubboClients {

    private DubboClients() {}
}
