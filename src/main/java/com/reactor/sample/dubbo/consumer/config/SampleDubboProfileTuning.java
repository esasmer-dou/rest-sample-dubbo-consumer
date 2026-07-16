package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;

import java.util.Locale;

public final class SampleDubboProfileTuning {

    private static final String BALANCED_DUBBO = "balanced-dubbo";
    private static final String MICRO_2X2 = "micro-2x2";

    private static final String[] CATALOG_READ_ROUTES = {
            "get.api.v1.catalog.nested",
            "get.api.v1.catalog.title",
            "get.api.v1.catalog.count",
            "get.api.v1.catalog.info",
            "get.api.v1.catalog.items",
            "get.api.v1.catalog.attributes"
    };

    private static final String[] CUSTOMER_RAW_READ_ROUTES = {
            "get.api.v1.customers.db",
            "get.api.v1.customers.db.id",
            "get.api.v1.customers.id.exists",
            "get.api.v1.customers.id.display-name"
    };

    private static final String[] CUSTOMER_TYPED_READ_ROUTES = {
            "get.api.v1.customers.db.stats",
            "get.api.v1.customers.db.by-segment"
    };

    private static final String[] CUSTOMER_RAW_COMMAND_ROUTES = {
            "post.api.v1.customers",
            "patch.api.v1.customers.id.segment",
            "patch.api.v1.customers.id.status",
            "delete.api.v1.customers.id"
    };

    private static final String[] CUSTOMER_TYPED_COMMAND_ROUTES = {
            "post.api.v1.customers.typed",
            "patch.api.v1.customers.id.status.typed"
    };

    private SampleDubboProfileTuning() {}

    public static void apply() {
        String profile = PropertiesLoader.get("reactor.runtime.profile", "")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (BALANCED_DUBBO.equals(profile)) {
            applyBalancedDubbo();
            return;
        }
        String capacityProfile = PropertiesLoader.get("sample.dubbo.capacity-profile", "micro-1x1")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (MICRO_2X2.equals(capacityProfile)) {
            applyMicro2x2();
        }
    }

    private static void applyMicro2x2() {
        setIfNoExternalOverride("reactor.dubbo.max-inflight", "64");
        setIfNoExternalOverride("reactor.dubbo.native-connections-per-endpoint", "2");
        setIfNoExternalOverride("reactor.dubbo.native-max-idle-connections-per-endpoint", "2");
        setIfNoExternalOverride("reactor.dubbo.native-idle-connection-ttl-ms", "30000");
        setIfNoExternalOverride("reactor.dubbo.native-async-workers", "2");
        setIfNoExternalOverride("reactor.dubbo.native-async-queue-capacity", "64");
        setIfNoExternalOverride("reactor.dubbo.native-async-transport", "blocking");
        setRouteAdmissionIfNoExternalOverride("post.api.v1.customers.typed", "8", "250");
        setRouteAdmissionIfNoExternalOverride("patch.api.v1.customers.id.status.typed", "8", "250");
    }

    private static void applyBalancedDubbo() {
        setIfNoExternalOverride("reactor.dubbo.runtime-profile", BALANCED_DUBBO);
        setIfNoExternalOverride("reactor.rust.jni.workers", "16");
        setIfNoExternalOverride("reactor.rust.jni.queue-capacity", "1024");
        setIfNoExternalOverride("reactor.rust.async.max-inflight", "1024");
        setIfNoExternalOverride("reactor.dubbo.max-inflight", "512");
        setIfNoExternalOverride("reactor.dubbo.native-connections-per-endpoint", "16");
        setIfNoExternalOverride("reactor.dubbo.native-max-idle-connections-per-endpoint", "4");
        setIfNoExternalOverride("reactor.dubbo.native-idle-connection-ttl-ms", "30000");
        setIfNoExternalOverride("reactor.dubbo.native-async-workers", "8");
        setIfNoExternalOverride("reactor.dubbo.native-async-queue-capacity", "1024");
        setIfNoExternalOverride("reactor.dubbo.native-async-transport", "tokio-demux");
        setIfNoExternalOverride("reactor.dubbo.catalog.min-inflight", "16");
        setIfNoExternalOverride("reactor.dubbo.catalog.initial-inflight", "64");
        setIfNoExternalOverride("reactor.dubbo.catalog.max-inflight", "64");
        setIfNoExternalOverride("reactor.dubbo.catalog.response-timeout-ms", "1200");

        for (String route : CATALOG_READ_ROUTES) {
            setIfNoExternalOverride("reactor.rust.route-admission." + route + ".max-concurrent", "96");
            setIfNoExternalOverride("reactor.rust.route-admission." + route + ".queue-timeout-ms", "250");
        }

        for (String route : CUSTOMER_RAW_READ_ROUTES) {
            setRouteAdmissionIfNoExternalOverride(route, "32", "250");
        }
        for (String route : CUSTOMER_TYPED_READ_ROUTES) {
            setRouteAdmissionIfNoExternalOverride(route, "16", "200");
        }
        for (String route : CUSTOMER_RAW_COMMAND_ROUTES) {
            setRouteAdmissionIfNoExternalOverride(route, "32", "250");
        }
        for (String route : CUSTOMER_TYPED_COMMAND_ROUTES) {
            setRouteAdmissionIfNoExternalOverride(route, "16", "200");
        }
        setIfNoExternalOverride("sample.command.customer-key-admission.max-concurrent-per-key", "1");
        setIfNoExternalOverride("sample.command.customer-key-admission.stripes", "2048");
    }

    private static void setRouteAdmissionIfNoExternalOverride(
            String route,
            String maxConcurrent,
            String queueTimeoutMs) {
        setIfNoExternalOverride("reactor.rust.route-admission." + route + ".max-concurrent", maxConcurrent);
        setIfNoExternalOverride("reactor.rust.route-admission." + route + ".queue-timeout-ms", queueTimeoutMs);
    }

    private static void setIfNoExternalOverride(String key, String value) {
        if (!PropertiesLoader.hasExternalOverride(key)) {
            System.setProperty(key, value);
        }
    }
}
