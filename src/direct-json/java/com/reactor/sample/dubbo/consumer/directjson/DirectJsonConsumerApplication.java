package com.reactor.sample.dubbo.consumer.directjson;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(name = "Direct JSON Dubbo Consumer", description = "Native Hessian to HTTP JSON",
        scanBasePackages = "com.reactor.sample.dubbo.consumer.directjson")
public final class DirectJsonConsumerApplication {
    private DirectJsonConsumerApplication() {}
    public static void main(String[] args) { RestApplication.run(DirectJsonConsumerApplication.class, args); }
}
