package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.dubbo.DubboConsumerConfig;
import com.reactor.rust.dubbo.DubboReferenceSpec;

import java.util.Locale;

public final class DubboClientSupport {

    private DubboClientSupport() {}

    public static DubboConsumerConfig dubboConfig() {
        return dubboConfig(!zookeeperDiscovery());
    }

    public static DubboConsumerConfig staticDubboConfig() {
        return dubboConfig(true);
    }

    public static <T> DubboReferenceSpec<T> reference(Class<T> serviceType) {
        return referenceBuilder(serviceType).build();
    }

    public static <T> DubboReferenceSpec.Builder<T> referenceBuilder(Class<T> serviceType) {
        return DubboReferenceSpec.builder(serviceType)
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"));
    }

    public static boolean readRetryOnIoError() {
        return ConsumerProperties.getBoolean("sample.dubbo.read-retry-on-io-error");
    }

    public static boolean zookeeperDiscovery() {
        String discovery = ConsumerProperties.get("sample.dubbo.discovery")
                .trim()
                .toLowerCase(Locale.ROOT);
        return "zookeeper".equals(discovery) || "zk".equals(discovery);
    }

    private static DubboConsumerConfig dubboConfig(boolean includeStaticProviders) {
        String providers = includeStaticProviders ? ConsumerProperties.get("reactor.dubbo.providers") : "";
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
}
