package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.dubbo.DubboConsumerConfig;
import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CatalogOnlyDubboClientFactory {

    private CatalogOnlyDubboClientFactory() {}

    public static CatalogOnlyClient create() {
        NativeDubboConsumerClient client = NativeDubboConsumers.create(dubboConfig());
        DubboReferenceSpec<NestedCatalogService> spec = DubboReferenceSpec
                .builder(NestedCatalogService.class)
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .build();

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
                ConsumerProperties.getBoolean("sample.dubbo.read-retry-on-io-error")
        );
        return new CatalogOnlyClient(client, catalogClient);
    }

    private static DubboConsumerConfig dubboConfig() {
        String discovery = ConsumerProperties.get("sample.dubbo.discovery")
                .trim()
                .toLowerCase(Locale.ROOT);
        String providers = ConsumerProperties.get("reactor.dubbo.providers");
        if ("zookeeper".equals(discovery) || "zk".equals(discovery)) {
            providers = "";
        }
        return DubboConsumerConfig.builder()
                .applicationName(ConsumerProperties.get("reactor.dubbo.application-name"))
                .registryAddress(ConsumerProperties.get("reactor.dubbo.registry-address"))
                .registryRoot(ConsumerProperties.get("reactor.dubbo.registry-root"))
                .providers(providers)
                .registryTimeoutMs(ConsumerProperties.getInt("reactor.dubbo.registry-timeout-ms"))
                .registrySessionTimeoutMs(ConsumerProperties.getInt("reactor.dubbo.registry-session-timeout-ms"))
                .registryCheck(ConsumerProperties.getBoolean("reactor.dubbo.registry-check"))
                .protocol(ConsumerProperties.get("reactor.dubbo.protocol"))
                .serialization(ConsumerProperties.get("reactor.dubbo.serialization"))
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .connections(ConsumerProperties.getInt("reactor.dubbo.connections"))
                .shareConnections(ConsumerProperties.getInt("reactor.dubbo.share-connections"))
                .referThreadNum(ConsumerProperties.getInt("reactor.dubbo.refer-thread-num"))
                .maxInflight(ConsumerProperties.getInt("reactor.dubbo.max-inflight"))
                .maxResponseBytes(ConsumerProperties.getInt("reactor.dubbo.max-response-bytes"))
                .nativeConnectionsPerEndpoint(ConsumerProperties.getInt("reactor.dubbo.native-connections-per-endpoint"))
                .nativeMaxIdleConnectionsPerEndpoint(ConsumerProperties.getInt("reactor.dubbo.native-max-idle-connections-per-endpoint"))
                .nativeAsyncWorkers(ConsumerProperties.getInt("reactor.dubbo.native-async-workers"))
                .nativeAsyncQueueCapacity(ConsumerProperties.getInt("reactor.dubbo.native-async-queue-capacity"))
                .nativeAsyncTransport(ConsumerProperties.get("reactor.dubbo.native-async-transport"))
                .runtimeProfile(ConsumerProperties.get("reactor.dubbo.runtime-profile"))
                .transport(ConsumerProperties.get("reactor.dubbo.transport"))
                .cluster(ConsumerProperties.get("reactor.dubbo.cluster"))
                .loadbalance(ConsumerProperties.get("reactor.dubbo.loadbalance"))
                .build();
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
