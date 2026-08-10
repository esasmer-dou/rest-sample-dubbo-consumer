package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.dubbo.codegen.EnableNativeDubboClients;
import com.reactor.rust.dubbo.codegen.GenerateNativeDubboClient;
import com.reactor.rust.dubbo.sample.CatalogJsonService;

@EnableNativeDubboClients(discoveryProperty = "sample.dubbo.discovery", staticOnly = true)
@GenerateNativeDubboClient(
        service = CatalogJsonService.class,
        generatedName = "NativeStaticCatalogClient",
        exposeMetrics = true,
        version = "0.0.0")
final class NativeStaticCatalogClientDefinition {

    private NativeStaticCatalogClientDefinition() {}
}
