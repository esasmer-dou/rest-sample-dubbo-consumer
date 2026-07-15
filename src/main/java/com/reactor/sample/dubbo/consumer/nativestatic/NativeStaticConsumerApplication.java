package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.app.RestApplication;
import com.reactor.sample.dubbo.consumer.config.SampleDubboProfileTuning;

public final class NativeStaticConsumerApplication {

    private NativeStaticConsumerApplication() {}

    public static void main(String[] args) {
        RestApplication.builder()
                .shutdownThreadName("native-static-consumer-shutdown")
                .module(context -> {
                    SampleDubboProfileTuning.apply();
                    RestApplication.disableRouteIndexValidationIfNotExplicit();
                    NativeStaticDubboClient client = context.manage(NativeStaticDubboClient.create());
                    context.handlers(
                            new NativeStaticHealthHandler(client),
                            new NativeStaticCatalogHandler(client));
                })
                .start();
    }
}
