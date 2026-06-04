# rest-sample-dubbo-consumer

English | [Turkish](README.tr.md)

Minimal Rust-Java REST sample application that consumes Dubbo providers through the lightweight
`java-rust-dubbo` adapter and exposes provider JSON responses as low-overhead REST endpoints.

This repository is intentionally small. It is meant to show how a `rust-java-rest` application can
become a Dubbo consumer without pulling Spring Boot, Dubbo Spring Boot starter, the official Dubbo
consumer stack, Netty, or ZooKeeper into the hot HTTP process by default.

## What This Sample Is For

Use this sample when you want to understand:

- How to add `java-rust-dubbo` to a Rust-Java REST application.
- How a Java REST handler can call a Dubbo provider.
- How provider JSON can be forwarded with `RawResponse` without building a Java DTO graph.
- How one REST process can consume more than one Dubbo interface without loading a full Dubbo stack.
- How to expose real GET/POST/PATCH/DELETE REST verbs over a minimal Dubbo consumer.
- How to keep the consumer process small with a static provider list.
- How to switch to ZooKeeper discovery only when you need it.
- How to structure handler, config, RPC adapter, and runtime classes without confusing them with DTOs.

This sample is not a full Dubbo governance platform. It does not try to demonstrate every Dubbo
feature. The goal is a minimum-overhead consumer path that fits the Rust-Java framework philosophy:
Java owns business logic, Rust owns HTTP I/O and selected low-level transport work.

## What `rust-java-rest` 3.2.1 Changes Here

This sample now targets `rust-java-rest` `3.2.1`. The application code model does not change:
handlers, service adapters, configuration classes, and business decisions still live in Java. The
change is mostly about the runtime path underneath those handlers.

| v3.2 change | What it means in this sample |
|------------|------------------------------|
| Lower-retention response pools | The consumer keeps fewer native response buffers when traffic is low. |
| Bounded in-flight response bytes | Large or slow responses cannot grow memory usage without a hard cap. |
| UTF-8 response/path/query fixes | Turkish characters are safe when request values and response bytes are UTF-8. |
| Raw/precomputed response path maturity | Provider JSON `byte[]` can be returned as `RawResponse.json(bytes)` without DTO parse/serialize work. |
| Startup component/route indexes | The sample can start without classpath scanning fallback; if an index is stale, startup fails early. |
| Route-level admission | Dubbo-backed routes have bounded in-flight limits before they can fill the global JNI queue. |
| Clearer low-RSS tuning | The sample properties use `micro-dubbo`: REST stays narrow and Dubbo uses native/static-provider settings. |
| Production artifact cleanup | The sample uses the normal framework dependency; framework demo/sample classes stay out of production-like RSS checks. |
| Explicit property override fix | Values you put in `rust-spring.properties` are no longer overwritten by runtime profile defaults. |

For this sample, the best path is still simple:

```text
Provider returns UTF-8 JSON byte[]
Consumer returns RawResponse.json(bytes)
Rust-Java runtime writes the HTTP response
```

Use native cache only when the response is intentionally cacheable and you have a clear key, TTL, and
invalidation rule. This sample does not enable response caching by default because provider responses
can be database-backed.

Use direct JSON writer only when the consumer itself builds a hot, fixed-shape JSON response. It is
not needed for the normal Dubbo forwarding path because the provider already returns ready JSON.

## Relationship With Other Projects

This repository depends on:

| Dependency | Why it is needed |
|------------|------------------|
| `rust-java-rest` | Provides the Rust Hyper HTTP server, Java handler model, DI, `RawResponse`, and runtime config. |
| `java-rust-dubbo` | Provides the lightweight Dubbo consumer adapter used by this sample. |
| `hessian-lite` | Needed only for argument-carrying Dubbo methods such as POST/PATCH/DELETE command examples. |
| `rest-sample-dubbo-provider` | Example Dubbo provider used for local end-to-end testing. |
| ZooKeeper | Optional. Needed only when running discovery mode instead of static provider mode. |

