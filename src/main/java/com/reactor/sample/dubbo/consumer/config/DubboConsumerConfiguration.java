package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.di.annotation.Bean;
import com.reactor.rust.di.annotation.Configuration;
import com.reactor.rust.di.annotation.PreDestroy;
import com.reactor.rust.dubbo.DubboConsumerConfig;
import com.reactor.rust.dubbo.DubboReferenceSpec;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
public final class DubboConsumerConfiguration {

    private final NativeDubboConsumerClient client = NativeDubboConsumers.create(dubboConfig());

    @Bean
    public NestedCatalogClient nestedCatalogClient() {
        DubboReferenceSpec<NestedCatalogService> spec = DubboReferenceSpec
                .builder(NestedCatalogService.class)
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .build();

        NativeDubboMethodInvoker<byte[]> invoker =
                client.method(spec, "getNestedCatalogJson", byte[].class);

        return new NestedCatalogClient(
                invoker,
                client.method(spec, "getCatalogTitle", String.class),
                client.method(spec, "countCatalogItems", Integer.class),
                client.method(spec, "getCatalogInfo", CatalogInfo.class),
                client.method(spec, "listFeaturedItems", List.class, int.class),
                client.method(spec, "getCatalogAttributes", Map.class),
                client
        );
    }

    @Bean
    public CustomerQueryClient customerQueryClient() {
        DubboReferenceSpec<CustomerQueryService> spec = DubboReferenceSpec
                .builder(CustomerQueryService.class)
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .build();

        NativeDubboMethodInvoker<byte[]> databaseInvoker =
                client.method(spec, "getDatabaseCustomersJson", byte[].class);

        return new CustomerQueryClient(
                databaseInvoker,
                client.method(spec, "getCustomer", CustomerSummary.class, long.class),
                client.method(spec, "findCustomersBySegment", List.class, String.class, int.class),
                client.method(spec, "getCustomerStats", CustomerStats.class),
                client.method(spec, "customerExists", Boolean.class, long.class),
                client.method(spec, "getCustomerDisplayName", String.class, long.class)
        );
    }

    @Bean
    public CustomerCommandClient customerCommandClient() {
        DubboReferenceSpec<CustomerCommandService> spec = DubboReferenceSpec
                .builder(CustomerCommandService.class)
                .timeoutMs(ConsumerProperties.getInt("reactor.dubbo.timeout-ms"))
                .retries(ConsumerProperties.getInt("reactor.dubbo.retries"))
                .check(ConsumerProperties.getBoolean("reactor.dubbo.check"))
                .lazy(ConsumerProperties.getBoolean("reactor.dubbo.lazy"))
                .build();

        return new CustomerCommandClient(
                client.method(spec, "createCustomer", byte[].class, byte[].class),
                client.method(spec, "patchCustomerSegment", byte[].class, long.class, byte[].class),
                client.method(spec, "patchCustomerStatus", byte[].class, long.class, byte[].class),
                client.method(spec, "deleteCustomer", byte[].class, long.class, byte[].class),
                client.method(spec, "createCustomerTyped", CustomerMutationResult.class, CreateCustomerCommand.class),
                client.method(spec, "patchCustomerStatusTyped", CustomerMutationResult.class, long.class, String.class, String.class)
        );
    }

    @PreDestroy
    public void shutdown() {
        client.close();
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
                .nativeAsyncWorkers(ConsumerProperties.getInt("reactor.dubbo.native-async-workers"))
                .nativeAsyncQueueCapacity(ConsumerProperties.getInt("reactor.dubbo.native-async-queue-capacity"))
                .runtimeProfile(ConsumerProperties.get("reactor.dubbo.runtime-profile"))
                .transport(ConsumerProperties.get("reactor.dubbo.transport"))
                .cluster(ConsumerProperties.get("reactor.dubbo.cluster"))
                .loadbalance(ConsumerProperties.get("reactor.dubbo.loadbalance"))
                .build();
    }
}
