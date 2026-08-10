package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(
        name = "Native Static Dubbo Consumer Sample",
        version = "0.6.0",
        description = "Minimal static-provider Dubbo consumer image",
        scanBasePackages = "com.reactor.sample.dubbo.consumer.nativestatic")
public final class NativeStaticConsumerApplication {

    private NativeStaticConsumerApplication() {}

    public static void main(String[] args) {
        RestApplication.run(NativeStaticConsumerApplication.class, args);
    }
}
