package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.di.annotation.Bean;
import com.reactor.rust.di.annotation.Configuration;
import com.reactor.rust.di.annotation.PreDestroy;
import com.reactor.rust.dubbo.NativeDubboConsumerClient;
import com.reactor.rust.dubbo.NativeDubboConsumers;
import com.reactor.rust.dubbo.support.DubboConsumerSupport;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;
import com.reactor.sample.dubbo.consumer.dubbo.NestedCatalogClient;

@Configuration
public final class DubboConsumerConfiguration {

    private final DubboConsumerSupport support = support();
    private final NativeDubboConsumerClient client = NativeDubboConsumers.create(support.config());

    @Bean
    public NestedCatalogClient nestedCatalogClient() {
        return NestedCatalogClient.create(client, support);
    }

    @Bean
    public CustomerQueryClient customerQueryClient() {
        return CustomerQueryClient.create(client, support);
    }

    @Bean
    public CustomerCommandClient customerCommandClient() {
        return CustomerCommandClient.create(client, support);
    }

    @PreDestroy
    public void shutdown() {
        client.close();
    }

    private static DubboConsumerSupport support() {
        return DubboConsumerSupport.fromProperties(PropertiesLoader.getAll())
                .discoveryProperty("sample.dubbo.discovery");
    }
}