## Maven Profiles In This Sample

The sample has three consumer dependency shapes:

| Profile | What it uses | Best for | Limitation |
|---------|--------------|----------|------------|
| `full-dubbo-consumer` | Full `java-rust-dubbo` artifact plus `hessian-lite`. Active by default. | Running every sample endpoint, including POST/PATCH/DELETE command methods. | Larger classpath than the smallest read-only path. |
| `native-static-consumer` | `java-rust-dubbo` with the `native-static` classifier. No Hessian or ZooKeeper dependency. | Lowest classpath surface for static-provider, no-arg `byte[]` read endpoints. | Argument-carrying Dubbo methods need the full profile. |
| `zookeeper-discovery` | Full `java-rust-dubbo`, `hessian-lite`, and ZooKeeper client dependency. | Kubernetes or any environment where provider discovery must come from ZooKeeper. | Adds Java ZooKeeper classes and threads to the consumer process. |

Use the default/full profile when you want to copy and run all examples:

```powershell
mvn -q test
mvn -q exec:java
```

Use the native-static profile when you are testing the smallest read-only pass-through path:

```powershell
mvn -q -Pnative-static-consumer test
mvn -q -Pnative-static-consumer exec:java
```

With `native-static-consumer`, call the no-argument read endpoints such as `/api/v1/catalog/nested` and `/api/v1/catalog/db/customers`. The POST/PATCH/DELETE command examples carry method arguments, so they intentionally require the full profile with Hessian request encoding.

Use the ZooKeeper profile when the consumer must discover providers from ZooKeeper. This profile is self-contained; do not combine it with `full-dubbo-consumer`.

```powershell
$env:SAMPLE_DUBBO_DISCOVERY="zookeeper"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://zookeeper:2181"
mvn -q -Pzookeeper-discovery exec:java
```

The provider repository is here:

```text
git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
```

Default mode is static provider:

```text
consumer -> 127.0.0.1:20880
```

The sample keeps this path small with:

```properties
reactor.runtime.profile=micro-dubbo
reactor.dubbo.enabled=true
reactor.dubbo.transport=native
reactor.dubbo.runtime-profile=micro-dubbo
reactor.dubbo.providers=127.0.0.1:20880
reactor.dubbo.native-connections-per-endpoint=1
reactor.dubbo.native-async-workers=1
reactor.dubbo.native-async-queue-capacity=32
```

For small pods, pair those properties with OpenJ9 micro-RSS JVM options:

```bash
-Xms8m -Xmx48m -Xss256k -Xquickstart -Xtune:virtualized -Xshareclasses:none -XX:ActiveProcessorCount=1
```

Use `-Xnojit` only for very low traffic services where memory is more important than Java CPU
throughput. Do not use it as the default for RPC-heavy routes without a benchmark.

### Practical Pod Memory Starting Points

These values are not promises; they are safe starting limits for this exact sample shape. RSS changes
with JVM, container base image, CPU limit, traffic, response size, and whether ZooKeeper discovery is
enabled.

| Service shape | Start with | Why |
|---------------|-----------:|-----|
| Static provider, low traffic, JSON pass-through only | `128Mi` | Rust-Java REST stays small and the consumer avoids Java ZooKeeper/Dubbo Netty runtime. |
| Static provider with DB-backed Dubbo route under moderate load | `160Mi` | DB calls increase queue pressure and retained buffers during spikes. |
| ZooKeeper discovery enabled inside the consumer process | `160Mi` or more | Java ZooKeeper client adds threads/classes/session state. Use only when discovery is required. |
| Higher concurrency Dubbo workload | Measure from `192Mi` | Increase route admission and native connections together, then check p99, 503 rate, and RSS. |

If the service receives only occasional calls, do not size it from a c1000 benchmark. Start with the
static provider mode, keep route admission bounded, and verify idle RSS after a 30-60 second quiet
period.

