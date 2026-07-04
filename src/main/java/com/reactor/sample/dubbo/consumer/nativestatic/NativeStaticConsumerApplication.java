package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfiles;
import com.reactor.sample.dubbo.consumer.app.ConsumerHttpBootstrap;
import com.reactor.sample.dubbo.consumer.config.SampleDubboProfileTuning;

public final class NativeStaticConsumerApplication {

    private NativeStaticConsumerApplication() {}

    public static void main(String[] args) {
        PropertiesLoader.load();
        RuntimeProfiles.apply();
        SampleDubboProfileTuning.apply();
        ConsumerHttpBootstrap.disableRouteIndexValidationIfNotExplicit();

        NativeStaticDubboClient dubboClient = NativeStaticDubboClient.create();

        ConsumerHttpBootstrap.startWithHandlers(
                "native-static-consumer-shutdown",
                dubboClient,
                new NativeStaticHealthHandler(dubboClient),
                new NativeStaticCatalogHandler(dubboClient));
    }
}
