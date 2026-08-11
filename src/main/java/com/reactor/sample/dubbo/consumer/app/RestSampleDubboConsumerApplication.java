package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(
        name = "Dubbo Consumer Sample",
        version = "0.6.1",
        description = "Exposes REST endpoints backed by generated native Dubbo clients",
        scanBasePackages = "com.reactor.sample.dubbo.consumer")
public final class RestSampleDubboConsumerApplication {

    private RestSampleDubboConsumerApplication() {}

    public static void main(String[] args) {
        RestApplication.run(RestSampleDubboConsumerApplication.class, args);
    }
}
