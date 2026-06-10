package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.dubbo.DubboConsumerConfig;
import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.sample.dubbo.consumer.config.ConsumerProperties;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class NativeStaticDubboClient implements AutoCloseable {

    private final NativeDubboConsumerClient client;
    private final NativeDubboMethodInvoker<byte[]> nestedCatalogJson;

    private NativeStaticDubboClient(
            NativeDubboConsumerClient client,
            NativeDubboMethodInvoker<byte[]> nestedCatalogJson
    ) {
        this.client = client;
        this.nestedCatalogJson = nestedCatalogJson;
    }

    public static NativeStaticDubboClient create() {
        String discovery = ConsumerProperties.get("sample.dubbo.discovery")
                .trim()
                .toLowerCase(Locale.ROOT);
        if ("zookeeper".equals(discovery) || "zk".equals(discovery)) {
            throw new IllegalStateException(
                    "native-static jlink image supports static providers only. "
                            + "Use the zookeeper jlink image for ZooKeeper discovery."
            );
        }

        NativeDubboConsumerClient client = NativeDubboConsumers.create(staticConfig());
        DubboReferenceSpec<CatalogJsonService> spec = DubboReferenceSpec
                .builder(CatalogJsonService.class)
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .build();

        return new NativeStaticDubboClient(
                client,
                client.method(spec, "getNestedCatalogJson", byte[].class)
        );
    }

    public CompletableFuture<byte[]> nestedCatalogJsonAsync() {
        return nestedCatalogJson.invokeAsync();
    }

    public String nativeMetricsJson() {
        return client.nativeMetricsJson();
    }

    @Override
    public void close() {
        client.close();
    }

    private static DubboConsumerConfig staticConfig() {
        return DubboConsumerConfig.builder()
                .applicationName(ConsumerProperties.get("reactor.dubbo.application-name"))
                .providers(ConsumerProperties.get("reactor.dubbo.providers"))
                .protocol(ConsumerProperties.get("reactor.dubbo.protocol"))
                .serialization(ConsumerProperties.get("reactor.dubbo.serialization"))
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .maxInflight(ConsumerProperties.getInt("reactor.dubbo.max-inflight"))
                .maxResponseBytes(ConsumerProperties.getInt("reactor.dubbo.max-response-bytes"))
                .nativeConnectionsPerEndpoint(ConsumerProperties.getInt("reactor.dubbo.native-connections-per-endpoint"))
                .nativeAsyncWorkers(ConsumerProperties.getInt("reactor.dubbo.native-async-workers"))
                .nativeAsyncQueueCapacity(ConsumerProperties.getInt("reactor.dubbo.native-async-queue-capacity"))
                .runtimeProfile(ConsumerProperties.get("reactor.dubbo.runtime-profile"))
                .transport(ConsumerProperties.get("reactor.dubbo.transport"))
                .cluster(ConsumerProperties.get("reactor.dubbo.cluster"))
                .loadbalance(ConsumerProperties.get("reactor.dubbo.loadbalance"))
                .build();
    }
}
