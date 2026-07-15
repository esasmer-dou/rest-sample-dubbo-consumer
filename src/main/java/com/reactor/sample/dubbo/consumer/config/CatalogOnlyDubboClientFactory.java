package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.support.DubboConsumerSupport;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

public final class CatalogOnlyDubboClientFactory {

    private CatalogOnlyDubboClientFactory() {}

    public static CatalogOnlyClient create() {
        DubboConsumerSupport support = support();
        NativeDubboConsumerClient client = NativeDubboConsumers.create(support.config());
        return new CatalogOnlyClient(client, NestedCatalogClient.create(client, support));
    }

    private static DubboConsumerSupport support() {
        return DubboConsumerSupport.fromProperties(PropertiesLoader.getAll())
                .discoveryProperty("sample.dubbo.discovery");
    }

    public record CatalogOnlyClient(
            NativeDubboConsumerClient nativeClient,
            NestedCatalogClient catalogClient
    ) implements AutoCloseable {

        @Override
        public void close() {
            nativeClient.close();
        }
    }
}