ZooKeeper discovery mode:

```text
consumer -> ZooKeeper -> provider URL -> Dubbo provider
```

Use ZooKeeper mode only when discovery is required. Leaving `reactor.dubbo.providers` empty enables
it, but that also means the Java ZooKeeper client is loaded in the REST process.

## Route Admission For Dubbo Calls

The sample uses `@RouteAdmission` on Dubbo-backed routes:

```java
@GetMapping(value = "/nested", responseType = RawResponse.class)
@RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
    return catalogClient.nestedCatalogJsonAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

The same values are also present in `rust-spring.properties`, so you can tune them without changing
code:

```properties
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=100
reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=8
reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms=150
```

Use this feature when a route calls RPC, DB, or another dependency that can become slower than the
HTTP server. The goal is not to reject traffic aggressively; the goal is to prevent one slow route
from occupying every worker and increasing RSS through deep queues.

| Workload | Suggested first action |
|----------|------------------------|
| Low traffic, memory-first service | Keep the checked-in limits. |
| More valid 200 responses needed at c256 | Increase route `max-concurrent` first, then measure RSS and p99. |
| p99 grows while RPS is acceptable | Lower `queue-timeout-ms` or provider-side concurrency; do not only add workers. |
| DB route gets slow | Align consumer route limit with provider service limit and Hikari max pool. |

## Architecture

Runtime flow:

```text
HTTP client
  -> Rust Hyper server from rust-java-rest
  -> Java handler
  -> small interface-specific client adapter
  -> java-rust-dubbo native consumer
  -> selected Dubbo interface
  -> JSON byte[]
  -> RawResponse.json(...)
  -> Rust HTTP response
```

The important part is that the consumer does not parse the provider JSON into a DTO and serialize it
again. If the provider already returns JSON bytes, the consumer forwards those bytes as the HTTP body.

This sample now consumes three Dubbo interfaces:

| REST area | Dubbo interface | Method | Why it is separate |
|-----------|-----------------|--------|--------------------|
| Catalog read | `NestedCatalogService` | `getNestedCatalogJson()` | Catalog payload is independent from database-backed customer reads. |
| Customer read | `CustomerQueryService` | `getDatabaseCustomersJson()` | DB-backed customer query has its own lifecycle, repository, and scaling profile. |
| Customer write | `CustomerCommandService` | `createCustomer(...)`, `patchCustomer*`, `deleteCustomer(...)` | Write commands have different concurrency, idempotency, and DB mutation risks than reads. |

Keeping these as separate interfaces avoids a "god RPC interface". It also lets you tune, test, and
replace each provider contract independently.

## Package Structure

```text
com.reactor.sample.dubbo.consumer.app
  Application bootstrap and Rust HTTP server startup.

com.reactor.sample.dubbo.consumer.config
  Properties-only runtime config and Dubbo consumer bean wiring.

com.reactor.sample.dubbo.consumer.handler
  REST endpoint handlers.

com.reactor.sample.dubbo.consumer.dubbo
  Native Dubbo client adapter and metrics access.

com.reactor.rust.dubbo.sample
  Shared Dubbo interface examples. In production, move these to a shared API jar.
```

Main class:

```text
com.reactor.sample.dubbo.consumer.app.RestSampleDubboConsumerApplication
```

## DTO, Runtime Class, and RawResponse Decision

The Rust-Java framework standard is:

```text
HTTP JSON request/response DTO = Java record
Runtime behavior/resource owner = Java class
Already serialized JSON/RPC payload = RawResponse
```

Classes in this sample are not response DTOs:

| Type | Role | JSON DTO? |
|------|------|-----------|
| `RestSampleDubboConsumerApplication` | Starts the process and HTTP server. | No |
| `ConsumerProperties` | Reads and validates runtime properties. | No |
| `DubboConsumerConfiguration` | Creates and closes Dubbo client beans. | No |
| `NestedCatalogClient` | Adapter around native Dubbo method invokers. | No |
| `CustomerQueryClient` | Adapter around the customer Dubbo interface. | No |
| `CatalogHandler` | REST endpoint behavior. | No |
| `CustomerHandler` | Customer REST endpoint behavior. | No |
| `HealthHandler` | Health endpoint behavior. | No |
| `NestedCatalogService` | Dubbo interface contract. | Not an HTTP JSON DTO |
| `CustomerQueryService` | Second Dubbo interface contract. | Not an HTTP JSON DTO |

### Use Case: Normal REST DTO

If your endpoint creates its own JSON response, use a record:

```java
public record LocalStatusResponse(
        String status,
        String node,
        long activeCalls
) {}

