package com.reactor.sample.dubbo.consumer.directjson;

import com.reactor.rust.annotations.GenerateDirectJsonWriter;

@GenerateDirectJsonWriter
public record SwarPayload(long identifier, String description, boolean available, long revision) {}
