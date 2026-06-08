package com.reactor.rust.dubbo.sample.dto;

import java.io.Serializable;

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
