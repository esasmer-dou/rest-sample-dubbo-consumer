package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.List;
import java.util.Map;

public final class CatalogOnlyDubboClientFactory {

    private CatalogOnlyDubboClientFactory() {}

    public static CatalogOnlyClient create() {
        NativeDubboConsumerClient client = NativeDubboConsumers.create(DubboClientSupport.dubboConfig());
        DubboReferenceSpec<NestedCatalogService> spec = DubboClientSupport.reference(NestedCatalogService.class);

        NativeDubboMethodInvoker<byte[]> nestedCatalogJson =
                client.method(spec, "getNestedCatalogJson", byte[].class);

        NestedCatalogClient catalogClient = new NestedCatalogClient(
                nestedCatalogJson,
                client.method(spec, "getCatalogTitle", String.class),
                client.method(spec, "countCatalogItems", Integer.class),
                client.method(spec, "getCatalogInfo", CatalogInfo.class),
                client.method(spec, "listFeaturedItems", List.class, int.class),
                client.method(spec, "getCatalogAttributes", Map.class),
                client,
                DubboClientSupport.readRetryOnIoError()
        );
        return new CatalogOnlyClient(client, catalogClient);
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
