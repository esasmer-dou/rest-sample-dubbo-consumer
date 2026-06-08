package com.reactor.rust.dubbo.sample.dto;

import java.io.Serializable;

public record CustomerMutationResult(
        String operation,
        String requestId,
        boolean success,
        Long customerId,
        String customerNo,
        String fullName,
        String segment,
        String status,
        String message,
        String generatedAt
) implements Serializable {
}
