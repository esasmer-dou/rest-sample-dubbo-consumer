package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfilePlan;

import java.util.Locale;

/** Immutable capacity plans for the sample's measured runtime shapes. */
public final class ConsumerRuntimePlans {

    private static final String BALANCED_DUBBO = "balanced-dubbo";
    private static final String MICRO_2X2 = "micro-2x2";

    private ConsumerRuntimePlans() {}

    public static RuntimeProfilePlan resolve() {
        String profile = PropertiesLoader.get("reactor.runtime.profile", "")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (BALANCED_DUBBO.equals(profile)) {
            return balanced();
        }
        String capacity = PropertiesLoader.get("sample.dubbo.capacity-profile", "micro-1x1")
                .trim()
                .toLowerCase(Locale.ROOT);
        return micro(capacity);
    }

    private static RuntimeProfilePlan micro(String capacity) {
        RuntimeProfilePlan.Builder plan = RuntimeProfilePlan.named(capacity)
                .routeBudget(ConsumerRouteBudgets.CATALOG_READ, 16, 100)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_RAW_READ, 8, 150)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_TYPED_READ, 4, 150)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_RAW_CREATE, 4, 250)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_TYPED_CREATE, 4, 250)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_RAW_MUTATION, 8, 150)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_TYPED_MUTATION, 4, 150);
        if (MICRO_2X2.equals(capacity)) {
            plan.positiveInt("reactor.dubbo.max-inflight", 64)
                    .positiveInt("reactor.dubbo.native-connections-per-endpoint", 2)
                    .positiveInt("reactor.dubbo.native-max-idle-connections-per-endpoint", 2)
                    .positiveLong("reactor.dubbo.native-idle-connection-ttl-ms", 30_000)
                    .positiveInt("reactor.dubbo.native-async-workers", 2)
                    .positiveInt("reactor.dubbo.native-async-queue-capacity", 64)
                    .oneOf("reactor.dubbo.native-async-transport", "blocking",
                            "blocking", "tokio-demux");
        }
        return plan.build();
    }

    private static RuntimeProfilePlan balanced() {
        return RuntimeProfilePlan.named(BALANCED_DUBBO)
                .value("reactor.dubbo.runtime-profile", BALANCED_DUBBO)
                .positiveInt("reactor.rust.jni.workers", 16)
                .positiveInt("reactor.rust.jni.queue-capacity", 1024)
                .positiveInt("reactor.rust.async.max-inflight", 1024)
                .positiveInt("reactor.dubbo.max-inflight", 512)
                .positiveInt("reactor.dubbo.native-connections-per-endpoint", 16)
                .positiveInt("reactor.dubbo.native-max-idle-connections-per-endpoint", 4)
                .positiveLong("reactor.dubbo.native-idle-connection-ttl-ms", 30_000)
                .positiveInt("reactor.dubbo.native-async-workers", 8)
                .positiveInt("reactor.dubbo.native-async-queue-capacity", 1024)
                .oneOf("reactor.dubbo.native-async-transport", "tokio-demux",
                        "blocking", "tokio-demux")
                .positiveInt("reactor.dubbo.catalog.min-inflight", 16)
                .positiveInt("reactor.dubbo.catalog.initial-inflight", 64)
                .positiveInt("reactor.dubbo.catalog.max-inflight", 64)
                .positiveInt("reactor.dubbo.catalog.response-timeout-ms", 1200)
                .routeBudget(ConsumerRouteBudgets.CATALOG_READ, 96, 250)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_RAW_READ, 32, 250)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_TYPED_READ, 16, 200)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_RAW_CREATE, 32, 250)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_TYPED_CREATE, 16, 200)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_RAW_MUTATION, 32, 250)
                .routeBudget(ConsumerRouteBudgets.CUSTOMER_TYPED_MUTATION, 16, 200)
                .positiveInt("sample.command.customer-key-admission.max-concurrent-per-key", 1)
                .positiveInt("sample.command.customer-key-admission.stripes", 2048)
                .build();
    }
}
