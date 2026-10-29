package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.config.PropertiesLoader;

public enum ConsumerReadinessContracts {
    FULL(true, true),
    CATALOG_ONLY(true, false),
    QUERY_ONLY(false, true);

    private final boolean catalogRequired;
    private final boolean customersRequired;

    ConsumerReadinessContracts(boolean catalogRequired, boolean customersRequired) {
        this.catalogRequired = catalogRequired;
        this.customersRequired = customersRequired;
    }

    public boolean catalogRequired() {
        return catalogRequired;
    }

    public boolean customersRequired() {
        return customersRequired;
    }

    public static ConsumerReadinessContracts fromProperties() {
        return select(PropertiesLoader.get("sample.consumer.readiness.contracts", "auto"),
                PropertiesLoader.get("sample.consumer.surface", "full"));
    }

    public static ConsumerReadinessContracts select(String contracts, String surface) {
        return switch (contracts) {
            case "auto" -> switch (surface) {
                case "full" -> FULL;
                case "catalog-only" -> CATALOG_ONLY;
                default -> throw new IllegalArgumentException("Unsupported sample.consumer.surface: " + surface);
            };
            case "full" -> FULL;
            case "catalog-only" -> CATALOG_ONLY;
            case "query-only" -> QUERY_ONLY;
            default -> throw new IllegalArgumentException(
                    "sample.consumer.readiness.contracts must be auto, full, catalog-only or query-only");
        };
    }
}
