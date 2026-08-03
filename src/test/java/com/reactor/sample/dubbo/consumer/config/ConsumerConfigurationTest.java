package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.concurrent.LongKeyAdmission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerConfigurationTest {

    @Test
    void localDefaultsDeclareBoundedDubboAndRouteCapacity() {
        PropertiesLoader.load();

        assertEquals(64, PropertiesLoader.requireInt("reactor.dubbo.max-inflight"));
        assertEquals(2, PropertiesLoader.requireInt(
                "reactor.dubbo.native-connections-per-endpoint"));
        assertEquals(4, PropertiesLoader.requireInt(
                "reactor.rust.route-budget.rpc-customer-raw-create"
                        + ".route-admission.max-concurrent"));

        LongKeyAdmission admission = new ConsumerConfiguration().customerCommandAdmission();
        assertTrue(admission.metricsJson().contains("\"enabled\":true"));
    }
}
