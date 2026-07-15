package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.dubbo.sample.dto.CatalogAttributesResponse;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;
import com.reactor.rust.dubbo.sample.dto.CatalogItemsResponse;
import com.reactor.rust.dubbo.sample.dto.CustomerListResponse;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import com.reactor.rust.http.JsonResponses;
import com.reactor.rust.http.RawResponse;

import java.util.List;
import java.util.Map;

final class JsonResponseSupport {

    private JsonResponseSupport() {}

    static RawResponse stringField(String name, String value) {
        return JsonResponses.stringField(name, value);
    }

    static RawResponse intField(String name, int value) {
        return JsonResponses.longField(name, value);
    }

    static RawResponse booleanField(String name, boolean value) {
        return JsonResponses.booleanField(name, value);
    }

    static RawResponse catalogInfo(CatalogInfo value) {
        return JsonResponses.body(value);
    }

    static RawResponse catalogItems(List<CatalogItem> items) {
        return JsonResponses.body(new CatalogItemsResponse(items));
    }

    static RawResponse catalogAttributes(Map<String, String> attributes) {
        return JsonResponses.body(new CatalogAttributesResponse(attributes));
    }

    static RawResponse customerSummary(CustomerSummary customer) {
        return JsonResponses.body(customer);
    }

    static RawResponse customerList(List<CustomerSummary> customers) {
        return JsonResponses.body(new CustomerListResponse(customers));
    }

    static RawResponse customerStats(CustomerStats stats) {
        return JsonResponses.body(stats);
    }

    static RawResponse mutation(CustomerMutationResult result) {
        return JsonResponses.body(result);
    }

    static RawResponse error(String code, String message) {
        return JsonResponses.error(code, message);
    }
}
