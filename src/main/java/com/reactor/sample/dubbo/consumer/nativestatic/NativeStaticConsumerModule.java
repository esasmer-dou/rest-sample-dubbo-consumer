package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.app.RestApplication;
import com.reactor.sample.dubbo.consumer.config.SampleDubboProfileTuning;

public final class NativeStaticConsumerModule implements RestApplication.Module {

    public static final NativeStaticConsumerModule INSTANCE = new NativeStaticConsumerModule();

    private NativeStaticConsumerModule() {}

    @Override
    public void configure(RestApplication.ModuleContext context) {
        SampleDubboProfileTuning.apply();
        RestApplication.disableRouteIndexValidationIfNotExplicit();
        NativeStaticDubboClient client = context.manage(NativeStaticDubboClient.create());
        context.handlers(
                new NativeStaticHealthHandler(client),
                new NativeStaticCatalogHandler(client));
    }
}
