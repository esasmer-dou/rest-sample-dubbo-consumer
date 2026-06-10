package com.reactor.rust.dubbo.sample.dto;

import com.dslplatform.json.CompiledJson;

import java.io.Serializable;

@CompiledJson
public record CustomerSummary(
        long id,
        String customerNo,
        String fullName,
        String segment,
        String email,
        String status,
        String updatedAt
) implements Serializable {
}
