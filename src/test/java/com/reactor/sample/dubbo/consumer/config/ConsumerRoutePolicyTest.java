package com.reactor.sample.dubbo.consumer.config;

import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.sample.dubbo.consumer.handler.CatalogHandler;
import com.reactor.sample.dubbo.consumer.handler.CustomerHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumerRoutePolicyTest {

    @Test
    void catalogUsesSharedReadBudget() throws Exception {
        assertWorkload(CatalogHandler.class, "nestedCatalog", RouteWorkload.Type.RPC_READ,
                ConsumerRouteBudgets.CATALOG_READ);
        assertWorkload(CatalogHandler.class, "dubboMetrics", RouteWorkload.Type.STANDARD, "");
    }

    @Test
    void customerRoutesDeclareBackendCapacityByWorkloadInsteadOfNumericAnnotations() throws Exception {
        assertWorkload(CustomerHandler.class, "databaseCustomers", RouteWorkload.Type.RPC_READ,
                ConsumerRouteBudgets.CUSTOMER_RAW_READ);
        assertWorkload(CustomerHandler.class, "customerStats", RouteWorkload.Type.RPC_READ,
                ConsumerRouteBudgets.CUSTOMER_TYPED_READ);
        assertWorkload(CustomerHandler.class, "createCustomer", RouteWorkload.Type.RPC_COMMAND,
                ConsumerRouteBudgets.CUSTOMER_RAW_CREATE, byte[].class);
        assertWorkload(CustomerHandler.class, "createCustomerTyped", RouteWorkload.Type.RPC_COMMAND,
                ConsumerRouteBudgets.CUSTOMER_TYPED_CREATE,
                com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand.class);
        assertWorkload(CustomerHandler.class, "patchCustomerStatusTyped", RouteWorkload.Type.RPC_COMMAND,
                ConsumerRouteBudgets.CUSTOMER_TYPED_MUTATION, long.class, String.class, String.class);
    }

    private static void assertWorkload(
            Class<?> handler,
            String methodName,
            RouteWorkload.Type type,
            String budget,
            Class<?>... parameterTypes) throws Exception {
        Method method = handler.getDeclaredMethod(methodName, parameterTypes);
        RouteWorkload workload = method.getAnnotation(RouteWorkload.class);
        if (workload == null) {
            workload = handler.getAnnotation(RouteWorkload.class);
        }
        assertEquals(type, workload.value());
        assertEquals(budget, workload.budget());
    }
}
