package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

@Component
public final class HealthHandler {

    @GetMapping(value = "/app/health", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"UP\",\"app\":\"rest-sample-dubbo-consumer\"}",
                "application/json; charset=utf-8"));
    }
}
