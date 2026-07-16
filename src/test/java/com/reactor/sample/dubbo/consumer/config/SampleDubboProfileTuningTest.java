package com.reactor.sample.dubbo.consumer.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleDubboProfileTuningTest {

    private static final List<String> KEYS = List.of(
            "sample.dubbo.capacity-profile",
            "reactor.dubbo.max-inflight",
            "reactor.dubbo.native-connections-per-endpoint",
            "reactor.dubbo.native-max-idle-connections-per-endpoint",
            "reactor.dubbo.native-idle-connection-ttl-ms",
            "reactor.dubbo.native-async-workers",
            "reactor.dubbo.native-async-queue-capacity",
            "reactor.dubbo.native-async-transport",
            "reactor.rust.route-admission.post.api.v1.customers.typed.max-concurrent",
            "reactor.rust.route-admission.post.api.v1.customers.typed.queue-timeout-ms");

    @AfterEach
    void clearProperties() {
        KEYS.forEach(System::clearProperty);
    }

    @Test
    void micro2x2AlignsNativeRpcAndTypedWriteAdmission() {
        System.setProperty("sample.dubbo.capacity-profile", "micro-2x2");

        SampleDubboProfileTuning.apply();

        assertEquals("64", System.getProperty("reactor.dubbo.max-inflight"));
        assertEquals("2", System.getProperty("reactor.dubbo.native-connections-per-endpoint"));
        assertEquals("2", System.getProperty("reactor.dubbo.native-max-idle-connections-per-endpoint"));
        assertEquals("30000", System.getProperty("reactor.dubbo.native-idle-connection-ttl-ms"));
        assertEquals("2", System.getProperty("reactor.dubbo.native-async-workers"));
        assertEquals("64", System.getProperty("reactor.dubbo.native-async-queue-capacity"));
        assertEquals("blocking", System.getProperty("reactor.dubbo.native-async-transport"));
        assertEquals("8", System.getProperty(
                "reactor.rust.route-admission.post.api.v1.customers.typed.max-concurrent"));
        assertEquals("250", System.getProperty(
                "reactor.rust.route-admission.post.api.v1.customers.typed.queue-timeout-ms"));
    }
}
