package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

public final class NativeStaticHealthHandler {

    @GetMapping(value = "/app/health", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"UP\",\"app\":\"rest-sample-dubbo-consumer\",\"image\":\"native-static\"}",
                "application/json; charset=utf-8"));
    }
}
