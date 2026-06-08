package com.reactor.rust.dubbo.sample.dto;

import java.io.Serializable;

public record CustomerStats(
        int total,
        int active,
        int passive,
        String generatedAt
) implements Serializable {
}
