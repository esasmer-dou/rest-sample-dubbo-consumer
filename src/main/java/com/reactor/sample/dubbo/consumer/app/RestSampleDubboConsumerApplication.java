package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfiles;
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
        PropertiesLoader.load();
        RuntimeProfiles.apply();
        SampleDubboProfileTuning.apply();

        if (isCatalogOnlySurface()) {
            startCatalogOnly();
            return;
        }

        startFullSurface();
    }

    private static void startFullSurface() {
        RestApplication.builder()
                .loadProperties(false)
                .applyRuntimeProfiles(false)
                .scan(BASE_PACKAGE)
                .handlers(HealthHandler.class, CatalogHandler.class, CustomerHandler.class)
                .shutdownThreadName("sample-shutdown")
                .start();
    }

    private static void startCatalogOnly() {
        RestApplication.disableRouteIndexValidationIfNotExplicit();

        CatalogOnlyDubboClientFactory.CatalogOnlyClient catalogOnlyClient =
                CatalogOnlyDubboClientFactory.create();

        RestApplication.builder()
                .loadProperties(false)
                .applyRuntimeProfiles(false)
                .shutdownThreadName("sample-catalog-only-shutdown")
                .closeable(catalogOnlyClient)
                .handlerInstances(
                        new HealthHandler(catalogOnlyClient.catalogClient()),
                        new CatalogOnlyHandler(catalogOnlyClient.catalogClient()))
                .start();
    }

    private static boolean isCatalogOnlySurface() {
        String surface = PropertiesLoader.get("sample.consumer.surface", "full")
                .trim()
                .toLowerCase(Locale.ROOT);
        return "catalog-only".equals(surface) || "catalog".equals(surface);
    }
}
