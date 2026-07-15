package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.dubbo.sample.dto.CatalogItem;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonResponseSupportTest {

    @Test
    void generatedResponsesKeepWrapperShapeAndUtf8Text() {
        String catalog = body(JsonResponseSupport.catalogItems(List.of(
                new CatalogItem("sku-1", "Çağrı paketi", "service", 12.5, "TRY", true))));
        String customers = body(JsonResponseSupport.customerList(List.of(
                new CustomerSummary(1, "C-1", "İpek Şahin", "pilot", "i@example.com", "active", "now"))));

        assertTrue(catalog.contains("\"items\""));
        assertTrue(catalog.contains("Çağrı paketi"));
        assertTrue(customers.contains("\"customers\""));
        assertTrue(customers.contains("İpek Şahin"));
    }

    private static String body(com.reactor.rust.http.RawResponse response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }
}
