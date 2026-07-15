package com.reactor.sample.dubbo.consumer.nativestatic;

import com.reactor.rust.app.RestApplication;

public final class NativeStaticConsumerApplication {

    private NativeStaticConsumerApplication() {}

    public static void main(String[] args) {
        RestApplication.run(NativeStaticConsumerModule.INSTANCE);
    }
}
