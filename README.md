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
- How to keep the consumer process small with a static provider list.
- How to switch to ZooKeeper discovery only when you need it.
- How to structure handler, config, RPC adapter, and runtime classes without confusing them with DTOs.

This sample is not a full Dubbo governance platform. It does not try to demonstrate every Dubbo
feature. The goal is a minimum-overhead consumer path that fits the Rust-Java framework philosophy:
Java owns business logic, Rust owns HTTP I/O and selected low-level transport work.

## Relationship With Other Projects

This repository depends on:

| Dependency | Why it is needed |
|------------|------------------|
| `rust-java-rest` | Provides the Rust Hyper HTTP server, Java handler model, DI, `RawResponse`, and runtime config. |
| `java-rust-dubbo` | Provides the lightweight Dubbo consumer adapter used by this sample. |
| `rest-sample-dubbo-provider` | Example Dubbo provider used for local end-to-end testing. |
| ZooKeeper | Optional. Needed only when running discovery mode instead of static provider mode. |

The provider repository is here:

```text
git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
```

Default mode is static provider:

```text
consumer -> 127.0.0.1:20880
```

ZooKeeper discovery mode:

```text
consumer -> ZooKeeper -> provider URL -> Dubbo provider
```

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

This sample now consumes two Dubbo interfaces:

| REST area | Dubbo interface | Method | Why it is separate |
|-----------|-----------------|--------|--------------------|
| Catalog read | `NestedCatalogService` | `getNestedCatalogJson()` | Catalog payload is independent from database-backed customer reads. |
| Customer read | `CustomerQueryService` | `getDatabaseCustomersJson()` | DB-backed customer query has its own lifecycle, repository, and scaling profile. |

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
    <version>3.1.0-rc4</version>
</dependency>

<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo</artifactId>
    <version>0.1.0-rc2</version>
</dependency>
```

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
startup.

Important properties:

| Property | Purpose |
|----------|---------|
| `server.port` | HTTP port for the Rust-Java REST server. |
| `reactor.rust.jni.workers` | Number of JNI worker threads. Kept small for low RSS. |
| `reactor.rust.http.max-response-body-bytes` | Per-response body size limit. |
| `reactor.rust.http.max-inflight-response-bytes` | Total in-flight response byte cap. |
| `sample.dubbo.discovery` | `static` or `zookeeper`. |
| `reactor.dubbo.providers` | Static provider list, e.g. `127.0.0.1:20880`. |
| `reactor.dubbo.registry-address` | ZooKeeper address used in discovery mode. |
| `reactor.dubbo.timeout-ms` | Per-RPC timeout. |
| `reactor.dubbo.max-inflight` | Bounded RPC concurrency. |
| `reactor.dubbo.native-connections-per-endpoint` | Native Dubbo TCP connection pool size per provider. |

## Quick Start: Static Provider Mode

Prerequisites:

- Java 21
- Maven 3.9+
- Access to GitHub Packages if the dependencies are private
- A running Dubbo provider on `127.0.0.1:20880`

Start the provider from the provider repository first:

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

Test:

```powershell
curl http://127.0.0.1:8080/app/health
curl http://127.0.0.1:8080/api/v1/catalog/nested
curl http://127.0.0.1:8080/api/v1/customers/db
curl http://127.0.0.1:8080/api/v1/catalog/db/customers
curl http://127.0.0.1:8080/api/v1/catalog/dubbo-metrics
```

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

Start the consumer with the optional ZooKeeper profile:

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
| `GET /api/v1/catalog/dubbo-metrics` | Shows native Dubbo client metrics. |

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
