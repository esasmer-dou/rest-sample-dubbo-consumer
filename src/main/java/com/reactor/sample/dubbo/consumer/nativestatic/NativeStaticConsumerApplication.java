package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.bridge.HandlerRegistry;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.bridge.RouteScanner;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfiles;
import com.reactor.sample.dubbo.consumer.config.ConsumerProperties;

public final class NativeStaticConsumerApplication {

    private NativeStaticConsumerApplication() {}

    public static void main(String[] args) {
        PropertiesLoader.load();
        RuntimeProfiles.apply();

        NativeStaticDubboClient dubboClient = NativeStaticDubboClient.create();

        HandlerRegistry registry = HandlerRegistry.getInstance();
        registry.registerBean(new NativeStaticHealthHandler());
        registry.registerBean(new NativeStaticCatalogHandler(dubboClient));

        RouteScanner.scanAndRegister();
        NativeBridge.configureRuntimeFromProperties();

        int port = ConsumerProperties.getInt("server.port");
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> shutdown(dubboClient),
                "native-static-consumer-shutdown"
        ));
        NativeBridge.startHttpServer(port);

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown(NativeStaticDubboClient dubboClient) {
        try {
            NativeBridge.stopHttpServer();
        } catch (UnsatisfiedLinkError ignored) {
            // Native library may be unavailable during failed startup.
        } finally {
            dubboClient.close();
        }
    }
}
