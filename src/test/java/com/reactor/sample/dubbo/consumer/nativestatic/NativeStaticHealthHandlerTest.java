package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.http.RawResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStaticHealthHandlerTest {

    @Test
    void healthUsesGeneratedJsonResponseWithoutStartingDubbo() {
        RawResponse response = new NativeStaticHealthHandler(null).health().getBody();
        String json = new String(response.getBody(), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"app\":\"rest-sample-dubbo-consumer\""));
        assertTrue(json.contains("\"image\":\"native-static\""));
    }
}
