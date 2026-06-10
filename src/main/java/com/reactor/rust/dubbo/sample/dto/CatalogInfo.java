package com.reactor.rust.dubbo.sample.dto;

import com.dslplatform.json.CompiledJson;

import java.io.Serializable;

@CompiledJson
public record CatalogInfo(
        String id,
        String title,
        String ownerTeam,
        String region,
        int itemCount,
        String generatedAt
) implements Serializable {
}
