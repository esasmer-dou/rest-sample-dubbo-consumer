package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.concurrent.LongKeyAdmission;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.di.annotation.Bean;
import com.reactor.rust.di.annotation.Configuration;

@Configuration
@RequiresProperty(name = "sample.consumer.surface", value = "full", matchIfMissing = true)
public final class ConsumerConfiguration {

    @Bean
    public LongKeyAdmission customerCommandAdmission() {
        return LongKeyAdmission.fromProperties("sample.command.customer-key-admission");
    }
}
