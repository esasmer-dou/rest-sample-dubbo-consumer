package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.bridge.HandlerRegistry;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.bridge.RouteScanner;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.di.BeanContainer;
import com.reactor.sample.dubbo.consumer.config.ConsumerProperties;

public final class ConsumerHttpBootstrap {

    private ConsumerHttpBootstrap() {}

    public static void startWithContainer(BeanContainer container, Class<?>... handlerTypes) {
        HandlerRegistry registry = HandlerRegistry.getInstance();
        for (Class<?> handlerType : handlerTypes) {
            registry.registerBean(container.getBean(handlerType));
        }
        startHttp(() -> container.shutdown(), "sample-shutdown");
    }

    public static void startWithHandlers(String shutdownThreadName, AutoCloseable closeable, Object... handlers) {
        HandlerRegistry registry = HandlerRegistry.getInstance();
        for (Object handler : handlers) {
            registry.registerBean(handler);
        }
        startHttp(closeable, shutdownThreadName);
    }

    public static void disableRouteIndexValidationIfNotExplicit() {
        if (!PropertiesLoader.hasExternalOverride("reactor.startup.route-index.validate")) {
            System.setProperty("reactor.startup.route-index.validate", "false");
        }
        if (!PropertiesLoader.hasExternalOverride("reactor.startup.route-index.required")) {
            System.setProperty("reactor.startup.route-index.required", "false");
        }
    }

    public static void sleepForever() {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void startHttp(AutoCloseable closeable, String shutdownThreadName) {
        RouteScanner.scanAndRegister();
        NativeBridge.configureRuntimeFromProperties();

        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> shutdown(closeable),
                shutdownThreadName
        ));
        NativeBridge.startHttpServer(ConsumerProperties.getInt("server.port"));
        sleepForever();
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
                // Shutdown is best effort.
            }
        }
    }
}
