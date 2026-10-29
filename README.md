# rest-sample-dubbo-consumer

[English](README.md) | [Türkçe](README.tr.md)

## Start With 0.8.0

The direct-json-consumer profile forwards eligible typed native results without consumer row DTOs. Default handlers and business validation stay in Java. Use normal typed results when rows must be changed.

[Usage guide](docs/functional-operations.md) · [Release notes](docs/RELEASE_NOTES_v0.8.0.md)


New usage guide: [New usage examples](docs/functional-operations.md).

Optional source profile: [Direct provider list to JSON](docs/DIRECT_JSON.md).

For a query-only DB provider, see the provider sample's
[native query profile](https://github.com/esasmer-dou/rest-sample-dubbo-provider#read-only-database-with-native-transport-source-checkout).
The consumer keeps the same six query methods and static provider address.
Use the [query-only readiness recipe](#readiness-contract-selection) below; the
default full readiness also requires the catalog service, which that provider does not export.
The current source also adds an optional JSON method for segment queries. After
updating both the provider and consumer contracts, set
`SAMPLE_CONSUMER_SEGMENT_NATIVE_JSON=true` (or
`-Dsample.consumer.segment-native-json=true`). The default is `false` for older
providers. Do not enable it before the matching provider is deployed.

The native-response option now calls `findCustomersBySegmentUtf8(long, byte[])`.
The old JSON-byte request method remains available. The HTTP query no longer
becomes a JSON request record just to be parsed again by the provider. The limit
and UTF-8 segment use the existing Rust `long, byte[]` Hessian encoder. Java keeps
the same validation, SQL and response mapping. Malformed UTF-8 and segments larger
than 4096 bytes are rejected, never truncated. No extra JNI capability is needed.
HessianLite remains available for the other typed methods in this full profile.

To combine the native-JSON response path with non-blocking transport, configure both options:

```yaml
env:
  - name: SAMPLE_CONSUMER_SEGMENT_NATIVE_JSON
    value: "true"
  - name: REACTOR_DUBBO_NATIVE_ASYNC_TRANSPORT
    value: "tokio-demux"
```

An async Java return type alone does not select the Rust transport. `blocking`
still waits on bounded RPC worker threads; `tokio-demux` waits for network replies
on the dedicated Dubbo Tokio runtime. It does not use the REST Hyper executor.
The native handle selects response ownership/copying, not network scheduling.
`NativeJsonAsync` alone does not enable `tokio-demux`; the transport setting also
applies to typed async calls independently of the segment native-JSON option.

`GET /api/v1/customers/db/by-segment?segment=standard&limit=5` keeps its URL and
response fields. Java still validates the request and runs the SQL on the provider.
The provider creates JSON once. Rust carries that response to HTTP with a native
handle. The consumer does not create a second customer list or serialize it again.
Every request still runs the SQL. This is not a response cache.
The existing typed RPC remains available. The JSON request is limited to 4096
bytes; SQL still bounds the result to 1–100 customers. No queue, admission limit,
retry count or thread pool is increased by this option.

That provider profile does not publish catalog or command services. A faster provider
can increase consumer work and memory under load. Check the consumer's own peak RSS
and admission counters; lower provider memory alone is not an end-to-end acceptance.

A REST application that calls Dubbo providers.

- Rust Hyper accepts HTTP requests.
- Java handlers keep the business flow.
- `java-rust-dubbo` performs the lightweight Dubbo client work.
- Providers can be found by a static address or ZooKeeper.
- The sample includes GET, POST, PATCH, and DELETE flows.

Current source versions: `rust-java-rest:4.8.0`, `java-rust-dubbo:0.9.0`, `rest-sample-utility:0.5.0`, `rust-sample-model:0.5.0`.

## Read This First

Choose one runtime surface before copying code. Most Kubernetes applications should start with a
static Service DNS address and the native client. Add ZooKeeper only when registry semantics are a
real requirement. Add the full consumer surface only when the application uses those contracts.

| Copy | Replace in your service |
| --- | --- |
| `@ReactorApplication` and constructor injection | Package name and application class |
| Generated `DubboClients` declaration | Your shared Dubbo interfaces and methods |
| Route workload and admission annotations | Limits measured for your provider and DB capacity |
| Readiness dependency check | Your required provider contracts |
| Static/ZooKeeper profile layout | Addresses, registry path, credentials, and Kubernetes limits |

The POM inherits `rust-java-platform-parent`. The normal surface uses the REST starter plus the
selected Dubbo profile. The smallest profile uses `rust-java-starter-dubbo`, which brings the
native-static client without the official Dubbo, Netty, ZooKeeper, or Hessian runtime. Code
generators remain build-only.

## Current Checkout

This update aligns the platform with REST `4.8.0` and optional agent `0.7.1`.
Use the native file packaged with the dependency you selected. Do not mix older SO/DLL files.
Business code, URLs and configuration keys stay the same. Telemetry remains disabled by default.
The HTTP micro memory gate does not guarantee the same overhead for a database job or a richer profile.

Version `0.8.0` uses platform parent `4.8.0`. Rebuild the application with the aligned
dependencies. See [release notes](docs/RELEASE_NOTES_v0.8.0.md). Runtime settings and URLs are unchanged.

- `RestSampleDubboConsumerApplication` uses declarative framework startup.
- One `DubboClients` declaration generates all typed clients and shares one bounded transport.
- Handwritten client definitions and duplicate runtime-plan classes are removed.
- Handlers use constructor injection and generated route invokers.
- Existing REST URLs, Dubbo interfaces, payloads, profiles, and Java business flow are unchanged.
- The HTTP runtime uses REST ABI `29` and Glowroot ABI `6` from the coordinated `4.8.0` platform line.

The optional Glowroot micro telemetry plane is available through REST `4.8.0`. It is disabled by
default. When enabled, HTTP route and native Dubbo timings are exported to the existing Glowroot
Central deployment without changing handlers, services, or Dubbo interfaces.

## Declarative Flow

| You write | Generated or managed for you | Runtime result |
| --- | --- | --- |
| Shared Dubbo interface | Typed client implementation and method plan | No dynamic proxy on the generated path |
| Client declaration | One shared bounded transport lifecycle | No client factory in each handler |
| REST handler and Java service | Constructor wiring and route invoker | Business logic remains Java |
| Startup condition | Conditional bean and route registration | Disabled surface adds no per-call branch |
| Route/RPC budgets | Admission and queue timeout | Controlled `503` instead of unbounded memory growth |

```mermaid
flowchart LR
    C["HTTP client"] --> H["Rust Hyper"]
    H --> J["Java handler and service"]
    J --> D["Generated Dubbo client"]
    D --> T["Bounded Rust transport"]
    T --> P["Dubbo provider"]
```

Start with the generated client. Use manual invoker wiring only for a deliberately unsupported
contract or framework development. For ready JSON passthrough, prefer the native response handle so
the provider body is not materialized as another Java `byte[]`.

## Start Here

Choose one consumer shape before reading any property.

| Need | Use |
|---|---|
| One ready-JSON catalog call and the fewest dependencies | Maven profile `native-static-consumer` |
| Catalog, customer reads, and customer commands | Default profile `full-dubbo-consumer` |
| Provider addresses come from ZooKeeper | Maven profile `zookeeper-discovery` |
| Export HTTP and Dubbo calls to Glowroot | [Glowroot Telemetry](#glowroot-telemetry) |

Most Kubernetes services can start with static Service DNS. ZooKeeper is not required when one stable Kubernetes Service exposes the provider replicas.

## Quick Start: Static Provider

This is the simplest local flow. ZooKeeper is not used.

### 1. Start the provider

Follow the quick start in
[`rest-sample-dubbo-provider`](https://github.com/esasmer-dou/rest-sample-dubbo-provider).

The provider must listen on `127.0.0.1:20880`.

### 2. Start the consumer

Run from this repository:

```powershell
$env:GITHUB_PACKAGES_TOKEN="YOUR_TOKEN_WITH_READ_PACKAGES"

mvn -q `
  "-Dserver.port=8080" `
  "-Dsample.dubbo.discovery=static" `
  "-Dreactor.dubbo.providers=127.0.0.1:20880" `
  "-Dreactor.runtime.profile=micro-dubbo" `
  clean compile exec:java
```

### 3. Call the API

```powershell
curl.exe http://127.0.0.1:8080/app/health
curl.exe http://127.0.0.1:8080/app/ready
curl.exe http://127.0.0.1:8080/api/v1/catalog/nested
curl.exe http://127.0.0.1:8080/api/v1/catalog/items?limit=3
curl.exe http://127.0.0.1:8080/api/v1/customers/db/1
```

Create a customer:

```powershell
curl.exe -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  --data '{"requestId":"req-1001","customerNo":"CUST-9001","fullName":"Ayse Yilmaz","segment":"pilot","email":"ayse@example.com"}'
```

Change the status:

```powershell
curl.exe -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  --data '{"requestId":"req-1002","status":"active"}'
```

Delete the customer:

```powershell
curl.exe -X DELETE http://127.0.0.1:8080/api/v1/customers/1 `
  -H "Content-Type: application/json" `
  --data '{"requestId":"req-1003","reason":"sample cleanup"}'
```

The ready-to-import Postman collection is under
[`artifacts/postman`](artifacts/postman/rest-sample-dubbo-consumer.postman_collection.json).

## Main Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /app/health` | Process liveness; does not call Dubbo |
| `GET /app/ready` | Checks required providers |
| `GET /api/v1/catalog/nested` | Ready JSON catalog response |
| `GET /api/v1/catalog/info` | Typed catalog response |
| `GET /api/v1/catalog/items?limit=3` | Typed list response |
| `GET /api/v1/customers/db/{id}` | One customer from the DB-backed provider |
| `GET /api/v1/customers/db/by-segment?...` | Filtered customer list |
| `POST /api/v1/customers` | Low-overhead JSON command |
| `POST /api/v1/customers/typed` | Typed record command |
| `PATCH /api/v1/customers/{id}/segment` | Changes customer segment |
| `PATCH /api/v1/customers/{id}/status` | Changes customer status |
| `DELETE /api/v1/customers/{id}` | Deletes a customer |

## Static Address or ZooKeeper?

### Static address

Use this when Kubernetes Service DNS already gives one stable provider address.

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=rest-sample-dubbo-provider:20880
```

Kubernetes distributes TCP connections across the provider pods behind the Service. The consumer does not need ZooKeeper for this shape.

The distribution happens when a TCP connection is opened. Requests on an existing Dubbo connection keep using the same provider pod.

### ZooKeeper

Use ZooKeeper when providers register dynamically, interfaces live in different registries, or Dubbo discovery behavior is required.

Build:

```powershell
mvn -q -Pzookeeper-discovery clean package
```

Run:

```properties
sample.dubbo.discovery=zookeeper
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

ZooKeeper adds client classes, threads, and memory. Use it only when it solves a real discovery requirement.

## Runtime Size

| Traffic shape | Starting values | Meaning |
|---|---|---|
| Very small service | connections `1`, workers `1`, queue `32`, max-inflight `16` | Lowest memory; overload fails fast |
| Small production service | connections `2`, workers `2`, queue `64`, max-inflight `64` | Sample defaults; more concurrent RPC work |
| Measured high traffic | `reactor.runtime.profile=balanced-dubbo` plus measured pool values | More headroom; higher process memory |

Use exact properties instead of a hidden preset:

```properties
reactor.dubbo.native-connections-per-endpoint=2
reactor.dubbo.native-async-workers=2
reactor.dubbo.native-async-queue-capacity=64
reactor.dubbo.max-inflight=64
```

These are the sample defaults. Change them only after provider, database pool, p99, `503`, and RSS
measurements show a reason. Move to `balanced-dubbo` only when the downstream capacity can use it.

Do not solve latency by increasing every queue. A larger queue uses more memory and can slow the worst requests.

## Small Linux/OpenJ9 Pods

For a small Linux/OpenJ9 pod, you can test `-XX:-TransparentHugePage` as an optional
setting. It is not enabled by default in these sample images. It tells OpenJ9
not to request transparent huge pages. It can reduce unused resident pages in a
small heap or JIT workspace. It does not disable JIT, lower the heap limit, or
change the host's global Linux settings. Some native memory can still use huge pages.

If you set `JAVA_TOOL_OPTIONS` in Kubernetes, it replaces the image's value.
Keep the complete options, not just the new flag:

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: >-
      -Xms24m -Xmx96m -Xss192k -Xgc:threads=1
      -XX:ActiveProcessorCount=1 -Xtune:virtualized -XX:-TransparentHugePage
```

This is an OpenJ9 recipe, not a HotSpot recipe. Compare RSS, successful requests
per second, p99 and errors on your payloads before rollout. This change does not
switch `blocking` to `tokio-demux` or change RPC, Hikari, or route limits.

### When Memory Peaks During JVM Compilation

`-XX:ActiveProcessorCount=1` is not a guarantee that OpenJ9 creates only one JIT
compiler thread. Inspect a javacore before increasing the heap or reducing RPC
capacity. The JVM heap and the compiler's native workspace are different budgets.

For a measured, one-CPU OpenJ9 service, these are explicit optional JVM additions:

```text
-XcompilationThreads1 -Xjit:scratchSpaceLimit=16384
```

| Option | What It Controls | What To Check |
|---|---|---|
| `-XcompilationThreads1` | One JIT compiler worker; JIT remains enabled | Startup, warmup and request p99 |
| `-Xjit:scratchSpaceLimit=16384` | 16 MiB scratch limit per compilation; not total JVM RSS | Large Java methods and steady useful RPS |
| `-XX:-TransparentHugePage` | Stops OpenJ9 requesting THP when the host uses `madvise` | Actual `AnonHugePages` in `smaps` |

These flags are not sample-image defaults and do not establish an overall memory
limit. A smaller compilation workspace can change optimization decisions. Check
the complete workload before adoption. On hosts configured with THP `always` or
`never`, the OpenJ9 THP flag is ignored; do not change the shared node to tune one pod.
See [OpenJ9 compiler threads](https://eclipse.dev/openj9/docs/xcompilationthreads/)
and [OpenJ9 THP behavior](https://eclipse.dev/openj9/docs/xxtransparenthugepage/).

## JSON and DTO Choice

| Need | Choose | Cost |
|---|---|---|
| Pass provider JSON directly to HTTP | `byte[]` plus Rust response handle | The body is not copied back into Java |
| Validate request fields in Java | Java `record` request | Clear contract; normal parsing cost |
| Make a business decision from provider fields | Typed `record` result | Hessian decode and Java object creation |
| Return a large list without inspecting it | Ready JSON bytes | Avoids a large Java object graph |

Use typed records for business logic. Use ready JSON bytes for measured pass-through endpoints.

## Readiness Contract Selection

**UNRELEASED, post-0.8.0 checkout change:** `sample.consumer.readiness.contracts`,
`sample.consumer.catalog.enabled` and the query-only overlay were added after the
published `0.8.0` release. An existing downloaded `0.8.0` jar does not support them.
Rebuild this checkout for the recipe below; its jar still has the `0.8.0` filename.
See the [unreleased change notes](docs/RELEASE_NOTES_UNRELEASED.md).

For the normal `full-dubbo-consumer` build, selection is resolved once at startup.
It does not parse configuration on requests or wait synchronously for RPC replies.

| `sample.consumer.readiness.contracts` | Required readiness dependencies |
|---|---|
| `auto` (default) | Catalog and customers for `surface=full`; catalog for `surface=catalog-only` |
| `full` | Catalog and customers |
| `catalog-only` | Catalog |
| `query-only` | Customers |

`GET /app/ready` returns `503` if a required client is absent or its RPC fails.
Unselected checks report `SKIPPED` with `required=false` and make no RPC.
`GET /app/health` remains local liveness; readiness never invokes a command.
Unknown contract selections fail startup rather than silently dropping checks.

For `native-db-query-provider`, build the normal consumer and use the standalone
[query-only overlay](src/main/resources/config/query-only.properties) instead of
`config/production.properties`. From the consumer checkout:

```powershell
mvn -B -ntp clean package -Pfull-dubbo-consumer
java `
  "-Dreactor.config.file=src/main/resources/config/query-only.properties" `
  "-Dreactor.dubbo.providers=127.0.0.1:20880" `
  -jar target/rest-sample-dubbo-consumer-0.8.0.jar
```

The overlay keeps `sample.consumer.surface=full`, selects
`sample.consumer.readiness.contracts=query-only` and sets
`sample.consumer.catalog.enabled=false`. The existing generated startup condition
then omits the catalog client and catalog routes. Query and command share the
customer handler, so the command client/routes remain for API compatibility.
Only query routes are supported against this provider: this selection is **not**
a read-only authorization policy. Restrict exposed routes separately if needed.
Do not use `surface=query-only`, which is not a supported surface value.

In Kubernetes, mount the overlay and set
`REACTOR_CONFIG_FILE=/app/config/query-only.properties`; set
`REACTOR_DUBBO_PROVIDERS` to the query provider's Service DNS and port.
Change these startup settings by restarting the consumer, not per request.
The `native-static-consumer` and `direct-json-consumer` builds have separate
catalog-only entrypoints; this customer-query recipe does not apply to them.

## Configuration

The application reads configuration in this order:

1. `src/main/resources/rust-spring.properties`
2. Files passed through `reactor.config.file` or `REACTOR_CONFIG_FILE`
3. JVM `-D...` values and supported environment variables

| File | Purpose |
|---|---|
| `rust-spring.properties` | Small local defaults |
| `config/production.properties` | Production timeouts, pools, and connection limits |
| `config/query-only.properties` | Standalone production-sized overlay for the native DB query provider |
| `config/advanced-tuning.properties` | Route budgets and low-level memory tuning |

Important starting properties:

| Property | Purpose |
|---|---|
| `sample.dubbo.discovery` | Selects `static` or `zookeeper` |
| `reactor.dubbo.providers` | Static provider addresses |
| `reactor.dubbo.timeout-ms` | Maximum RPC wait |
| `reactor.dubbo.max-inflight` | Total concurrent RPC limit |
| `reactor.dubbo.native-connections-per-endpoint` | TCP connections per provider address |
| `sample.consumer.readiness.contracts` | Required dependencies, selected once at startup |
| `sample.consumer.catalog.enabled` | Startup gate for the generated catalog client and routes |
| `sample.command.customer-key-admission.max-concurrent-per-key` | Per-customer concurrency cap in this consumer JVM only |

Keyed admission is not a replica-wide lock or an idempotency guarantee. Provider
method caps are also process-local, not a shared per-customer lock across methods
or replicas. PostgreSQL transactions, constraints and row locks remain responsible
for database consistency. The optional shared-DB idempotency example applies only
to the typed status command with its key/customer scope, not every write. See the
[error and retry contracts](docs/functional-operations.md).

## Timeout Contracts

For generated/native clients in the normal full consumer, these are separate budgets:

```properties
reactor.dubbo.timeout-ms=1000
reactor.rust.async.response-timeout-ms=1500
```

The first bounds RPC waiting; the second bounds the REST async response. This
plain-library sample has no demonstrated per-method timeout annotation/configuration
contract. Do not copy Spring-only method timeout examples into these clients.
The direct-JSON profile instead builds its client with the client-wide
`sample.direct-json.timeout-ms` option (default `3000`); see its
[profile guide](docs/DIRECT_JSON.md). Provider request/drain timeouts, DB pool waits
and SQL statement timeouts are separate limits, not caller method deadlines.
A caller timeout does not prove that Java business work stopped or a write rolled
back. Do not blindly retry a timed-out write; use the documented idempotency contract.

## Kubernetes Example

Static Service DNS:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "2"
  - name: REACTOR_DUBBO_NATIVE_ASYNC_WORKERS
    value: "2"
  - name: REACTOR_DUBBO_NATIVE_ASYNC_QUEUE_CAPACITY
    value: "64"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "64"
```

ZooKeeper discovery:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
```

## Glowroot Telemetry

This consumer runs on Rust-Java REST. It does not need a separate Spring starter. The agent is
disabled by default. When enabled, HTTP route timings and native Dubbo count, duration, and error
aggregates stay in the same Rust runtime. Handlers, services, and Dubbo interfaces do not change.

This sample already uses production platform `4.8.0` with Glowroot ABI `6`. The optional
`java-rust-glowroot-agent:0.7.1` JAR is needed only when you want `-javaagent` syntax; the embedded
REST telemetry runtime does not require a separate agent dependency:

```xml
<parent>
  <groupId>com.reactor</groupId>
  <artifactId>rust-java-platform-parent</artifactId>
  <version>4.8.0</version>
  <relativePath/>
</parent>
```

Add these values to `rust-spring.properties` for local use:

```properties
reactor.glowroot.enabled=true
reactor.glowroot.profile=micro
reactor.glowroot.collector.address=http://127.0.0.1:8181
reactor.glowroot.agent.id=dubbo-consumer-local
reactor.glowroot.application.name=rest-sample-dubbo-consumer
reactor.glowroot.http.sample-rate=256
reactor.glowroot.trace.capacity=0
```

If you explicitly set `reactor.native.capabilities`, include every required surface:

```properties
reactor.native.capabilities=http,dubbo,glowroot
```

Add these environment variables to a Kubernetes deployment:

```yaml
env:
  - name: REACTOR_GLOWROOT_ENABLED
    value: "true"
  - name: REACTOR_GLOWROOT_PROFILE
    value: "micro"
  - name: REACTOR_GLOWROOT_COLLECTOR_ADDRESS
    value: "http://glowroot-collector.observability.svc.cluster.local:8181"
  - name: REACTOR_GLOWROOT_AGENT_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
  - name: REACTOR_GLOWROOT_APPLICATION_NAME
    value: "rest-sample-dubbo-consumer"
```

Check the local agent state:

```powershell
curl.exe http://127.0.0.1:8080/diagnostics/glowroot
```

| Need | Profile | Use |
|---|---|---|
| Normal production traffic | `micro` | HTTP, Dubbo, RSS, thread, and exporter health |
| Heap or GC investigation | `jvm` | Enable temporarily on one pod |
| Explicit SQL in the consumer | `sql` | Use only when this consumer really uses JDBC |
| JVM, SQL, and error investigation | `full` | Use for a short incident window |
| Thread or heap output | `diagnostic` | Use only for an authorized operation |

The `micro` production gate allows at most one exporter thread and a `3 MiB` resident-memory
boundary. A collector outage does not stop HTTP or Dubbo calls. The Dubbo timing is the native call
as observed by the consumer; it does not separately measure database time inside the provider. Do
not expose `/diagnostics/glowroot` through a public ingress.

Keep the default when the agent is not required:

```properties
reactor.glowroot.enabled=false
```

See the
[`java-rust-glowroot-agent`](https://github.com/esasmer-dou/java-rust-glowroot-agent/blob/master/README.md)
guide for profile switching, the SQL API, and the full property reference.

## Code Map

| File | Why it matters |
|---|---|
| `RestSampleDubboConsumerApplication.java` | Starts the application with one declarative `RestApplication.run(...)` call |
| `DubboClients.java` | Declares generated clients, startup conditions, and one shared transport lifecycle |
| `ConsumerConfiguration.java` | Creates customer-key admission only when the full surface is enabled |
| `ConsumerReadinessContracts.java` / `ConsumerReadinessConfiguration.java` | Select immutable required readiness contracts at startup |
| `CatalogHandler.java` | Catalog GET examples |
| `CustomerHandler.java` | Conditional GET, POST, PATCH, and DELETE examples for the full surface |
| `NativeStaticConsumerApplication.java` | Entry point for the physically smaller native-static Maven profile |
| `rust-spring.properties` | Local settings |

The normal full surface uses `@ReactorApplication`, constructor injection, generated route invokers,
and `@EnableNativeDubboClients`. It does not create clients, handlers, factories, or modules by hand.
`sample.consumer.surface=catalog-only` disables customer components once at startup. Their routes are
not registered and strict AOT route validation remains valid. The `native-static-consumer` Maven
profile goes further: customer source files and the full runtime are not packaged at all.

## Maven Package Access

GitHub Packages requires a token with `read:packages`. The token also needs access to the private shared sample repositories.

The server IDs in `~/.m2/settings.xml` must match the POM:

```xml
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
  <server>
    <id>github-rest-sample-utility</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-rust-sample-model</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

## Common Problems

| Symptom | Check |
|---|---|
| Maven returns `401` | Token, private repo access, and all four server IDs |
| `/app/health` is `UP`, but `/app/ready` is `DOWN` | Provider address, provider process, registry, and network |
| Connection refused | Provider host, port `20880`, and container/Kubernetes DNS |
| Typed DTO class is unknown | Shared model version and Hessian allowlist |
| Requests return controlled `503` | Route or RPC limit is protecting the pod; inspect provider and DB capacity before increasing it |
| Turkish characters are broken | Send and return UTF-8 with `application/json; charset=utf-8` |
| Glowroot data is missing | Check `enabled`, collector address, agent id, and `/diagnostics/glowroot` |

## Production Checklist

- Use the smallest Maven profile and component surface that exports the required routes.
- Prefer Kubernetes Service DNS; enable ZooKeeper only for an explicit discovery requirement.
- Keep retries at `0` for non-idempotent commands.
- Bound HTTP route admission, RPC max in-flight, timeout, queue, and connections together.
- Use native response handles only when Java does not need to inspect or transform provider JSON.
- Keep liveness local; include required provider contracts in readiness with a short timeout.
- Test provider restart, DNS endpoint change, c64/c256 mixed load, p99, `503`, RSS, and final idle.
- Never expose raw provider exception text to the HTTP client.
- Start agent use with `micro`; do not leave `full` or `diagnostic` enabled continuously.

## Glossary

| Term | Meaning |
| --- | --- |
| Consumer | Application that calls a Dubbo provider |
| Static discovery | Provider address comes from configuration or Service DNS |
| Registry discovery | Provider addresses are watched through ZooKeeper |
| Native handle | Provider body remains in Rust; Java carries only a response id |
| Route admission | HTTP endpoint concurrency and short queue boundary |
| RPC bulkhead | Dubbo call concurrency boundary that protects the process |
| Telemetry profile | The bounded data and resource surface currently enabled in the agent |

## More Detail

- [Turkish user guide](docs/USER_GUIDE.tr.md)
- [Turkish PDF guide](docs/rest-sample-dubbo-consumer-user-guide.tr.pdf)
- [Docker image guide](docker/images/README.md)
- [Production settings](src/main/resources/config/production.properties)
- [Advanced tuning](src/main/resources/config/advanced-tuning.properties)
- [v0.8.0 release notes](docs/RELEASE_NOTES_v0.8.0.md)