@GetMapping(value = "/local/status", responseType = LocalStatusResponse.class)
public ResponseEntity<LocalStatusResponse> localStatus() {
    return ResponseEntity.ok(new LocalStatusResponse("UP", "consumer-1", 12));
}
```

### Use Case: Dubbo Provider Already Returns JSON

If the provider already returns JSON bytes, do not deserialize and serialize again:

```java
@GetMapping(value = "/catalog/raw", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> catalogRaw() {
    return catalogClient.nestedCatalogJsonAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

### Use Case: Dubbo Object Contract

If a provider returns domain objects instead of JSON bytes, put record DTOs in a shared API jar:

```java
public record CatalogItem(String sku, String name) {}

public record CatalogResponse(String source, List<CatalogItem> items) {}

public interface CatalogService {
    CatalogResponse getCatalog();
}
```

This is easier to read for normal business APIs, but it creates object graph and serialization cost on
the consumer side. For low-RSS, read-heavy JSON forwarding, `byte[] + RawResponse` is the better path.

### Does a Dubbo Method Return a Record Directly?

At the Java API level, yes:

```java
public interface CatalogService {
    CatalogResponse getCatalog();
}
```

The consumer can declare the method invoker with the record return type:

```java
DubboReferenceSpec<CatalogService> spec = DubboReferenceSpec.of(CatalogService.class);

NativeDubboMethodInvoker<CatalogResponse> invoker =
        client.method(spec, "getCatalog", CatalogResponse.class);

CompletableFuture<CatalogResponse> response = invoker.invokeAsync();
```

But the runtime path is not "zero-copy record transfer". The record is serialized by the provider and
deserialized again by the consumer:

```text
provider CatalogResponse record
  -> Hessian2 serialization
  -> Dubbo TCP frame
  -> Rust native transport
  -> response bytes
  -> Java Hessian2 decode
  -> new CatalogResponse record instance
  -> rust-java-rest JSON serialization if returned as HTTP JSON
```

This is correct when the consumer actually needs to inspect or transform the object. It is not the
lowest-overhead path when the consumer only forwards the provider response to an HTTP client.

Current adapter behavior:

| Dubbo method shape | Runtime path | Main overhead |
|--------------------|--------------|---------------|
| `byte[] method()` | Native fast path for no-arg byte-array calls. | Lowest allocation; Java receives ready bytes. |
| `record method()` | Java Hessian2 decode after Rust transport. | Record object graph allocation plus optional HTTP JSON serialization. |
| `record method(args...)` | Java Hessian2 encode and decode. | Request object allocation, response graph allocation, codec CPU. |
| `String method()` | Works as an object return path. | String allocation and possible charset/JSON escaping if wrapped later. |
| Large nested object graph | Avoid unless business logic needs it. | Heap pressure, GC, p99 latency, RSS growth. |

Hessian Lite 4.0.3 can serialize and deserialize simple Java 21 records in this project environment.
Still, production APIs must have a contract test with the real nested fields, collection types, enum
types, dates, null values, and version changes. Do not assume every future DTO shape is safe just
because a small record works.

### Use Case Decision Examples

Use `record` when the REST consumer applies business logic to the result:

```java
public record CatalogSummary(String source, int itemCount) {}

@GetMapping(value = "/catalog/summary", responseType = CatalogSummary.class)
public CompletableFuture<ResponseEntity<CatalogSummary>> summary() {
    return catalogRecordInvoker.invokeAsync()
            .thenApply(catalog -> ResponseEntity.ok(
                    new CatalogSummary(catalog.source(), catalog.items().size())));
}
```

Use `byte[] + RawResponse` when the provider already owns the response JSON:

```java
@GetMapping(value = "/catalog", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> catalog() {
    return catalogJsonInvoker.invokeAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

Use a normal Java class only for runtime behavior or resource ownership:

```java
public final class CatalogClient {
    private final NativeDubboMethodInvoker<byte[]> catalogJson;

    public CompletableFuture<byte[]> catalogJsonAsync() {
        return catalogJson.invokeAsync();
    }
}
```

Use streaming or a file/native response design for large exports. Pulling a large Dubbo object graph
into the consumer JVM and serializing it again is an anti-pattern for this framework.

## Dependencies

```xml
<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>rust-java-rest</artifactId>
    <version>3.2.1</version>
</dependency>

<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo</artifactId>
    <version>0.1.0-rc3</version>
</dependency>

<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>hessian-lite</artifactId>
    <version>4.0.3</version>
</dependency>
```

`hessian-lite` is needed by the POST/PATCH/DELETE command examples because those Dubbo methods carry
arguments. The no-argument read path can use the native byte-array fast path without Hessian request
encoding.

The sample contains the Dubbo interface sources only to keep the demo self-contained:

```java
package com.reactor.rust.dubbo.sample;

public interface NestedCatalogService {
    byte[] getNestedCatalogJson();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();
}
```

In a real system, publish these interfaces from a shared `*-api` jar used by both provider and
consumer. Do not copy/paste slightly different versions into separate services.

## GitHub Packages

If `rust-java-rest` and `java-rust-dubbo` are published only to GitHub Packages, Maven needs repository
and credential configuration.

`pom.xml` already contains:

```xml
<repositories>
    <repository>
        <id>github-rust-java-rest</id>
        <url>https://maven.pkg.github.com/esasmer-dou/rust-java-rest</url>
    </repository>
    <repository>
        <id>github-java-rust-dubbo</id>
        <url>https://maven.pkg.github.com/esasmer-dou/java-rust-dubbo</url>
    </repository>
</repositories>
```

Add credentials to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github-rust-java-rest</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
        <server>
            <id>github-java-rust-dubbo</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
    </servers>
</settings>
```

For private packages, the token needs `read:packages` and access to the private repositories.

## Configuration

Main config file:

```text
src/main/resources/rust-spring.properties
```

Read order:

```text
system property > environment variable > classpath properties
```

The sample does not keep runtime defaults in code. Missing or invalid properties fail fast during
startup. The checked-in values are intentionally low-RSS oriented; increase them only after measuring
throughput, p99 latency, 503 rate, and RSS together.

Important properties:

| Property | Purpose |
|----------|---------|
| `server.port` | HTTP port for the Rust-Java REST server. |
| `reactor.runtime.profile` | Runtime preset. This sample uses `micro-dubbo` for low RSS with native Dubbo enabled. |
| `reactor.startup.component-index.*` | Requires checked-in component indexes so startup does not silently fall back to broad classpath scanning. |
| `reactor.startup.route-index.*` | Validates checked-in route indexes and fails fast if route metadata is stale. |
| `reactor.rust.jni.workers` | Number of JNI worker threads. Kept small for low RSS. |
| `reactor.rust.http.max-response-body-bytes` | Per-response body size limit. |
| `reactor.rust.http.max-inflight-response-bytes` | Total in-flight response byte cap. |
| `reactor.rust.http.max-connections` | Upper bound for accepted HTTP connections. Keep bounded in Kubernetes. |
| `reactor.rust.response-pool.small-capacity` / `medium-capacity` / `large-capacity` | Native response buffer retention caps. Lower values reduce idle RSS; higher values reduce allocation churn under steady load. |
| `reactor.rust.json.writer-retain-max-bytes` | Maximum retained JSON writer buffer size. Keeps occasional larger responses from permanently increasing retained memory. |
| `reactor.rust.native-cache.max-bytes` | Hard cap for optional native response cache. Leave small or unused unless the endpoint is explicitly cacheable. |
| `reactor.rust.route-admission.*` | Per-route in-flight and queue timeout limits enforced before the global JNI queue. |
| `sample.dubbo.discovery` | `static` or `zookeeper`. |
| `reactor.dubbo.providers` | Static provider list, e.g. `127.0.0.1:20880`. |
| `reactor.dubbo.registry-address` | ZooKeeper address used in discovery mode. |
| `reactor.dubbo.timeout-ms` | Per-RPC timeout. |
| `reactor.dubbo.max-inflight` | Bounded RPC concurrency. |
| `reactor.dubbo.native-connections-per-endpoint` | Native Dubbo TCP connection pool size per provider. Keep it low for memory-first services; increase only with p99/RSS measurements. |

## Recommended Local Run Order

For the smallest consumer process, start with static provider mode:

```text
1. Start PostgreSQL if you want to test the DB-backed endpoint.
2. Start ZooKeeper because the provider sample registers itself there.
3. Start rest-sample-dubbo-provider.
4. Start this consumer with sample.dubbo.discovery=static.
5. Call the REST endpoints and check /api/v1/catalog/dubbo-metrics.
```

Static consumer mode does not load a Java ZooKeeper client inside the consumer. ZooKeeper is still
useful for the provider sample because it demonstrates provider registration and discovery mode.

## Quick Start: Static Provider Mode

Prerequisites:

- Java 21
- Maven 3.9+
- Docker Desktop or another local container runtime for the sample ZooKeeper/PostgreSQL services
- Access to GitHub Packages if the dependencies are private
- A running Dubbo provider on `127.0.0.1:20880`

Start local infrastructure. The consumer can use static provider mode, but the provider sample still
registers itself in ZooKeeper and warms up PostgreSQL:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
docker run -d --name rest-sample-postgres `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 postgres:16-alpine
```

Start the provider from the provider repository:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
cd rest-sample-dubbo-provider
mvn -q exec:java
```

Then start this consumer:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-consumer.git
cd rest-sample-dubbo-consumer
mvn -q test
mvn -q exec:java
```

This starts the default `full-dubbo-consumer` profile, so all GET/POST/PATCH/DELETE examples are available. If you only want the smallest static-provider read path, use:

```powershell
mvn -q -Pnative-static-consumer exec:java
```

Test:

```powershell
curl http://127.0.0.1:8080/app/health
curl http://127.0.0.1:8080/api/v1/catalog/nested
curl http://127.0.0.1:8080/api/v1/customers/db
curl http://127.0.0.1:8080/api/v1/catalog/db/customers
curl http://127.0.0.1:8080/api/v1/catalog/dubbo-metrics
curl -X POST http://127.0.0.1:8080/api/v1/customers -H "Content-Type: application/json" -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Zeynep Şahin\",\"segment\":\"pilot\",\"email\":\"zeynep.sahin@example.com\"}"
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment -H "Content-Type: application/json" -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status -H "Content-Type: application/json" -d "{\"requestId\":\"req-1003\",\"status\":\"passive\"}"
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/3 -H "Content-Type: application/json" -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

Expected result: all endpoints above should return HTTP `200`. The customer endpoints read from the
provider's PostgreSQL-backed service; if PostgreSQL is not running, those endpoints should return a
controlled `503` instead of hanging.

## Quick Start: ZooKeeper Discovery Mode

Start ZooKeeper:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
```

Start the provider. It registers itself under ZooKeeper:

```powershell
cd rest-sample-dubbo-provider
mvn -q exec:java
```

Start the consumer with the ZooKeeper profile:

```powershell
cd rest-sample-dubbo-consumer
$env:SAMPLE_DUBBO_DISCOVERY="zookeeper"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://127.0.0.1:2181"
mvn -q -Pzookeeper-discovery exec:java
```

In this mode, `reactor.dubbo.providers` is ignored and provider URLs are read from ZooKeeper.

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /app/health` | Consumer application health endpoint. |
| `GET /api/v1/catalog/nested` | Calls `NestedCatalogService` and forwards nested catalog JSON. |
| `GET /api/v1/customers/db` | Calls `CustomerQueryService` and forwards PostgreSQL-backed customer JSON. |
| `GET /api/v1/catalog/db/customers` | Compatibility alias that also calls `CustomerQueryService`. |
| `POST /api/v1/customers` | Creates or upserts a customer through `CustomerCommandService`. |
| `PATCH /api/v1/customers/{id}/segment` | Changes customer segment through a bounded DB command method. |
| `PATCH /api/v1/customers/{id}/status` | Changes customer lifecycle status through a bounded DB command method. |
| `DELETE /api/v1/customers/{id}` | Deletes a customer through a bounded DB command method. |
| `GET /api/v1/catalog/dubbo-metrics` | Shows native Dubbo client metrics. |

## Copy/Paste Use Case Cookbook

This section is intentionally practical. If you are performance-first, start from the property
tables. If you are looking for a coding pattern, copy the Java snippets and keep the same boundaries:
REST handler in Java, Dubbo adapter in Java, HTTP I/O in Rust, DB mutation in the provider.

| Real-world need | Use this pattern | Why |
|-----------------|------------------|-----|
| Product catalog, dashboard config, branch list | `GET` + provider returns JSON `byte[]` + `RawResponse.json(bytes)` | Lowest allocation path for read-heavy pass-through JSON. |
| Customer list from DB provider | `GET` + provider owns Hikari/SQL + consumer forwards bytes | REST process stays small; DB pool stays outside the Rust-Java HTTP process. |
| Create customer/order/payment command | `POST` + compact JSON command bytes | Write command reaches provider without building a large consumer DTO graph. |
| Partial update such as segment/status/address | `PATCH` + path id + command body | Small payload, clear command intent, bounded route admission. |
| Delete/deactivate request | `DELETE` + path id + optional reason body | Explicit destructive operation with audit-friendly reason/requestId. |
| Provider discovery required | ZooKeeper profile | Works, but adds Java ZooKeeper client overhead to the consumer process. |
| Lowest memory service | Static provider list + `micro-dubbo` | Avoids ZooKeeper client and official Dubbo runtime in the hot REST JVM. |

### Use Case 1: Read-Heavy Catalog Pass-Through

When the provider already knows how to build the JSON, do not parse it in the consumer.

```java
@GetMapping(value = "/nested", responseType = RawResponse.class)
@RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
    return catalogClient.nestedCatalogJsonAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

Properties to keep:

```properties
reactor.runtime.profile=micro-dubbo
reactor.dubbo.transport=native
reactor.dubbo.providers=127.0.0.1:20880
reactor.rust.native-cache.max-bytes=0
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16
```

Use native cache only when this catalog is intentionally cacheable and you have a TTL/invalidation
rule. Otherwise, keep it off as this sample does.

### Use Case 2: DB-Backed Customer Query

The provider owns PostgreSQL/Hikari. The consumer should not open its own JDBC pool just to expose a
REST endpoint.

```powershell
curl http://127.0.0.1:8080/api/v1/customers/db
```

Tune these values together:

```properties
# consumer
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=150

# provider
sample.db.maximum-pool-size=2
dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1
```

BEST: if p99 grows, first check DB pool wait and provider method limits. ANTI-PATTERN: increasing
consumer JNI workers while the provider DB pool is already saturated.

### Use Case 3: POST Create Customer

PowerShell:

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Zeynep Şahin\",\"segment\":\"pilot\",\"email\":\"zeynep.sahin@example.com\"}"
```

Consumer handler:

```java
@PostMapping(value = "", requestType = byte[].class, responseType = RawResponse.class)
@MaxRequestBodySize(32768)
@RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody byte[] body) {
    return customerCommandClient.createCustomerAsync(body)
            .thenApply(json -> ResponseEntity.created(RawResponse.json(json)));
}
```

Use `@RequestBody byte[]` for pass-through command JSON. Use record DTOs only when the consumer must
make a typed business decision before calling Dubbo.

### Use Case 4: PATCH Customer Segment

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
```

Use this for business classification changes, tenant moves, pricing segment changes, or pilot to
enterprise transitions.

### Use Case 5: PATCH Customer Status

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1003\",\"status\":\"passive\"}"
```

Use this for lifecycle changes such as `active`, `passive`, `blocked`, or `pending-review`. Keep the
payload small and prefer an explicit route over a generic "update everything" endpoint.

### Use Case 6: DELETE Customer

```powershell
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/3 `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

For production, prefer soft-delete/deactivate when audit or recovery matters. Hard delete is included
because users need a concrete `DELETE` verb example.

### Profile And Property Selection

| User problem | Recommended starting profile | Key properties |
|--------------|------------------------------|----------------|
| "I need the smallest pod for occasional traffic" | `micro-dubbo`, static provider | `reactor.rust.jni.workers=1`, `reactor.dubbo.native-async-workers=1`, `reactor.rust.response-pool.small-capacity=8` |
| "I need more successful writes at c256" | `micro-dubbo` plus route-specific tuning | Increase `reactor.rust.route-admission.post.api.v1.customers.max-concurrent`, then measure RSS/p99/503. |
| "I need dynamic provider discovery" | `micro-dubbo` + `zookeeper-discovery` Maven profile | Set `SAMPLE_DUBBO_DISCOVERY=zookeeper`; expect a small RSS increase. |
| "Provider DB is slow" | Keep consumer bounded, tune provider first | Check `sample.db.maximum-pool-size`, provider method limits, PostgreSQL latency. |
| "I need a best-practice code template" | Copy this sample structure | `handler` -> `dubbo client` -> `shared interface` -> `provider service` -> `repository`. |

Do not turn every problem into a bigger thread pool. For this framework, the safer production order is
usually: bounded route admission, explicit timeout, provider bulkhead, DB pool tuning, then worker
increase only after measurement.

## Why This Consumer Is Small

The minimum-overhead path is static-provider native transport:

- No Java ZooKeeper client in the hot JVM.
- No official Dubbo `ReferenceConfig` stack.
- No Netty client stack inside the consumer process.
- No provider JSON parsing and re-serialization.
- Bounded queue, timeout, and in-flight limits.

ZooKeeper discovery is available when needed, but static provider mode is the better starting point
for low RSS services, Kubernetes Service DNS, or sidecar-generated provider lists.

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Maven cannot download `com.reactor` artifacts | Verify GitHub Packages repositories and `~/.m2/settings.xml`. |
| `401` or `403` from GitHub Packages | Token needs `read:packages` and private repo access. |
| `Dubbo provider ... timed out` | Check provider is listening on `20880` and timeout is high enough. |
| ZooKeeper mode finds no provider | Verify provider registered under `/dubbo/{interface}/providers`. |
| High p99 at high concurrency | Increase `reactor.dubbo.max-inflight` and native connections carefully, or use a balanced profile. |

## Production Notes

- Keep DTOs as records for normal HTTP JSON contracts.
- Use `RawResponse` only when JSON is already serialized or intentionally precomputed.
- Keep timeouts and in-flight limits explicit.
- Do not hide runtime defaults in code.
- Move the shared Dubbo interfaces into a real API jar before using this pattern in production.
- Protect metrics endpoints in real deployments.
