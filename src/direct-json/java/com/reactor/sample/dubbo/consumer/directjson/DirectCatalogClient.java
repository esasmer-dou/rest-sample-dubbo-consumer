package com.reactor.sample.dubbo.consumer.directjson;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.dubbo.annotation.DubboReference;
import com.reactor.rust.dubbo.runtime.DubboClientOptions;
import com.reactor.rust.dubbo.runtime.DubboRuntimeOptions;
import com.reactor.rust.dubbo.runtime.StandaloneDubboClients;
import com.reactor.rust.dubbo.sample.StreamCatalogService;

@Component
public final class DirectCatalogClient implements AutoCloseable {
    private final StandaloneDubboClients clients;
    @DubboReference(version = "0.0.0")
    private StreamCatalogService catalog;

    public DirectCatalogClient() {
        clients = new StandaloneDubboClients(new DubboClientOptions(
                PropertiesLoader.get("sample.direct-json.providers", "127.0.0.1:20880"),
                PropertiesLoader.getInt("sample.direct-json.connections", 2), 32, 64, 30_000,
                PropertiesLoader.getInt("sample.direct-json.timeout-ms", 3000),
                1024 * 1024, 10_000, 1024, 4, 4096), DubboRuntimeOptions.microDefaults());
        try { catalog = clients.require(StreamCatalogService.class, "", "0.0.0"); }
        catch (RuntimeException | Error failure) { clients.close(); throw failure; }
    }

    public StreamCatalogService catalog() { return catalog; }
    @Override public void close() { clients.close(); }
}
