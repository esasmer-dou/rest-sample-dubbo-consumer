package com.reactor.rust.dubbo.sample.dto;

import com.dslplatform.json.CompiledJson;

import java.io.Serializable;

@CompiledJson
public record CatalogItem(
        String sku,
        String name,
        String category,
        double amount,
        String currency,
        boolean active
) implements Serializable {
}
