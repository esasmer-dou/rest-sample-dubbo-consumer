package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.di.annotation.Bean;
import com.reactor.rust.di.annotation.Configuration;

@Configuration
public final class ConsumerReadinessConfiguration {
    @Bean
    public ConsumerReadinessContracts consumerReadinessContracts() {
        return ConsumerReadinessContracts.fromProperties();
    }
}
