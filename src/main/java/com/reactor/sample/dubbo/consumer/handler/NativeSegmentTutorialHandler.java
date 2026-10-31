package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.dubbo.http.DubboResponses;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.dubbo.consumer.dubbo.CustomerQueryClient;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static com.reactor.sample.dubbo.consumer.http.ConsumerErrorResponses.unavailable;

/** Opt-in native JSON tutorial; CustomerHandler remains the sole HTTP route owner. */
@Component
@RequiresProperty(name = "sample.consumer.surface", value = "full", matchIfMissing = true)
@RequiresProperty(name = "sample.consumer.segment-native-json", value = "true")
public final class NativeSegmentTutorialHandler {

    private final CustomerQueryClient customerQueryClient;

    public NativeSegmentTutorialHandler(CustomerQueryClient customerQueryClient) {
        this.customerQueryClient = customerQueryClient;
    }

    public CompletableFuture<ResponseEntity<RawResponse>> customersBySegment(String segment, int limit) {
        return DubboResponses.replyAsync(
                customerQueryClient.findCustomersBySegmentUtf8NativeJsonAsync(
                                limit, segment.getBytes(StandardCharsets.UTF_8))
                        .thenApply(DubboResponses::ok)
                        .exceptionally(error -> unavailable("dubbo_customer_segment_query_unavailable", error)),
                response -> response);
    }
}
