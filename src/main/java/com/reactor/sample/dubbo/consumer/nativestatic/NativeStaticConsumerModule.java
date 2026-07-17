package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.support.DubboConsumerSupport;
import com.reactor.sample.dubbo.consumer.config.ConsumerRuntimePlans;

public final class NativeStaticConsumerModule implements RestApplication.Module {

    public static final NativeStaticConsumerModule INSTANCE = new NativeStaticConsumerModule();

    private NativeStaticConsumerModule() {}

    @Override
    public void configure(RestApplication.ModuleContext context) {
        context.profile(ConsumerRuntimePlans.resolve());
        RestApplication.disableRouteIndexValidationIfNotExplicit();
        DubboConsumerSupport support = DubboConsumerSupport.fromProperties(PropertiesLoader.getAll())
                .discoveryProperty("sample.dubbo.discovery");
        if (support.zookeeperDiscovery()) {
            throw new IllegalStateException(
                    "native-static jlink image supports static providers only. "
                            + "Use the zookeeper jlink image for ZooKeeper discovery.");
        }
        NativeDubboConsumerClient transport = context.manage(
                NativeDubboConsumers.create(support.staticConfig()));
        NativeStaticCatalogClient client = NativeStaticCatalogClient.create(transport, support);
        context.handlers(
                new NativeStaticHealthHandler(client),
                new NativeStaticCatalogHandler(client));
    }
}
