package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.app.RestApplication;

public final class RestSampleDubboConsumerApplication {

    private RestSampleDubboConsumerApplication() {}

    public static void main(String[] args) {
        RestApplication.run(DubboConsumerModule.INSTANCE);
    }
}
