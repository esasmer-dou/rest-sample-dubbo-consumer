package com.reactor.sample.dubbo.consumer.directjson;

import com.reactor.rust.annotations.DirectQueryInt;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.dubbo.runtime.DubboStreamingResult;
import com.reactor.rust.dubbo.rest.DubboJsonResponses;
import com.reactor.rust.dubbo.sample.dto.StreamCatalogRow;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.json.JsonBodyProducer;
import com.reactor.rust.json.JsonBufferWriter;
import java.util.List;

@RestController("/api/v1/catalog")
public final class DirectCatalogHandler {
    private static final RawResponse HEALTH = RawResponse.json("{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final DirectCatalogClient client;
    private final boolean direct = PropertiesLoader.getBoolean("sample.direct-json.enabled", true);

    public DirectCatalogHandler(DirectCatalogClient client) { this.client = client; }

    @GetMapping("/health")
    public RawResponse health() { return HEALTH; }

    @GetMapping("/diagnostics")
    public RawResponse diagnostics() {
        long heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        return RawResponse.text("{\"row_constructions\":" + StreamCatalogRow.constructionCount()
                + ",\"detail_constructions\":" + com.reactor.rust.dubbo.sample.dto.StreamCatalogDetails.constructionCount()
                + ",\"heap_used\":" + heap + ",\"native\":"
                + com.reactor.rust.dubbo.runtime.NativeDubboBridge.metricsJson() + "}", "application/json; charset=utf-8");
    }

    @GetMapping("/rows")
    @DirectQueryInt(value = "count", defaultValue = 32, min = 0, max = 10000)
    public JsonBodyProducer rows(int count) {
        List<StreamCatalogRow> rows = client.catalog().read(count);
        if (direct) return DubboJsonResponses.body(rows);
        // This explicit comparison path materializes the ordinary typed List, then releases the wire lease.
        try { rows.size(); }
        finally { ((DubboStreamingResult<?>) rows).close(); }
        return (output, offset) -> {
            var json = JsonBufferWriter.reusable(output, offset).beginArray();
            for (int index = 0; index < rows.size(); index++) {
                if (index != 0) json.comma();
                var row = rows.get(index);
                json.beginObject().fieldLong("id", row.id()).comma().fieldString("name", row.name())
                        .comma().fieldDouble("amount", row.amount()).comma().fieldBoolean("active", row.active())
                        .comma().fieldName("details");
                var details = row.details();
                if (details == null) json.nullValue();
                else {
                    json.beginObject().fieldString("city", details.city()).comma().fieldName("tags").beginArray();
                    for (int tag = 0; tag < details.tags().size(); tag++) {
                        if (tag != 0) json.comma();
                        json.string(details.tags().get(tag));
                    }
                    json.endArray().comma().fieldString("note", details.note()).endObject();
                }
                json.endObject();
            }
            return json.endArray().result();
        };
    }
}
