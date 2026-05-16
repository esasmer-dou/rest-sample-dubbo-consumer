package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;

public final class ConsumerProperties {

    private ConsumerProperties() {}

    public static String get(String key) {
        String value = PropertiesLoader.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required consumer property: " + key);
        }
        return value.trim();
    }

    public static int getInt(String key) {
        String value = get(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Consumer property must be an integer: " + key + "=" + value, e);
        }
    }

    public static boolean getBoolean(String key) {
        String value = get(key);
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Consumer property must be a boolean: " + key + "=" + value);
    }
}
