package com.reactor.sample.dubbo.consumer;

import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.DubboConsumerConfig;
import com.reactor.rust.json.DslJsonService;
import com.reactor.sample.dubbo.consumer.config.ConsumerReadinessContracts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerRuntimeConfigurationTest {

    @Test
    void fullStaticImagesKeepBoundedJitEnabledDefaults() throws IOException {
        for (String file : new String[] {
                "Dockerfile.jlink", "Dockerfile.jlink.workspace", "Dockerfile.jlink.full-static.workspace"}) {
            String dockerfile = Files.readString(Path.of("docker", "images", file));
            assertTrue(dockerfile.contains("-Xms24m -Xmx96m"), file);
            assertTrue(dockerfile.contains("-XX:ActiveProcessorCount=1"), file);
            assertFalse(dockerfile.contains("-Xnojit"), file);
        }
    }

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
        assertEquals("true", properties.getProperty("sample.dubbo.read-retry-on-io-error"));
        assertEquals("true", properties.getProperty("reactor.dubbo.enabled"));
        assertEquals("127.0.0.1:20880", properties.getProperty("reactor.dubbo.providers"));

        DubboConsumerConfig config = DubboConsumerConfig.fromProperties(properties);
        assertEquals("native", config.transport());
        assertEquals("micro-dubbo", config.runtimeProfile());
        assertEquals(0, config.retries());
        assertEquals(64, config.maxInflight());
        assertEquals(2, config.nativeAsyncWorkers());
        assertEquals(2, config.nativeConnectionsPerEndpoint());
    }

    @Test
    void dubboRoutesHaveAdmissionLimits() throws IOException {
        Properties properties = loadProperties("config/advanced-tuning.properties");

        assertEquals("true", properties.getProperty("reactor.rust.route-admission.enabled"));
        assertBudget(properties, "rpc-catalog-read", "16", "100");
        assertBudget(properties, "rpc-customer-raw-read", "8", "150");
        assertBudget(properties, "rpc-customer-typed-read", "4", "150");
        assertBudget(properties, "rpc-customer-raw-create", "4", "250");
        assertBudget(properties, "rpc-customer-typed-create", "4", "250");
        assertBudget(properties, "rpc-customer-raw-mutation", "8", "150");
        assertBudget(properties, "rpc-customer-typed-mutation", "4", "150");
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

    @Test
    void typedCustomerCommandHasCompileTimeJsonReader() {
        byte[] json = """
                {
                  "customerNo": "C-9001",
                  "fullName": "Mustafa Korkmaz",
                  "segment": "enterprise",
                  "email": "mustafa.korkmaz@example.com",
                  "requestId": "req-json-reader"
                }
                """.getBytes(StandardCharsets.UTF_8);

        CreateCustomerCommand command = DslJsonService.parse(json, CreateCustomerCommand.class);

        assertEquals("C-9001", command.customerNo());
        assertEquals("Mustafa Korkmaz", command.fullName());
        assertEquals("enterprise", command.segment());
        assertEquals("mustafa.korkmaz@example.com", command.email());
        assertEquals("req-json-reader", command.requestId());
    }

    @Test
    void readinessDefaultsAndStandaloneQueryOverlayMatchClientSelection() throws IOException {
        Properties defaults = loadProperties();
        assertEquals("true", defaults.getProperty("sample.consumer.catalog.enabled"));
        assertEquals(ConsumerReadinessContracts.FULL, ConsumerReadinessContracts.select(
                defaults.getProperty("sample.consumer.readiness.contracts"),
                defaults.getProperty("sample.consumer.surface")));

        Properties production = loadProperties("rust-spring.properties", "config/production.properties");
        Properties query = loadProperties("rust-spring.properties", "config/query-only.properties");
        assertEquals("auto", production.getProperty("sample.consumer.readiness.contracts"));
        assertEquals("true", production.getProperty("sample.consumer.catalog.enabled"));
        assertEquals("full", query.getProperty("sample.consumer.surface"));
        assertEquals("false", query.getProperty("sample.consumer.catalog.enabled"));
        assertEquals(ConsumerReadinessContracts.QUERY_ONLY, ConsumerReadinessContracts.select(
                query.getProperty("sample.consumer.readiness.contracts"),
                query.getProperty("sample.consumer.surface")));
        for (String key : new String[] {"reactor.runtime.profile", "reactor.runtime.low-rss-gate.mode",
                "reactor.dubbo.timeout-ms", "reactor.dubbo.retries", "reactor.dubbo.max-inflight",
                "reactor.dubbo.native-connections-per-endpoint",
                "reactor.dubbo.native-max-idle-connections-per-endpoint",
                "reactor.dubbo.native-idle-connection-ttl-ms", "reactor.dubbo.native-async-workers",
                "reactor.dubbo.native-async-queue-capacity", "reactor.dubbo.native-async-transport",
                "reactor.dubbo.native-thread-stack-bytes"}) {
            assertEquals(production.getProperty(key), query.getProperty(key), key);
        }
    }

    @Test
    void generatedStartupBeansAndCatalogRoutesUseSelectedConditions() throws IOException {
        Path annotations = Path.of("target", "generated-sources", "annotations");
        Path generated = annotations.resolve("com/reactor/sample/dubbo/consumer");
        String condition = "ConfigurationBinder.matches(\"sample.consumer.catalog.enabled\", \"true\", true)";
        assertTrue(Files.readString(generated.resolve(
                "dubbo/DubboClients__DubboConfiguration__ReactorFactory.java")).contains(condition));
        assertTrue(Files.readString(generated.resolve(
                "handler/CatalogHandler__ReactorFactory.java")).contains(condition));
        String health = Files.readString(generated.resolve("handler/HealthHandler__ReactorFactory.java"));
        for (String client : new String[] {"NestedCatalogClient", "CustomerQueryClient"}) {
            String clientClass = "com.reactor.sample.dubbo.consumer.dubbo." + client + ".class";
            assertTrue(health.contains("hasBean(" + clientClass + ")"), client + " optional presence lookup");
            assertTrue(health.contains("getBean(" + clientClass + ")"), client + " optional bean lookup");
            assertFalse(health.contains("hasBean(" + client + ".class)"), client + " must be qualified");
            assertFalse(health.contains("getBean(" + client + ".class)"), client + " must be qualified");
        }
        assertTrue(health.contains("markGeneratedReflectionFree(com.reactor.sample.dubbo.consumer.handler.HealthHandler.class)"));
        assertTrue(health.contains("getBean(com.reactor.sample.dubbo.consumer.config.ConsumerReadinessContracts.class)"));
        String readiness = Files.readString(generated.resolve(
                "config/ConsumerReadinessConfiguration__ReactorFactory.java"));
        assertTrue(readiness.contains("hasBean(\"consumerReadinessContracts\")"));
        assertTrue(readiness.contains("registerGeneratedFactory(com.reactor.sample.dubbo.consumer.config.ConsumerReadinessContracts.class"));
        assertTrue(readiness.contains("configuration.consumerReadinessContracts()"));
        String descriptor = Files.readString(annotations.resolve(
                "com/reactor/generated/ReactorApplicationDescriptor.java"));
        assertTrue(descriptor.contains("ConsumerReadinessConfiguration__ReactorFactory.register(container)"));
        assertTrue(descriptor.contains("ConsumerReadinessConfiguration__ReactorFactory.registerConfigurationBeans(container)"));
        assertTrue(resourceText("META-INF/reactor/components.idx").contains(
                "com.reactor.sample.dubbo.consumer.config.ConsumerReadinessConfiguration"));
    }

    @Test
    void generatedSegmentTutorialIsOptInAndInjectedWithoutDuplicateRoutes() throws IOException {
        Path handlerSources = Path.of("target", "generated-sources", "annotations",
                "com", "reactor", "sample", "dubbo", "consumer", "handler");
        String tutorial = Files.readString(handlerSources.resolve("NativeSegmentTutorialHandler__ReactorFactory.java"));
        assertTrue(tutorial.contains("ConfigurationBinder.matches(\"sample.consumer.surface\", \"full\", true)"));
        assertTrue(tutorial.contains("ConfigurationBinder.matches(\"sample.consumer.segment-native-json\", \"true\", false)"));
        String customer = Files.readString(handlerSources.resolve("CustomerHandler__ReactorFactory.java"));
        String type = "com.reactor.sample.dubbo.consumer.handler.NativeSegmentTutorialHandler.class";
        assertTrue(customer.contains("hasBean(" + type + ")"));
        assertTrue(customer.contains("getBean(" + type + ")"));
        String routes = resourceText("META-INF/reactor/routes.idx");
        assertEquals(1L, routes.lines().filter(line ->
                line.startsWith("GET /api/v1/customers/db/by-segment ")).count());
    }

    private static Properties loadProperties(String... resources) throws IOException {
        String[] names = resources.length == 0 ? new String[] {"rust-spring.properties"} : resources;
        Properties properties = new Properties();
        for (String resource : names) {
            try (InputStream input = ConsumerRuntimeConfigurationTest.class
                    .getClassLoader()
                    .getResourceAsStream(resource)) {
                assertNotNull(input, resource + " must be available on the test classpath");
                properties.load(input);
            }
        }
        return properties;
    }

    private static void assertBudget(
            Properties properties,
            String budget,
            String maxConcurrent,
            String queueTimeoutMs) {
        String prefix = "reactor.rust.route-budget." + budget + ".route-admission";
        assertEquals(maxConcurrent, properties.getProperty(prefix + ".max-concurrent"));
        assertEquals(queueTimeoutMs, properties.getProperty(prefix + ".queue-timeout-ms"));
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
