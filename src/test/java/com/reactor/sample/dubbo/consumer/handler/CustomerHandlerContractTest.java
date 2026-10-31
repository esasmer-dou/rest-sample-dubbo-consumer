package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.KeyAdmission;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.concurrent.LongKeyAdmission;
import com.reactor.rust.concurrent.KeyAdmissionSupport;
import com.reactor.rust.dubbo.DubboConsumerException;
import com.reactor.rust.dubbo.NativeDubboMethodInvoker;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerHandlerContractTest {

    @Test
    void allFourKeyedCommandsKeepTheirLegacyAdmissionErrorContract() throws Throwable {
        var admission = new LongKeyAdmission(true, 1, 16);
        var pending = new CompletableFuture<Void>();
        var held = admission.execute(41L, () -> pending);
        var handler = new CustomerHandler(null, null, admission, Optional.empty());
        try {
            assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                            () -> handler.patchCustomerSegment(41L, new byte[0]), handler::segmentRejected),
                    "dubbo_customer_segment_unavailable");
            assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                            () -> handler.patchCustomerStatus(41L, new byte[0]), handler::statusRejected),
                    "dubbo_customer_status_unavailable");
            assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                            () -> handler.patchCustomerStatusTyped(41L, "ACTIVE", "request-41"), handler::typedStatusRejected),
                    "dubbo_customer_status_typed_unavailable");
            assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                            () -> handler.deleteCustomer(41L, null), handler::deleteRejected),
                    "dubbo_customer_delete_unavailable");
            assertFalse(pending.isDone());
            assertTrue(admission.metricsJson().contains("\"accepted\":1"));
            assertTrue(admission.metricsJson().contains("\"rejected\":4"));
        } finally {
            pending.complete(null);
        }
        held.join();
        admission.execute(41L, () -> CompletableFuture.completedFuture(null)).join();
        assertTrue(admission.metricsJson().contains("\"accepted\":2"));
    }

    @Test
    void fourCommandsDeclareTheirExactGeneratedAdmissionAndCompatibilityConstructor() throws Exception {
        assertAdmission("patchCustomerSegment", "segmentRejected", long.class, byte[].class);
        assertAdmission("patchCustomerStatus", "statusRejected", long.class, byte[].class);
        assertAdmission("patchCustomerStatusTyped", "typedStatusRejected", long.class, String.class, String.class);
        assertAdmission("deleteCustomer", "deleteRejected", long.class, byte[].class);
        var compatibility = CustomerHandler.class.getConstructor(
                com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient.class,
                CustomerCommandClient.class, LongKeyAdmission.class);
        assertFalse(compatibility.isAnnotationPresent(com.reactor.rust.di.annotation.Autowired.class));
        var injected = Arrays.stream(CustomerHandler.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(com.reactor.rust.di.annotation.Autowired.class))
                .toList();
        assertEquals(1, injected.size());
        assertEquals(4, injected.getFirst().getParameterCount());
        assertEquals(LongKeyAdmission.class, injected.getFirst().getParameterTypes()[2]);
        assertEquals(Optional.class, injected.getFirst().getParameterTypes()[3]);
        assertEquals(0, Arrays.stream(CustomerHandler.class.getDeclaredFields())
                .filter(field -> field.getType() == LongKeyAdmission.class).count());
    }

    @Test
    void synchronousLazyInvocationFailuresKeep503AndReleaseTheSingleGeneratedAdmission() throws Throwable {
        var calls = new AtomicInteger();
        var failure = new DubboConsumerException("lazy reference unavailable");
        NativeDubboMethodInvoker<byte[]> raw = failingLazyInvoker(failure, calls);
        NativeDubboMethodInvoker<CustomerMutationResult> typed = failingLazyInvoker(failure, calls);
        var constructor = CustomerCommandClient.class.getDeclaredConstructor(
                NativeDubboMethodInvoker.class, NativeDubboMethodInvoker.class,
                NativeDubboMethodInvoker.class, NativeDubboMethodInvoker.class,
                NativeDubboMethodInvoker.class, NativeDubboMethodInvoker.class);
        constructor.setAccessible(true);
        var commands = (CustomerCommandClient) constructor.newInstance(raw, typed, raw, raw, raw, typed);
        var admission = new LongKeyAdmission(true, 1, 16);
        // The original three-argument call, including a null admission, must remain unambiguous.
        var handler = new CustomerHandler(null, commands, null);
        assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                        () -> handler.patchCustomerSegment(41L, new byte[0]), handler::segmentRejected),
                "dubbo_customer_segment_unavailable");
        assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                        () -> handler.patchCustomerStatus(41L, new byte[0]), handler::statusRejected),
                "dubbo_customer_status_unavailable");
        assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                        () -> handler.patchCustomerStatusTyped(41L, "ACTIVE", "request-41"), handler::typedStatusRejected),
                "dubbo_customer_status_typed_unavailable");
        assertUnavailable(KeyAdmissionSupport.invoke(admission, 41L,
                        () -> handler.deleteCustomer(41L, null), handler::deleteRejected),
                "dubbo_customer_delete_unavailable");
        assertEquals(4, calls.get(), "No retries or duplicate invocation wrappers");
        assertTrue(admission.metricsJson().contains("\"accepted\":4"));
        assertTrue(admission.metricsJson().contains("\"rejected\":0"));
        admission.execute(41L, () -> CompletableFuture.completedFuture(null)).join();
        assertTrue(admission.metricsJson().contains("\"accepted\":5"));
    }

    @SuppressWarnings("unchecked")
    private static <T> NativeDubboMethodInvoker<T> failingLazyInvoker(
            RuntimeException failure, AtomicInteger calls) throws Exception {
        // Exercise the real lazy invocation path without JNI or an additional production test hook.
        var constructor = NativeDubboMethodInvoker.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        Supplier<NativeDubboMethodInvoker<T>> supplier = () -> {
            calls.incrementAndGet();
            throw failure;
        };
        return (NativeDubboMethodInvoker<T>) constructor.newInstance(supplier);
    }

    private static void assertAdmission(String method, String mapper, Class<?>... parameters) throws Exception {
        var annotation = CustomerHandler.class.getMethod(method, parameters).getAnnotation(KeyAdmission.class);
        assertEquals("customerCommandKeyAdmission", annotation.bean());
        assertEquals("customerId", annotation.key());
        assertEquals(mapper, annotation.onRejected());
        assertEquals(CompletableFuture.class, CustomerHandler.class.getMethod(mapper, Throwable.class).getReturnType());
    }

    @Test
    void rejectionMappersDelegateToTheExistingErrorContractWithBothSettings() throws Exception {
        String key = "sample.errors.common-contract";
        String previous = System.getProperty(key);
        String handlerName = CustomerHandler.class.getName();
        String errorsName = "com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses";
        var codes = java.util.Map.of(
                "segmentRejected", "dubbo_customer_segment_unavailable",
                "statusRejected", "dubbo_customer_status_unavailable",
                "typedStatusRejected", "dubbo_customer_status_typed_unavailable",
                "deleteRejected", "dubbo_customer_delete_unavailable");
        try {
            for (boolean common : new boolean[]{false, true}) {
                System.setProperty(key, Boolean.toString(common));
                // Each setting initializes the existing static policy in an isolated loader; production stays reflection-free.
                try (var loader = new URLClassLoader(new java.net.URL[]{
                        CustomerHandler.class.getProtectionDomain().getCodeSource().getLocation()}, getClass().getClassLoader()) {
                    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                        if (!name.equals(handlerName) && !name.startsWith(handlerName + "$")
                                && !name.equals(errorsName)) return super.loadClass(name, resolve);
                        synchronized (getClassLoadingLock(name)) {
                            Class<?> type = findLoadedClass(name);
                            if (type == null) type = findClass(name);
                            if (resolve) resolveClass(type);
                            return type;
                        }
                    }
                }) {
                    Class<?> type = loader.loadClass(handlerName);
                    Object handler = type.getConstructor(
                            com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient.class,
                            com.reactor.sample.dubbo.consumer.dubbo.CustomerCommandClient.class, LongKeyAdmission.class)
                            .newInstance(null, null, null);
                    var existing = loader.loadClass(errorsName).getMethod("unavailable", String.class, Throwable.class);
                    for (var route : codes.entrySet()) {
                        var error = new RejectedExecutionException("command key is already in flight");
                        var expected = (ResponseEntity<?>) existing.invoke(null, route.getValue(), error);
                        var result = (CompletableFuture<?>) type.getMethod(route.getKey(), Throwable.class).invoke(handler, error);
                        var actual = (ResponseEntity<?>) result.join();
                        assertEquals(503, actual.getStatus().getCode());
                        assertEquals(expected.getStatus(), actual.getStatus());
                        assertEquals(((RawResponse) expected.getBody()).getBody().length,
                                ((RawResponse) actual.getBody()).getBody().length);
                        org.junit.jupiter.api.Assertions.assertArrayEquals(
                                ((RawResponse) expected.getBody()).getBody(), ((RawResponse) actual.getBody()).getBody());
                        String json = new String(((RawResponse) actual.getBody()).getBody(), StandardCharsets.UTF_8);
                        assertTrue(json.contains(common ? "service_unavailable" : route.getValue()), json);
                    }
                }
            }
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    @Test
    void segmentRouteKeepsItsPathDefaultsAndSingleOwner() throws Exception {
        var route = CustomerHandler.class.getDeclaredMethod("customersBySegment", String.class, int.class);
        assertEquals("/db/by-segment", route.getAnnotation(GetMapping.class).value());
        assertEquals("standard", route.getParameters()[0].getAnnotation(RequestParam.class).defaultValue());
        assertEquals("10", route.getParameters()[1].getAnnotation(RequestParam.class).defaultValue());
        assertNull(NativeSegmentTutorialHandler.class.getAnnotation(RestController.class));
        assertNull(NativeSegmentTutorialHandler.class.getDeclaredMethod(
                "customersBySegment", String.class, int.class).getAnnotation(GetMapping.class));
        var conditions = NativeSegmentTutorialHandler.class.getAnnotationsByType(RequiresProperty.class);
        assertEquals(2, conditions.length);
        assertTrue(Arrays.stream(conditions).anyMatch(condition ->
                condition.name().equals("sample.consumer.surface")
                        && condition.value().equals("full") && condition.matchIfMissing()));
        assertTrue(Arrays.stream(conditions).anyMatch(condition ->
                condition.name().equals("sample.consumer.segment-native-json")
                        && condition.value().equals("true") && !condition.matchIfMissing()));
    }

    @Test
    void absentNativeHelperBindsTypedQueryOnceWithPrimitiveLimit() throws Exception {
        var handler = new CustomerHandler(null, null, null);
        Object binding = segmentBinding(handler);
        assertCapturedReceiver(binding, handler);
        assertSegmentBindingUnchangedAfterFlagChange(handler, binding, "true");
    }

    @Test
    void presentNativeHelperBindsNativeQueryOnceIncludingCompatibilityConstructor() throws Exception {
        var nativeHelper = new NativeSegmentTutorialHandler(null);
        var handler = new CustomerHandler(null, null, null, Optional.of(nativeHelper));
        Object binding = segmentBinding(handler);
        assertCapturedReceiver(binding, nativeHelper);
        assertSegmentBindingUnchangedAfterFlagChange(handler, binding, "false");
    }

    private static Object segmentBinding(CustomerHandler handler) throws Exception {
        var field = CustomerHandler.class.getDeclaredField("segmentQuery");
        assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
        var query = field.getType().getDeclaredMethod("query", String.class, int.class);
        assertEquals(CompletableFuture.class, query.getReturnType());
        field.setAccessible(true);
        return field.get(handler);
    }

    private static void assertCapturedReceiver(Object binding, Object expected) throws Exception {
        // Inspect only the construction-time method reference; no upstream/JNI call is needed.
        for (var field : binding.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.get(binding) == expected) return;
        }
        org.junit.jupiter.api.Assertions.fail("Segment query must bind the selected receiver at construction");
    }

    private static void assertSegmentBindingUnchangedAfterFlagChange(
            CustomerHandler handler, Object binding, String value) throws Exception {
        String key = "sample.consumer.segment-native-json";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, value);
            assertSame(binding, segmentBinding(handler));
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    private static void assertUnavailable(
            CompletableFuture<ResponseEntity<RawResponse>> result, String code) {
        var response = result.join();
        assertEquals(503, response.getStatus().getCode());
        String json = new String(response.getBody().getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains('"' + code + '"'), json);
        assertTrue(json.contains("Upstream service is temporarily unavailable"), json);
        assertFalse(json.contains("command key is already in flight"), json);
    }
}
