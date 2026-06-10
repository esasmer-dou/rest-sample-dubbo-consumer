package com.reactor.rust.dubbo.sample.dto;

import com.dslplatform.json.CompiledJson;

import java.io.Serializable;

@CompiledJson
public record CustomerStats(
        int total,
        int active,
        int passive,
        String generatedAt
) implements Serializable {
}
