package com.reactor.sample.dubbo.consumer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerRuntimeConfigurationTest {

    @Test
    void sampleUsesMicroDubboProfileAndStartupIndexes() throws IOException {
        Properties properties = loadProperties();

        assertEquals("micro-dubbo", properties.getProperty("reactor.runtime.profile"));
        assertEquals("true", properties.getProperty("reactor.startup.component-index.enabled"));
        assertEquals("true", properties.getProperty("reactor.startup.component-index.required"));
        assertEquals("true", properties.getProperty("reactor.startup.route-index.validate"));
        assertEquals("true", properties.getProperty("reactor.startup.route-index.required"));
        assertEquals("false", properties.getProperty("reactor.startup.scan.fallback-enabled"));
    }

    @Test
    void sampleKeepsDubboNativeAndBoundedByDefault() throws IOException {
        Properties properties = loadProperties();

        assertEquals("static", properties.getProperty("sample.dubbo.discovery"));
        assertEquals("true", properties.getProperty("reactor.dubbo.enabled"));
        assertEquals("native", properties.getProperty("reactor.dubbo.transport"));
        assertEquals("micro-dubbo", properties.getProperty("reactor.dubbo.runtime-profile"));
        assertEquals("127.0.0.1:20880", properties.getProperty("reactor.dubbo.providers"));
        assertEquals("0", properties.getProperty("reactor.dubbo.retries"));
        assertTrue(Integer.parseInt(properties.getProperty("reactor.dubbo.max-inflight")) <= 32);
        assertTrue(Integer.parseInt(properties.getProperty("reactor.dubbo.native-async-workers")) <= 1);
        assertEquals("0", properties.getProperty("reactor.rust.native-cache.max-bytes"));
    }

    @Test
    void dubboRoutesHaveAdmissionLimits() throws IOException {
        Properties properties = loadProperties();

        assertEquals("true", properties.getProperty("reactor.rust.route-admission.enabled"));
        assertEquals("16", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent"));
        assertEquals("100", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms"));
        assertEquals("16", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.info.max-concurrent"));
        assertEquals("16", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.items.max-concurrent"));
        assertEquals("16", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.attributes.max-concurrent"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent"));
        assertEquals("150", properties.getProperty("reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent"));
        assertEquals("150", properties.getProperty("reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms"));
        assertEquals("4", properties.getProperty("reactor.rust.route-admission.get.api.v1.customers.db.stats.max-concurrent"));
        assertEquals("4", properties.getProperty("reactor.rust.route-admission.get.api.v1.customers.db.by-segment.max-concurrent"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.get.api.v1.customers.db.id.max-concurrent"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.post.api.v1.customers.max-concurrent"));
        assertEquals("150", properties.getProperty("reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms"));
        assertEquals("4", properties.getProperty("reactor.rust.route-admission.post.api.v1.customers.typed.max-concurrent"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent"));
        assertEquals("150", properties.getProperty("reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent"));
        assertEquals("150", properties.getProperty("reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms"));
        assertEquals("4", properties.getProperty("reactor.rust.route-admission.patch.api.v1.customers.id.status.typed.max-concurrent"));
        assertEquals("8", properties.getProperty("reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent"));
        assertEquals("150", properties.getProperty("reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms"));
    }

    @Test
    void routeIndexContainsTypedDubboExamples() throws IOException {
        String routes = resourceText("META-INF/reactor/routes.idx");

        assertTrue(routes.contains("GET /api/v1/catalog/info"));
        assertTrue(routes.contains("GET /api/v1/catalog/items"));
        assertTrue(routes.contains("GET /api/v1/catalog/attributes"));
        assertTrue(routes.contains("GET /api/v1/customers/db/{id}"));
        assertTrue(routes.contains("GET /api/v1/customers/db/by-segment"));
        assertTrue(routes.contains("POST /api/v1/customers/typed"));
        assertTrue(routes.contains("PATCH /api/v1/customers/{id}/status/typed"));
    }

    private static Properties loadProperties() throws IOException {
        try (InputStream input = ConsumerRuntimeConfigurationTest.class
                .getClassLoader()
                .getResourceAsStream("rust-spring.properties")) {
            assertNotNull(input, "rust-spring.properties must be available on the test classpath");
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        }
    }

    private static String resourceText(String name) throws IOException {
        try (InputStream input = ConsumerRuntimeConfigurationTest.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(input, name + " must be available on the test classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
