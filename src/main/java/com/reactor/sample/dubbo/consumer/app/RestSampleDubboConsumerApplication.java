package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.bridge.HandlerRegistry;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.bridge.RouteScanner;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfiles;
import com.reactor.rust.di.BeanContainer;
import com.reactor.sample.dubbo.consumer.config.CatalogOnlyDubboClientFactory;
import com.reactor.sample.dubbo.consumer.config.SampleDubboProfileTuning;
import com.reactor.sample.dubbo.consumer.config.ConsumerProperties;
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
        BeanContainer container = BeanContainer.getInstance();
        container.scan(BASE_PACKAGE);
        container.start();

        HandlerRegistry registry = HandlerRegistry.getInstance();
        registry.registerBean(container.getBean(HealthHandler.class));
        registry.registerBean(container.getBean(CatalogHandler.class));
        registry.registerBean(container.getBean(CustomerHandler.class));

        RouteScanner.scanAndRegister();
        NativeBridge.configureRuntimeFromProperties();

        int port = ConsumerProperties.getInt("server.port");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(container), "sample-shutdown"));
        NativeBridge.startHttpServer(port);

        sleepForever();
    }

    private static void startCatalogOnly() {
        disableRouteIndexValidationIfNotExplicit();

        CatalogOnlyDubboClientFactory.CatalogOnlyClient catalogOnlyClient =
                CatalogOnlyDubboClientFactory.create();

        HandlerRegistry registry = HandlerRegistry.getInstance();
        registry.registerBean(new HealthHandler(catalogOnlyClient.catalogClient()));
        registry.registerBean(new CatalogOnlyHandler(catalogOnlyClient.catalogClient()));

        RouteScanner.scanAndRegister();
        NativeBridge.configureRuntimeFromProperties();

        int port = ConsumerProperties.getInt("server.port");
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> shutdown(catalogOnlyClient),
                "sample-catalog-only-shutdown"
        ));
        NativeBridge.startHttpServer(port);

        sleepForever();
    }

    private static boolean isCatalogOnlySurface() {
        String surface = PropertiesLoader.get("sample.consumer.surface", "full")
                .trim()
                .toLowerCase(Locale.ROOT);
        return "catalog-only".equals(surface) || "catalog".equals(surface);
    }

    private static void disableRouteIndexValidationIfNotExplicit() {
        if (!PropertiesLoader.hasExternalOverride("reactor.startup.route-index.validate")) {
            System.setProperty("reactor.startup.route-index.validate", "false");
        }
        if (!PropertiesLoader.hasExternalOverride("reactor.startup.route-index.required")) {
            System.setProperty("reactor.startup.route-index.required", "false");
        }
    }

    private static void sleepForever() {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown(BeanContainer container) {
        try {
            NativeBridge.stopHttpServer();
        } catch (UnsatisfiedLinkError ignored) {
            // Native library may be unavailable during failed startup.
        } finally {
            container.shutdown();
        }
    }

    private static void shutdown(AutoCloseable closeable) {
        try {
            NativeBridge.stopHttpServer();
        } catch (UnsatisfiedLinkError ignored) {
            // Native library may be unavailable during failed startup.
        } finally {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Shutdown best effort.
            }
        }
    }
}
