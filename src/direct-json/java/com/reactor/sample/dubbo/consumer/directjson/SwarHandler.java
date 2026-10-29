package com.reactor.sample.dubbo.consumer.directjson;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;

@RestController("/api/v1/catalog")
public final class SwarHandler {
    @GetMapping("/literal")
    public SwarPayload literal() { return new SwarPayload(42, "catalog", true, 7); }
}
