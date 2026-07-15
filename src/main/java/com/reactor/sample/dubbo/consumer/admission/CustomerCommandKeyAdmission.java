package com.reactor.sample.dubbo.consumer.admission;

import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.config.PropertiesLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Component
public final class CustomerCommandKeyAdmission {

    private final boolean enabled;
    private final Semaphore[] stripes;
    private final int mask;
    private final LongAdder accepted = new LongAdder();
    private final LongAdder rejected = new LongAdder();

    public CustomerCommandKeyAdmission() {
        this.enabled = PropertiesLoader.requireBoolean("sample.command.customer-key-admission.enabled");
        int maxConcurrentPerKey =
                PropertiesLoader.requireInt("sample.command.customer-key-admission.max-concurrent-per-key");
        int stripeCount = nextPowerOfTwo(
                PropertiesLoader.requireInt("sample.command.customer-key-admission.stripes"));
        this.mask = stripeCount - 1;
        this.stripes = new Semaphore[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            this.stripes[i] = new Semaphore(Math.max(1, maxConcurrentPerKey));
        }
    }

    public <T> CompletableFuture<T> execute(long key, Supplier<CompletableFuture<T>> action) {
        if (!enabled) {
            return action.get();
        }
        Semaphore semaphore = stripes[stripeIndex(key)];
        if (!semaphore.tryAcquire()) {
            rejected.increment();
            return CompletableFuture.failedFuture(new CustomerCommandKeyAdmissionException(
                    "customer command key is already in-flight: " + key));
        }
        accepted.increment();
        try {
            return action.get().whenComplete((ignored, error) -> semaphore.release());
        } catch (Throwable error) {
            semaphore.release();
            return CompletableFuture.failedFuture(error);
        }
    }

    private int stripeIndex(long key) {
        long mixed = key ^ (key >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return ((int) mixed) & mask;
    }

    private static int nextPowerOfTwo(int value) {
        int normalized = Math.max(1, Math.min(value, 65_536));
        if (normalized == 1) {
            return 1;
        }
        return Integer.highestOneBit(normalized - 1) << 1;
    }

    public void reset() {
        accepted.reset();
        rejected.reset();
    }

    public String metricsJson() {
        return "{\"enabled\":" + enabled + ","
                + "\"stripes\":" + stripes.length + ","
                + "\"accepted\":" + accepted.sum() + ","
                + "\"rejected\":" + rejected.sum() + "}";
    }

    public static final class CustomerCommandKeyAdmissionException extends RuntimeException {
        public CustomerCommandKeyAdmissionException(String message) {
            super(message);
        }
    }
}
