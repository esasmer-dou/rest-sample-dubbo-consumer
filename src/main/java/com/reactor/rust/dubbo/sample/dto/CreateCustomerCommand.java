package com.reactor.rust.dubbo.sample.dto;

import java.io.Serializable;

public record CreateCustomerCommand(
        String customerNo,
        String fullName,
        String segment,
        String email,
        String requestId
) implements Serializable {
}
