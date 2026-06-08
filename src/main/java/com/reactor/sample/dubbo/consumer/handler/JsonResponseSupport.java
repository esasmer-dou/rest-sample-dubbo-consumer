package com.reactor.sample.dubbo.consumer.handler;

import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import com.reactor.rust.http.RawResponse;

import java.util.List;
import java.util.Map;

final class JsonResponseSupport {

    private static final String JSON = "application/json; charset=utf-8";

    private JsonResponseSupport() {
    }

    static RawResponse stringField(String name, String value) {
        return json("{\"" + escape(name) + "\":\"" + escape(value) + "\"}");
    }

    static RawResponse intField(String name, int value) {
        return json("{\"" + escape(name) + "\":" + value + "}");
    }

    static RawResponse booleanField(String name, boolean value) {
        return json("{\"" + escape(name) + "\":" + value + "}");
    }

    static RawResponse catalogInfo(CatalogInfo value) {
        return json("""
                {
                  "id": "%s",
                  "title": "%s",
                  "ownerTeam": "%s",
                  "region": "%s",
                  "itemCount": %d,
                  "generatedAt": "%s"
                }
                """.formatted(
                escape(value.id()),
                escape(value.title()),
                escape(value.ownerTeam()),
                escape(value.region()),
                value.itemCount(),
                escape(value.generatedAt())));
    }

    static RawResponse catalogItems(List<CatalogItem> items) {
        StringBuilder json = new StringBuilder(64 + items.size() * 160);
        json.append("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            CatalogItem item = items.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("""
                    {"sku":"%s","name":"%s","category":"%s","amount":%s,"currency":"%s","active":%s}"""
                    .formatted(
                            escape(item.sku()),
                            escape(item.name()),
                            escape(item.category()),
                            Double.toString(item.amount()),
                            escape(item.currency()),
                            Boolean.toString(item.active())));
        }
        json.append("]}");
        return json(json.toString());
    }

    static RawResponse catalogAttributes(Map<String, String> attributes) {
        StringBuilder json = new StringBuilder(64 + attributes.size() * 64);
        json.append("{\"attributes\":{");
        int index = 0;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (index++ > 0) {
                json.append(',');
            }
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        json.append("}}");
        return json(json.toString());
    }

    static RawResponse customerSummary(CustomerSummary customer) {
        return json(customerJson(customer));
    }

    static RawResponse customerList(List<CustomerSummary> customers) {
        StringBuilder json = new StringBuilder(64 + customers.size() * 192);
        json.append("{\"customers\":[");
        for (int i = 0; i < customers.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(customerJson(customers.get(i)));
        }
        json.append("]}");
        return json(json.toString());
    }

    static RawResponse customerStats(CustomerStats stats) {
        return json("""
                {
                  "total": %d,
                  "active": %d,
                  "passive": %d,
                  "generatedAt": "%s"
                }
                """.formatted(
                stats.total(),
                stats.active(),
                stats.passive(),
                escape(stats.generatedAt())));
    }

    static RawResponse mutation(CustomerMutationResult result) {
        String customerId = result.customerId() == null ? "null" : result.customerId().toString();
        return json("""
                {
                  "operation": "%s",
                  "requestId": "%s",
                  "success": %s,
                  "customerId": %s,
                  "customerNo": "%s",
                  "fullName": "%s",
                  "segment": "%s",
                  "status": "%s",
                  "message": "%s",
                  "generatedAt": "%s"
                }
                """.formatted(
                escape(result.operation()),
                escape(result.requestId()),
                Boolean.toString(result.success()),
                customerId,
                escape(result.customerNo()),
                escape(result.fullName()),
                escape(result.segment()),
                escape(result.status()),
                escape(result.message()),
                escape(result.generatedAt())));
    }

    static RawResponse error(String code, String message) {
        return json("{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message) + "\"}");
    }

    private static String customerJson(CustomerSummary customer) {
        return """
                {"id":%d,"customerNo":"%s","fullName":"%s","segment":"%s","email":"%s","status":"%s","updatedAt":"%s"}"""
                .formatted(
                        customer.id(),
                        escape(customer.customerNo()),
                        escape(customer.fullName()),
                        escape(customer.segment()),
                        escape(customer.email()),
                        escape(customer.status()),
                        escape(customer.updatedAt()));
    }

    private static RawResponse json(String json) {
        return RawResponse.text(json, JSON);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
