package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.bridge.HandlerRegistry;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.bridge.RouteScanner;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.di.BeanContainer;
import com.reactor.sample.dubbo.consumer.config.ConsumerProperties;
import com.reactor.sample.dubbo.consumer.handler.CatalogHandler;
import com.reactor.sample.dubbo.consumer.handler.CustomerHandler;
import com.reactor.sample.dubbo.consumer.handler.HealthHandler;

public final class RestSampleDubboConsumerApplication {

    private static final String BASE_PACKAGE = "com.reactor.sample.dubbo.consumer";

    private RestSampleDubboConsumerApplication() {}

    public static void main(String[] args) {
        PropertiesLoader.load();

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
}
