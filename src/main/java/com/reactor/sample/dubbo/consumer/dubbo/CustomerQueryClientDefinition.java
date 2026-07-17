package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.codegen.GenerateNativeDubboClient;
import com.reactor.rust.dubbo.sample.CustomerQueryService;

@GenerateNativeDubboClient(
        service = CustomerQueryService.class,
        generatedName = "CustomerQueryClient",
        retryReads = true,
        retryProperty = "sample.dubbo.read-retry-on-io-error")
final class CustomerQueryClientDefinition {

    private CustomerQueryClientDefinition() {}
}
