package com.reactor.sample.dubbo.consumer.config;

/**
 * Stable names for backend capacity budgets shared by route metadata and profiles.
 */
public final class ConsumerRouteBudgets {

    public static final String CATALOG_READ = "rpc-catalog-read";
    public static final String CUSTOMER_RAW_READ = "rpc-customer-raw-read";
    public static final String CUSTOMER_TYPED_READ = "rpc-customer-typed-read";
    public static final String CUSTOMER_RAW_CREATE = "rpc-customer-raw-create";
    public static final String CUSTOMER_TYPED_CREATE = "rpc-customer-typed-create";
    public static final String CUSTOMER_RAW_MUTATION = "rpc-customer-raw-mutation";
    public static final String CUSTOMER_TYPED_MUTATION = "rpc-customer-typed-mutation";

    private ConsumerRouteBudgets() {}
}
