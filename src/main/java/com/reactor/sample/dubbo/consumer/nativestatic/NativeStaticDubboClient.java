package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.NativeResponseHandle;
import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.rust.dubbo.support.DubboConsumerSupport;

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
        DubboConsumerSupport support = support();
        if (support.zookeeperDiscovery()) {
            throw new IllegalStateException(
                    "native-static jlink image supports static providers only. "
                            + "Use the zookeeper jlink image for ZooKeeper discovery."
            );
        }

        NativeDubboConsumerClient client = NativeDubboConsumers.create(support.staticConfig());
        DubboReferenceSpec<CatalogJsonService> spec = support.referenceBuilder(CatalogJsonService.class)
                .version("0.0.0")
                .build();

        return new NativeStaticDubboClient(
                client,
                client.method(spec, "getNestedCatalogJson", byte[].class)
        );
    }

    public CompletableFuture<byte[]> nestedCatalogJsonAsync() {
        return nestedCatalogJson.invokeAsync();
    }

    public CompletableFuture<NativeResponseHandle> nestedCatalogNativeJsonAsync() {
        return nestedCatalogJson.invokeNativeJsonResponseAsync();
    }

    public String nativeMetricsJson() {
        return client.nativeMetricsJson();
    }

    @Override
    public void close() {
        client.close();
    }

    private static DubboConsumerSupport support() {
        return DubboConsumerSupport.fromProperties(PropertiesLoader.getAll())
                .discoveryProperty("sample.dubbo.discovery");
    }
}
