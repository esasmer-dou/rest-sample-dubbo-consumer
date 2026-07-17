package com.reactor.sample.dubbo.consumer.dubbo;

import com.reactor.rust.dubbo.codegen.GenerateNativeDubboClient;
import com.reactor.rust.dubbo.sample.CustomerCommandService;

@GenerateNativeDubboClient(
        service = CustomerCommandService.class,
        generatedName = "CustomerCommandClient")
final class CustomerCommandClientDefinition {

    private CustomerCommandClientDefinition() {}
}
