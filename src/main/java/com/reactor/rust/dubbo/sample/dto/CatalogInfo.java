package com.reactor.rust.dubbo.sample.dto;

import java.io.Serializable;

public record CatalogInfo(
        String id,
        String title,
        String ownerTeam,
        String region,
        int itemCount,
        String generatedAt
) implements Serializable {
}
