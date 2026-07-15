package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.sample.dubbo.consumer.config.CatalogOnlyDubboClientFactory;
import com.reactor.sample.dubbo.consumer.config.SampleDubboProfileTuning;
import com.reactor.sample.dubbo.consumer.handler.CatalogOnlyHandler;
import com.reactor.sample.dubbo.consumer.handler.CatalogHandler;
import com.reactor.sample.dubbo.consumer.handler.CustomerHandler;
import com.reactor.sample.dubbo.consumer.handler.HealthHandler;

import java.util.Locale;

public final class RestSampleDubboConsumerApplication {

    private static final String BASE_PACKAGE = "com.reactor.sample.dubbo.consumer";

    private RestSampleDubboConsumerApplication() {}

    public static void main(String[] args) {
        RestApplication.builder()
                .shutdownThreadName("sample-shutdown")
                .module(context -> {
                    SampleDubboProfileTuning.apply();
                    if (isCatalogOnlySurface()) {
                        RestApplication.disableRouteIndexValidationIfNotExplicit();
                        CatalogOnlyDubboClientFactory.CatalogOnlyClient client =
                                context.manage(CatalogOnlyDubboClientFactory.create());
                        context.handlers(
                                new HealthHandler(client.catalogClient()),
                                new CatalogOnlyHandler(client.catalogClient()));
                    } else {
                        context.scan(BASE_PACKAGE)
                                .handlerTypes(HealthHandler.class, CatalogHandler.class, CustomerHandler.class);
                    }
                })
                .start();
    }

    private static boolean isCatalogOnlySurface() {
        String surface = PropertiesLoader.get("sample.consumer.surface", "full")
                .trim()
                .toLowerCase(Locale.ROOT);
        return "catalog-only".equals(surface) || "catalog".equals(surface);
    }
}
