package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(scanBasePackages = "com.reactor.sample.dubbo.consumer")
public final class RestSampleDubboConsumerApplication {

    private RestSampleDubboConsumerApplication() {}

    public static void main(String[] args) {
        if (DubboConsumerModule.isCatalogOnlySurface()) {
            RestApplication.run(DubboConsumerModule.INSTANCE);
            return;
        }
        RestApplication.run(RestSampleDubboConsumerApplication.class, args);
    }
}
