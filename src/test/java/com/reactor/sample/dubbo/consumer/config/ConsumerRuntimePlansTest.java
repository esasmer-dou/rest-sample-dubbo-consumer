package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumerRuntimePlansTest {

    private static final List<String> KEYS = List.of(
            "sample.dubbo.capacity-profile",
            "reactor.dubbo.max-inflight",
            "reactor.dubbo.native-connections-per-endpoint",
            "reactor.dubbo.native-max-idle-connections-per-endpoint",
            "reactor.dubbo.native-idle-connection-ttl-ms",
            "reactor.dubbo.native-async-workers",
            "reactor.dubbo.native-async-queue-capacity",
            "reactor.dubbo.native-async-transport",
            "reactor.rust.route-budget.rpc-catalog-read.route-admission.max-concurrent",
            "reactor.rust.route-budget.rpc-customer-raw-create.route-admission.max-concurrent");

    @BeforeEach
    void loadProperties() {
        PropertiesLoader.load();
    }

    @AfterEach
    void clearProperties() {
        KEYS.forEach(System::clearProperty);
    }

    @Test
    void micro2x2PlanIsValidatedBeforeApplication() {
        System.setProperty("sample.dubbo.capacity-profile", "micro-2x2");

        ConsumerRuntimePlans.resolve().apply();

        assertEquals("64", PropertiesLoader.get("reactor.dubbo.max-inflight"));
        assertEquals("2", PropertiesLoader.get("reactor.dubbo.native-connections-per-endpoint"));
        assertEquals("blocking", PropertiesLoader.get("reactor.dubbo.native-async-transport"));
        assertEquals("4", PropertiesLoader.get(
                "reactor.rust.route-budget.rpc-customer-raw-create.route-admission.max-concurrent"));
    }

    @Test
    void explicitOverrideWinsOverImmutablePlan() {
        String key = "reactor.rust.route-budget.rpc-catalog-read.route-admission.max-concurrent";
        System.setProperty(key, "7");

        ConsumerRuntimePlans.resolve().apply();

        assertEquals("7", PropertiesLoader.get(key));
    }
}
