package com.reactor.rust.dubbo.sample.dto;

import com.dslplatform.json.CompiledJson;

import java.io.Serializable;

@CompiledJson
public record CreateCustomerCommand(
        String customerNo,
        String fullName,
        String segment,
        String email,
        String requestId
) implements Serializable {
}
