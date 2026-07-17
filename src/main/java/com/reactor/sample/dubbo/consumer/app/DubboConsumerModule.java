package com.reactor.sample.dubbo.consumer.app;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.sample.dubbo.consumer.config.CatalogOnlyDubboClientFactory;
import com.reactor.sample.dubbo.consumer.config.ConsumerRuntimePlans;
import com.reactor.sample.dubbo.consumer.handler.CatalogHandler;
import com.reactor.sample.dubbo.consumer.handler.CatalogOnlyHandler;
import com.reactor.sample.dubbo.consumer.handler.CustomerHandler;
import com.reactor.sample.dubbo.consumer.handler.HealthHandler;

import java.util.Locale;

public final class DubboConsumerModule implements RestApplication.Module {

    public static final DubboConsumerModule INSTANCE = new DubboConsumerModule();
    private static final String BASE_PACKAGE = "com.reactor.sample.dubbo.consumer";

    private DubboConsumerModule() {}

    @Override
    public void configure(RestApplication.ModuleContext context) {
        context.profile(ConsumerRuntimePlans.resolve());
        if (isCatalogOnlySurface()) {
            configureCatalogOnly(context);
            return;
        }
        context.scan(BASE_PACKAGE)
                .handlerTypes(HealthHandler.class, CatalogHandler.class, CustomerHandler.class);
    }

    private static void configureCatalogOnly(RestApplication.ModuleContext context) {
        RestApplication.disableRouteIndexValidationIfNotExplicit();
        CatalogOnlyDubboClientFactory.CatalogOnlyClient client =
                context.manage(CatalogOnlyDubboClientFactory.create());
        context.handlers(
                new HealthHandler(client.catalogClient()),
                new CatalogOnlyHandler(client.catalogClient()));
    }

    private static boolean isCatalogOnlySurface() {
        String surface = PropertiesLoader.get("sample.consumer.surface", "full")
                .trim()
                .toLowerCase(Locale.ROOT);
        return "catalog-only".equals(surface) || "catalog".equals(surface);
    }
}
