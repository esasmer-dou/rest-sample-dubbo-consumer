package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.di.annotation.Bean;
import com.reactor.rust.di.annotation.Configuration;
import com.reactor.rust.di.annotation.PreDestroy;
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
import java.util.Map;

@Configuration
public final class DubboConsumerConfiguration {

    private final NativeDubboConsumerClient client = NativeDubboConsumers.create(DubboClientSupport.dubboConfig());

    @Bean
    public NestedCatalogClient nestedCatalogClient() {
        DubboReferenceSpec<NestedCatalogService> spec = DubboClientSupport.reference(NestedCatalogService.class);

        NativeDubboMethodInvoker<byte[]> invoker =
                client.method(spec, "getNestedCatalogJson", byte[].class);

        return new NestedCatalogClient(
                invoker,
                client.method(spec, "getCatalogTitle", String.class),
                client.method(spec, "countCatalogItems", Integer.class),
                client.method(spec, "getCatalogInfo", CatalogInfo.class),
                client.method(spec, "listFeaturedItems", List.class, int.class),
                client.method(spec, "getCatalogAttributes", Map.class),
                client,
                DubboClientSupport.readRetryOnIoError()
        );
    }

    @Bean
    public CustomerQueryClient customerQueryClient() {
        DubboReferenceSpec<CustomerQueryService> spec = DubboClientSupport.reference(CustomerQueryService.class);

        NativeDubboMethodInvoker<byte[]> databaseInvoker =
                client.method(spec, "getDatabaseCustomersJson", byte[].class);

        return new CustomerQueryClient(
                databaseInvoker,
                client.method(spec, "getCustomer", CustomerSummary.class, long.class),
                client.method(spec, "findCustomersBySegment", List.class, String.class, int.class),
                client.method(spec, "getCustomerStats", CustomerStats.class),
                client.method(spec, "customerExists", Boolean.class, long.class),
                client.method(spec, "getCustomerDisplayName", String.class, long.class),
                DubboClientSupport.readRetryOnIoError()
        );
    }

    @Bean
    public CustomerCommandClient customerCommandClient() {
        DubboReferenceSpec<CustomerCommandService> spec = DubboClientSupport.reference(CustomerCommandService.class);

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
}
