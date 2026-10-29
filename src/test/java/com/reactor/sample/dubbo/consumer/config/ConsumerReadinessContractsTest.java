package com.reactor.sample.dubbo.consumer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerReadinessContractsTest {
    @Test
    void autoPreservesExistingSurfaceSelection() {
        assertEquals(ConsumerReadinessContracts.FULL, ConsumerReadinessContracts.select("auto", "full"));
        assertEquals(ConsumerReadinessContracts.CATALOG_ONLY,
                ConsumerReadinessContracts.select("auto", "catalog-only"));
    }

    @Test
    void explicitSelectionRequiresExactlyTheSelectedReadContracts() {
        var full = ConsumerReadinessContracts.select("full", "catalog-only");
        assertTrue(full.catalogRequired());
        assertTrue(full.customersRequired());
        var catalog = ConsumerReadinessContracts.select("catalog-only", "full");
        assertTrue(catalog.catalogRequired());
        assertFalse(catalog.customersRequired());
        var query = ConsumerReadinessContracts.select("query-only", "full");
        assertFalse(query.catalogRequired());
        assertTrue(query.customersRequired());
    }

    @Test
    void invalidSelectionFailsAtStartupRatherThanSilentlySkippingDependencies() {
        for (String value : new String[] {"", "none", "queries", "FULL", "full,query-only"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ConsumerReadinessContracts.select(value, "full"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerReadinessContracts.select("auto", "query-only"));
    }
}
