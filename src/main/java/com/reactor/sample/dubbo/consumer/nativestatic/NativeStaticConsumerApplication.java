package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfiles;
import com.reactor.sample.dubbo.consumer.config.SampleDubboProfileTuning;

public final class NativeStaticConsumerApplication {

    private NativeStaticConsumerApplication() {}

    public static void main(String[] args) {
        PropertiesLoader.load();
        RuntimeProfiles.apply();
        SampleDubboProfileTuning.apply();
        RestApplication.disableRouteIndexValidationIfNotExplicit();

        NativeStaticDubboClient dubboClient = NativeStaticDubboClient.create();

        RestApplication.builder()
                .loadProperties(false)
                .applyRuntimeProfiles(false)
                .shutdownThreadName("native-static-consumer-shutdown")
                .closeable(dubboClient)
                .handlerInstances(
                        new NativeStaticHealthHandler(dubboClient),
                        new NativeStaticCatalogHandler(dubboClient))
                .start();
    }
}
