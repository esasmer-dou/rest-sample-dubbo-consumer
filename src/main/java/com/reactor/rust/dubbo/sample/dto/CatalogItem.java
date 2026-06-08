package com.reactor.rust.dubbo.sample.dto;

import java.io.Serializable;

public record CatalogItem(
        String sku,
        String name,
        String category,
        double amount,
        String currency,
        boolean active
) implements Serializable {
}
