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

## Start Here: Pick Your Consumer Shape

Most users should not start by reading every property. Start from the shape of your service, copy the
closest profile, then tune only the few values that affect your traffic.

| Scenario | Profile | Path / Gain | Trade-off |
|----------|---------|-------------|-----------|
| Local all verbs | Maven: default `full-dubbo-consumer`<br>Runtime: `micro-dubbo` | `RawResponse.json(bytes)`<br>GET/POST/PATCH/DELETE work immediately | Larger classpath than read-only |
| Lowest-memory read-only | Maven: `native-static-consumer`<br>Runtime: `micro-dubbo` | No-arg Dubbo `byte[]` JSON<br>No ZooKeeper/Hessian classes | Only no-arg reads |
| K8s Service DNS static | Default or `native-static-consumer`<br>Runtime: `micro-dubbo` | Provider address is K8s Service DNS<br>No ZooKeeper client/thread | No registry/governance |
| K8s ZooKeeper required | Maven: `zookeeper-discovery`<br>Runtime: `micro-dubbo` | Provider URL comes from ZooKeeper<br>Follows restart/re-register | ZooKeeper threads/classes |
| Read-heavy catalog/lookup | Default or `zookeeper-discovery`<br>`micro-dubbo` | Provider returns ready JSON<br>No DTO graph or reserialization | Provider owns JSON shape |
| Typed Dubbo DTO | Default or `zookeeper-discovery`<br>`micro-dubbo` | Shows `record/list/map/scalar` contracts | Hessian + object allocation |
| Write/command API | Default or `zookeeper-discovery`<br>`micro-dubbo` | `byte[]` body goes to provider command<br>Thin REST handler | Hessian request encoding |
| Higher concurrency | Default or `zookeeper-discovery`<br>Measure toward balanced | Same API, larger route budgets<br>Fewer overload rejects | Higher RSS/provider pressure |

Recommended starting point: if your provider is already behind a stable Kubernetes `Service` DNS and
you do not need Dubbo registry/governance, start with static provider mode. It is smaller and easier
to operate. If your deployment contract says provider discovery must come from ZooKeeper, build and
run with `zookeeper-discovery`; runtime properties alone cannot add ZooKeeper classes to an image
that was built without them.

## Scenario-Specific Jlink Images

Do not ship one image that supports every possible Dubbo shape. Build the smallest image for the
scenario you actually run.

| Image | Build command | Use when | Intentionally not supported | Local smoke evidence |
|-------|---------------|----------|-----------------------------|----------------------|
| `rest-sample-dubbo-consumer:native-static-jlink` | `docker build -f rest-sample-dubbo-consumer/Dockerfile.jlink.native-static.workspace -t rest-sample-dubbo-consumer:native-static-jlink .` | Static provider address, no ZooKeeper, no-arg raw JSON read such as `GET /api/v1/catalog/nested`. | Typed DTO routes, request-argument Dubbo methods, ZooKeeper discovery. | JRE `61M`, app jar `72K`, RSS about `33 MiB` after idle in Docker Desktop smoke. |
| `rest-sample-dubbo-consumer:full-static-jlink` | `docker build -f rest-sample-dubbo-consumer/Dockerfile.jlink.full-static.workspace -t rest-sample-dubbo-consumer:full-static-jlink .` | Static provider address plus typed `record/list/map/scalar` examples and POST/PATCH/DELETE command examples. | ZooKeeper discovery. | JRE `80M`, RSS about `39 MiB` after idle in Docker Desktop smoke. |
| `rest-sample-dubbo-consumer:zookeeper-jlink` | `docker build -f rest-sample-dubbo-consumer/Dockerfile.jlink.zookeeper.workspace -t rest-sample-dubbo-consumer:zookeeper-jlink .` | Provider discovery must come from ZooKeeper. | Lowest-memory static-only mode. | JRE `80M`, RSS about `42-51 MiB` depending on warm state in Docker Desktop smoke. |

Selection rule:

- **BEST:** `native-static-jlink` for read-only JSON pass-through behind a stable provider Service.
- **ACCEPTABLE:** `full-static-jlink` when the same consumer must also show typed DTO and command examples.
- **USE ONLY WHEN REQUIRED:** `zookeeper-jlink` when registry/discovery is a production requirement.
- **ANTI-PATTERN:** using `zookeeper-jlink` for a simple K8s Service DNS setup only because the app is in Kubernetes.

The native-static image uses the raw-only `CatalogJsonService` contract. That is why
`/api/v1/catalog/nested` works and typed endpoints such as `/api/v1/catalog/info` return `404` in
that image. This is deliberate: unsupported routes should not keep classes, serializers, and JDK
modules alive.

## Postman Collection

Import this collection when you want to try the consumer endpoints without writing curl commands:

```text
artifacts/postman/rest-sample-dubbo-consumer.postman_collection.json
```

The collection uses `baseUrl=http://localhost:8080` by default and includes health, catalog reads,
customer reads, and POST/PATCH/DELETE command examples. Start `rest-sample-dubbo-provider` first,
then start this consumer.

## Typed DTO Checklist

Typed Dubbo examples such as `POST /api/v1/customers/typed`, `GET /api/v1/customers/db/stats`,
and `GET /api/v1/customers/db/by-segment` intentionally use Java records. This is useful when the
consumer must validate fields or branch on a typed result, but it needs two explicit registrations:

| File / setup | Why it exists | If it is missing |
|--------------|---------------|------------------|
| DTO records annotated with `@CompiledJson` | Generates DSL-JSON readers/writers at build time for REST request parsing. | `@RequestBody CreateCustomerCommand` fails with "Unable to find reader". |
| `META-INF/services/com.dslplatform.json.Configuration` | Lets `rust-java-rest` discover generated DSL-JSON converters without reflection fallback. | The generated converter classes exist, but the framework does not load them. |
| `security/serialize.allowlist` | Lets Dubbo/Hessian deserialize only the DTO package you explicitly trust. | Provider may return Hessian status `40` for typed command requests. |
| `java-rust-dubbo` full profile + `hessian-lite` | Required for argument methods and typed record/list/scalar responses. | Only no-arg `byte[]` native-static calls are safe. |

Do not solve typed DTO errors by disabling serialization security globally or by enabling a generic
JSON reflection fallback. The sample keeps both paths explicit so the runtime stays predictable and
low-overhead.

The sample also enables `sample.dubbo.read-retry-on-io-error=true` by default. This is a narrow
read-side safety valve: if a previous failed Dubbo call or provider restart leaves one stale TCP
connection in the native pool, idempotent GET/query calls retry once when the error is a connection
abort such as Windows `os error 10053`. POST/PATCH/DELETE command calls do not use this retry because
automatic write retries can duplicate business operations.

## What ZooKeeper Provides, And When You Need It

ZooKeeper is not an HTTP performance feature. In this sample it is only the Dubbo registry/discovery
plane. It tells the consumer which provider addresses exist and lets the consumer follow provider
registration changes.

| ZooKeeper duty | What it means | Does Kubernetes Service DNS cover it? |
|----------------|---------------|----------------------------------------|
| Provider registration | Provider publishes interface, host, port, and URL metadata under the registry path. | Partly. K8s tracks pod endpoints, but not Dubbo interface/group/version metadata. |
| Provider discovery | Consumer reads the current provider address list. | Yes for simple one-service scenarios where the provider is represented by one Service DNS. |
| Provider change watch | Consumer can react when providers restart, disappear, or register again. | K8s readiness and EndpointSlice handle pod membership, but not Dubbo registry events. |
| Interface/group/version metadata | Consumer can distinguish Dubbo service variants from registry metadata. | No. You must model this with separate Service DNS names or config. |
| Dubbo governance/routing | Registry-driven routing, dynamic provider rules, or Dubbo governance features. | No. Use ZooKeeper if you depend on these features. |

**BEST for this framework:** if one Dubbo interface maps cleanly to one Kubernetes Service, use
static provider mode and point the consumer at that Service DNS:

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
```

**ACCEPTABLE:** if different provider interfaces are managed through different namespaces or Service
DNS names, static mode can still be explicit and production-safe:

```properties
reactor.dubbo.providers=customer-provider:20880,order-provider:20880
```

In this model, Kubernetes distributes TCP connections behind the Service regardless of whether there
is one provider replica or several. It does not balance every individual RPC call. If you keep
`reactor.dubbo.native-connections-per-endpoint=1`, one consumer pod can spend most of its time on
one backend connection. Use `2` or `4` only after measuring p99 and provider capacity.

Use ZooKeeper when providers are not represented by stable K8s Services, when VM/bare-metal
providers register dynamically, when interface/group/version metadata must come from the registry,
or when your organization depends on Dubbo governance/routing. Do not add ZooKeeper only because the
application runs in Kubernetes.

## Production Recipes

### Recipe 1: Kubernetes Consumer With ZooKeeper Discovery

Use this when provider pods can restart, move, or scale and the consumer must follow ZooKeeper
registrations.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "1"
  - name: REACTOR_DUBBO_NATIVE_ASYNC_WORKERS
    value: "1"
```

Build the image with the same dependency shape:

```powershell
mvn -q -Pzookeeper-discovery -DskipTests package
```

Effect:

| Setting | What it controls | If you increase it | If you decrease it |
|---------|------------------|--------------------|--------------------|
| `REACTOR_DUBBO_MAX_INFLIGHT` | Concurrent RPC calls allowed by Dubbo adapter | More work before fail-fast;<br>RSS/provider pressure can rise | Lower RSS;<br>more 503 under spikes |
| `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT` | Native Dubbo TCP connections per provider endpoint | Better parallelism for slow providers | Smaller connection/buffer footprint |
| `REACTOR_DUBBO_NATIVE_ASYNC_WORKERS` | Native RPC completion workers | Can reduce queueing at higher concurrency | Lower thread stack/native memory |
| `REACTOR_RUST_JNI_WORKERS` | Java handler execution workers | More Java work can run concurrently | Lower RSS and less CPU contention |

### Recipe 2: Small Read-Only Consumer

Use this when the provider address is known and your REST API only exposes read endpoints such as
`/api/v1/catalog/nested`.

```powershell
mvn -q -Pnative-static-consumer package
java -Xms8m -Xmx48m -Xss256k -XX:ActiveProcessorCount=1 `
  -Dreactor.dubbo.providers=provider:20880 `
  -jar target/rest-sample-dubbo-consumer-0.1.0.jar
```

Effect:

| Choice | Why it matters |
|--------|----------------|
| `native-static-consumer` | Keeps ZooKeeper and Hessian out of the read-only consumer classpath. |
| `RawResponse.json(bytes)` | Provider JSON is not parsed into Java records and serialized again. |
| `micro-dubbo` | Keeps Rust worker count, JNI workers, queues, and pools small by default. |

### Recipe 3: Command Endpoint With Bounded Overload

Use this for POST/PATCH/DELETE routes where each REST request becomes one provider command call.

```properties
reactor.rust.route-admission.post.api.v1.customers.max-concurrent=8
reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=150
reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent=8
reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=8
reactor.dubbo.timeout-ms=1000
reactor.dubbo.retries=0
```

Effect:

| Setting | Production effect |
|---------|-------------------|
| Route `max-concurrent` | Protects the REST process from too many slow RPC/DB calls. |
| Route `queue-timeout-ms` | Defines how long the REST layer waits before returning controlled overload. |
| `reactor.dubbo.timeout-ms` | Bounds one RPC call. Keep it lower than your HTTP timeout budget. |
| `reactor.dubbo.retries=0` | Avoids retry storms for write commands. Put idempotency/retry policy at the caller or workflow layer. |

### Recipe 4: Low-Traffic Pod That Must Stay Small After Idle

Use this only for services that receive occasional traffic and spend meaningful time idle.

```properties
reactor.rust.native-trim.enabled=true
reactor.rust.native-trim.initial-delay-ms=30000
reactor.rust.native-trim.interval-ms=60000
reactor.rust.native-trim.min-idle-ms=10000
reactor.rust.native-trim.max-active-connections=0
reactor.rust.native-trim.max-active-requests=0
reactor.rust.native-trim.retain-small=16
reactor.rust.native-trim.allocator-trim-enabled=true
```

Effect: warmed native anonymous memory can be returned after idle. Do not enable this blindly on
high-throughput services; validate p99 and 503 rate with trim on/off.

### Recipe 5: Provider Rolling Restart Without Restarting the Consumer

Use this when providers are redeployed during the day and the consumer must recover through
ZooKeeper discovery.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_DUBBO_REGISTRY_CHECK
    value: "false"
  - name: REACTOR_DUBBO_CHECK
    value: "false"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1000"
  - name: REACTOR_DUBBO_RETRIES
    value: "0"
```

Effect: the consumer can start even if a provider is temporarily absent. During the gap, REST routes
return bounded failures instead of growing queues. When the provider registers again under
ZooKeeper, the consumer can use the new provider address.

Practical check:

```powershell
curl http://localhost:8080/api/v1/catalog/dubbo-metrics
curl http://localhost:8080/api/v1/catalog/nested
```

### Recipe 6: Static Provider Address In Docker Or Kubernetes Service DNS

Use this when the provider address is stable as a Docker service name or Kubernetes Service DNS and
you do not need a ZooKeeper client in the consumer process.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
```

Effect: the consumer skips Java ZooKeeper client loading and connects directly to the provider host
you give it. This is a good shape for local Docker, Docker Compose, Kubernetes Service DNS, or a
sidecar-generated provider list.

If several provider interfaces are managed by separate Service DNS names, pass a comma-separated
list:

```properties
reactor.dubbo.providers=customer-provider:20880,order-provider:20880
```

ZooKeeper is not mandatory in this model. The number of provider pod replicas behind the Kubernetes
Service can change; the consumer connects to the Service DNS and Kubernetes distributes connections
to endpoints behind it. That distribution is TCP-connection based, not per-RPC. With one native
connection, a consumer pod can stay on one backend pod for a while. If you need more even spreading,
try `reactor.dubbo.native-connections-per-endpoint=2` or `4`, but do not make that the default
without measuring RSS and p99.

Switch to the ZooKeeper recipe only when provider addresses cannot be modeled as Service DNS names,
VM/bare-metal providers register dynamically, interface/group/version metadata must come from the
registry, or you depend on Dubbo governance/routing features.

### Recipe 7: Larger Provider JSON Response

Use this when the provider returns a bigger JSON document, for example a catalog snapshot or report.

```properties
reactor.rust.http.max-response-body-bytes=16777216
reactor.rust.http.max-inflight-response-bytes=33554432
reactor.dubbo.max-response-bytes=16777216
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=8
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=150
```

Effect:

| Setting | Meaning |
|---------|---------|
| `max-response-body-bytes` | Maximum single HTTP response body accepted by the Rust-Java runtime. |
| `max-inflight-response-bytes` | Total response bytes allowed across in-flight requests. |
| `reactor.dubbo.max-response-bytes` | Maximum response size accepted from the Dubbo provider. |
| Lower route concurrency | Prevents several large responses from being retained at the same time. |

Use `RawResponse.json(bytes)` for this path. Do not parse a large provider JSON into a Java object
graph only to serialize it back to JSON.

### Recipe 8: Higher Read Concurrency, Still Bounded

Use this when the catalog/read endpoint is stable, provider CPU is not saturated, and your p99 target
needs more successful requests under load.

```properties
reactor.dubbo.max-inflight=64
reactor.dubbo.native-connections-per-endpoint=2
reactor.dubbo.native-async-workers=2
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=32
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=150
```

Effect: more read calls can progress before overload. RSS and provider CPU can also rise. Increase
these values together, then measure p99, 503 rate, provider CPU, and memory. If the provider is
DB-backed, align this with the provider Hikari pool and provider method limit first.

### Recipe 9: Turkish Characters and UTF-8 Payloads

Use this when request bodies, query values, path variables, or provider JSON can contain values like
`İstanbul`, `Şişli`, `Çağrı`, or `müşteri`.

```java
@GetMapping(value = "/catalog/nested", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
    return catalogClient.getNestedCatalogJson()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

```powershell
curl "http://localhost:8080/api/v1/customers/db?city=%C4%B0stanbul"
```

Effect: keep JSON as UTF-8 bytes and return it with `RawResponse.json(bytes)`. For URLs, clients
should percent-encode non-ASCII values. For request bodies, send UTF-8 JSON and avoid platform
default string/byte conversions.

### Recipe 10: Health Endpoint Without Dubbo Pressure

Use a cheap local health endpoint for Kubernetes liveness, and keep Dubbo checks for readiness or a
separate diagnostics endpoint.

```powershell
curl http://localhost:8080/app/health
curl http://localhost:8080/api/v1/catalog/dubbo-metrics
```

Effect: liveness does not become dependent on ZooKeeper, provider CPU, or DB latency. Readiness can
still verify Dubbo/provider state when your deployment policy needs it.

## Production Scenario Guide

This section is not a list of properties to memorize. It is a practical guide for choosing settings
based on the shape of a real service. A single consumer can expose customer reads, order reads,
catalog reads, customer creation, status updates, and deletes at the same time. These endpoints do
not have the same cost, so giving every endpoint the same queue, timeout, and concurrency is not a
production-safe design.

### First, The Terms

| Term | Plain meaning | What it affects here |
|------|---------------|----------------------|
| `p99` | 99 of 100 requests finish below this latency. Example: p99 120 ms means most requests are fast, but the slowest 1 percent still reaches 120 ms. | Shows user-visible tail latency better than average latency. |
| `503` | The service says "I cannot accept this request right now". It is an error response, but it can be intentional controlled overload. | Prevents queues from growing until memory and latency explode. |
| Hot read | A heavily called read endpoint, such as get customer, get order, or get catalog. | Can receive a larger route budget, but must not exceed provider/DB capacity. |
| Cold route | A rarely called endpoint, such as admin actions, rare patch, or delete. | Should not steal capacity from hot endpoints. |
| Write command | A data-changing operation, such as create customer, cancel order, or change customer status. | Retries and deep queues are dangerous; idempotency is required. |
| Route budget | How many requests one endpoint may process at the same time. | Controlled by `reactor.rust.route-admission...max-concurrent`. |
| Queue timeout | How long a request may wait when the route budget is full. | Increasing `queue-timeout-ms` can reduce 503, but can increase p99 and memory. |
| In-flight | Requests or responses currently being processed. | Too much in-flight work increases RSS and p99. |
| RSS | The real memory seen by the operating system and Kubernetes. | This is the number your pod memory limit cares about. |
| Consumer | This sample: receives REST requests, optionally calls Dubbo provider, and returns HTTP responses. | REST and Dubbo settings are applied here. |
| Provider | The backend Dubbo service. It may call DB, execute business logic, or produce JSON. | If the provider is slow, making the consumer queue larger is not a real fix. |
| RawResponse | Returning provider JSON bytes directly without Java DTO parse/serialize work. | Preferred for lower allocation, lower CPU, and lower RSS. |

Route admission keys are generated from the HTTP method and path. For example,
`GET /api/v1/customers/db` maps to
`reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent`. If your path is different,
the property key must also match your own path.

### E-Commerce Customer And Order Service: 5 Endpoints, 3 Busy Endpoints

An e-commerce REST consumer serves mobile traffic. The mobile app frequently asks for customer
details, order summary, and catalog data. The same service also creates customers, changes customer
status, and deletes customers through a Dubbo provider. The provider uses PostgreSQL and Hikari.

API meaning:

| Real-world action | Sample endpoint | Traffic | Backend work |
|-------------------|-----------------|---------|--------------|
| Get catalog or order summary | `GET /api/v1/catalog/nested` | Very high | Dubbo read provider returns JSON |
| Get customer details | `GET /api/v1/customers/db` | Very high | Dubbo provider reads PostgreSQL |
| Create customer | `POST /api/v1/customers` | High | Dubbo provider writes PostgreSQL |
| Change customer status | `PATCH /api/v1/customers/{id}/status` | Low | Dubbo write command |
| Delete customer | `DELETE /api/v1/customers/{id}` | Low | Dubbo write command |

Timeline:

| Step | What happened? | What you see |
|------|----------------|--------------|
| Day one | You start with `micro-dubbo`. | RSS is low and endpoints are healthy. |
| Traffic grows | Three busy endpoints hit the provider at the same time. | `GET /customers/db` p99 rises and `POST /customers` slows down. |
| First wrong move | Only the global queue is increased. | 503 decreases, but p99 and memory rise. |
| Correct move | Each endpoint receives its own route budget. | Read endpoints stay productive and write commands stop overwhelming the provider. |

Starting configuration:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.jni.workers=1
reactor.rust.jni.queue-capacity=128

reactor.dubbo.max-inflight=24
reactor.dubbo.timeout-ms=1000
reactor.dubbo.retries=0
reactor.dubbo.native-connections-per-endpoint=2
reactor.dubbo.native-async-workers=1

reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=24
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=100

reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=150

reactor.rust.route-admission.post.api.v1.customers.max-concurrent=6
reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=150

reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent=2
reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=2
```

Why: cheap catalog/order reads can receive a larger budget. DB-backed customer reads start lower.
Write commands stay lower because provider and DB write capacity is not infinite. `retries=0`
prevents command retry storms.

Measure: successful `200` RPS per endpoint, p99 per endpoint, `503` rate per endpoint, provider DB
pool wait, and consumer RSS after 30-60 seconds of quiet idle time.

Different problems in this scenario and the exact property actions:

| Situation | Previous value | Change | Expected improvement | Cost / watch-out |
|-----------|----------------|--------|----------------------|------------------|
| Customer DB p99 rises | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=12</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8</code></small> | DB read matches provider capacity | Some `503` may increase |
| Create slows / duplicate risk | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=10</code><br><code>reactor.dubbo.retries=1</code></small> | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=6</code><br><code>reactor.dubbo.retries=0</code></small> | Retry storm decreases | Idempotency still needed |
| Cheap catalog read gets 503 | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16</code><br><code>reactor.dubbo.native-connections-per-endpoint=1</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=24</code><br><code>reactor.dubbo.native-connections-per-endpoint=2</code></small> | Hot read throughput rises | Provider CPU must be healthy |
| All endpoint p99 rises | Global queue increased | Revert queue<br>split route budgets | Slow route stops dominating | Endpoint metrics required |

### Loyalty Points Service: Small Pod, 4 Endpoints, 2 Busy Endpoints

Many small pods run in Kubernetes. This consumer mainly serves loyalty-point and customer-summary
reads. Two command endpoints are rare. The environment is memory-constrained, so the goal is not to
carry every spike; the goal is to keep each pod small.

API meaning:

| Real-world action | Sample endpoint | Traffic | Decision |
|-------------------|-----------------|---------|----------|
| Get points/catalog summary | `GET /api/v1/catalog/nested` | Very high | Return raw JSON |
| Get customer points | `GET /api/v1/customers/db` | Very high | Start DB-backed read budget low |
| Create points account | `POST /api/v1/customers` | Low | Keep command concurrency low |
| Disable account | `DELETE /api/v1/customers/{id}` | Low | Fast-fail is acceptable |

Timeline:

| Step | What happened? | What you see |
|------|----------------|--------------|
| Initial setup | Pod memory limit is low. | Service starts, but warm-load RSS matters. |
| Traffic spike | Two hot reads arrive at the same time. | Some requests get 503, but pod memory remains stable. |
| Expectation is clarified | This service is memory-first. | Controlled 503 is acceptable; unbounded queueing is not. |
| Idle period | Service receives little traffic. | Native trim can reduce idle RSS if enabled. |

Starting configuration:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.runtime.worker-threads=1
reactor.rust.runtime.max-blocking-threads=1
reactor.rust.runtime.thread-stack-bytes=262144
reactor.rust.jni.workers=1
reactor.rust.response-pool.small-capacity=8
reactor.rust.response-pool.medium-capacity=2
reactor.rust.response-pool.large-capacity=1
reactor.rust.json.writer-retain-max-bytes=32768
reactor.rust.http.max-connections=256

reactor.dubbo.max-inflight=8
reactor.dubbo.native-connections-per-endpoint=1
reactor.dubbo.native-async-workers=1
```

If the service stays idle for long periods and your p99 test passes, enable this separately:

```properties
reactor.rust.native-trim.enabled=true
reactor.rust.native-trim.initial-delay-ms=30000
reactor.rust.native-trim.interval-ms=60000
reactor.rust.native-trim.min-idle-ms=10000
reactor.rust.native-trim.max-active-connections=0
reactor.rust.native-trim.max-active-requests=0
reactor.rust.native-trim.retain-small=16
reactor.rust.native-trim.allocator-trim-enabled=true
```

Why: thread count, response pools, and Dubbo connections stay small, so RSS remains controlled. The
cost is that high concurrency can produce some 503. If every request must return 200, this is not a
memory-first scenario anymore.

Different problems in this scenario and the exact property actions:

| Situation | Previous value | Change | Expected improvement | Cost / watch-out |
|-----------|----------------|--------|----------------------|------------------|
| Idle RSS high | <small><code>reactor.rust.native-trim.enabled=false</code><br>wider response pools</small> | <small><code>reactor.rust.native-trim.enabled=true</code><br><code>reactor.rust.response-pool.small-capacity=8</code><br><code>reactor.rust.response-pool.medium-capacity=2</code><br><code>reactor.rust.response-pool.large-capacity=1</code></small> | Idle memory releases better | Trim only while idle |
| Hot read 503 high, RSS safe | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=12</code><br><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=6</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16</code><br><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8</code></small> | Useful `200` RPS rises | Re-measure RSS/DB wait |
| Pod limit tighter | <small><code>reactor.rust.http.max-connections=512</code><br><code>reactor.dubbo.max-inflight=16</code></small> | <small><code>reactor.rust.http.max-connections=256</code><br><code>reactor.dubbo.max-inflight=8</code></small> | RSS stays controlled | More 503 during spikes |
| p99 unstable after trim | <small><code>reactor.rust.native-trim.interval-ms=15000</code></small> | <small><code>reactor.rust.native-trim.initial-delay-ms=30000</code><br><code>reactor.rust.native-trim.interval-ms=60000</code><br><code>reactor.rust.native-trim.min-idle-ms=10000</code></small> | Trim is calmer | RSS drop may be slower |

### Reporting And Snapshot Service: 10 Endpoints, 4 Busy Endpoints, 1 Large JSON

An internal operations screen calls the consumer for customer snapshots, campaign lists, order
status, and one large report JSON. There are 10 endpoints total. Most traffic goes to 4 of them, and
one of those 4 returns a large JSON body.

API meaning:

| Real-world action | Sample endpoint | Traffic | Risk |
|-------------------|-----------------|---------|------|
| Get catalog/snapshot | `GET /api/v1/catalog/nested` | Very high | Can receive high budget if cheap |
| Get customer DB info | `GET /api/v1/customers/db` | Very high | Can wait on provider DB pool |
| Get customer+campaign large JSON | `GET /api/v1/catalog/db/customers` | High | Large responses retain memory together |
| Read metrics/health | `GET /api/v1/catalog/dubbo-metrics` | Medium | Must not steal hot route capacity |
| Other 6 endpoints | Admin or command | Low | Keep small budgets |

Timeline:

| Step | What happened? | What you see |
|------|----------------|--------------|
| Start | All endpoints share similar settings. | Small responses are fine, large JSON fluctuates. |
| Traffic grows | Large JSON endpoint receives many concurrent calls. | RSS rises and p99 gets worse. |
| Wrong move | `max-connections` is increased first. | More large responses are retained at the same time. |
| Correct move | Large JSON limits are adjusted together and route concurrency is reduced. | Memory is controlled and small hot reads are protected. |

Starting configuration:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.jni.workers=2
reactor.rust.jni.queue-capacity=128
reactor.dubbo.max-inflight=48
reactor.dubbo.native-connections-per-endpoint=3
reactor.dubbo.native-async-workers=2

reactor.rust.http.max-response-body-bytes=16777216
reactor.rust.http.max-inflight-response-bytes=33554432
reactor.dubbo.max-response-bytes=16777216

reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=32
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=125

reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=12
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=125

reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6
reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms=150
```

Why: large JSON needs more than one limit. Dubbo response limit, HTTP response limit, and total
in-flight response bytes must be considered together. The large JSON route receives lower concurrency
so it cannot keep too many large bodies in memory at once.

Measure: large JSON p99, small hot-read p99, `503` rate, response bytes, warm-load RSS, and idle RSS
after 30-60 seconds.

Different problems in this scenario and the exact property actions:

| Situation | Previous value | Change | Expected improvement | Cost / watch-out |
|-----------|----------------|--------|----------------------|------------------|
| Large JSON hits limit | <small><code>reactor.rust.http.max-response-body-bytes=8388608</code><br><code>reactor.dubbo.max-response-bytes=8388608</code></small> | <small><code>reactor.rust.http.max-response-body-bytes=16777216</code><br><code>reactor.dubbo.max-response-bytes=16777216</code><br><code>reactor.rust.http.max-inflight-response-bytes=33554432</code></small> | Response can return | Total in-flight also required |
| Large JSON raises RSS | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=10</code> or <code>12</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6</code></small> | Fewer large bodies retained | Large endpoint RPS may drop |
| Small reads slowed by large JSON | Same budget for all routes | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=32</code><br><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6</code></small> | Queues are separated | Route key must match path |
| Provider CPU free, p99 high | <small><code>reactor.dubbo.native-connections-per-endpoint=1</code><br><code>reactor.dubbo.native-async-workers=1</code></small> | <small><code>reactor.dubbo.native-connections-per-endpoint=3</code><br><code>reactor.dubbo.native-async-workers=2</code></small> | More native data-plane parallelism | No help if provider saturated |

### CRM Command Service: High Create, Patch, And Delete Pressure

A call-center or CRM screen creates customers, changes segment, updates status, and sometimes deletes
customers. These are not reads; they are commands that change data. The provider writes to DB. Some
clients retry when they see a timeout.

API meaning:

| Real-world action | Sample endpoint | Traffic | Risk |
|-------------------|-----------------|---------|------|
| Create customer | `POST /api/v1/customers` | High | Duplicate create |
| Change segment | `PATCH /api/v1/customers/{id}/segment` | Medium | Racing updates for same customer |
| Change status | `PATCH /api/v1/customers/{id}/status` | Medium | Retry conflicts with state |
| Delete customer | `DELETE /api/v1/customers/{id}` | Low | Hard-to-revert command |

Timeline:

| Step | What happened? | What you see |
|------|----------------|--------------|
| First live traffic | Read endpoints work fine. | Command routes show occasional timeout. |
| Client retries | The same command arrives again. | Provider and DB see duplicate write pressure. |
| Wrong move | Consumer queue is increased. | The problem is hidden, while p99 and memory rise. |
| Correct move | Command concurrency stays low, retries stay off, idempotency is planned. | The system becomes more predictable. |

Starting configuration:

```properties
reactor.dubbo.retries=0
reactor.dubbo.timeout-ms=1000
reactor.dubbo.max-inflight=16

reactor.rust.route-admission.post.api.v1.customers.max-concurrent=4
reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=100

reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent=4
reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms=100

reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent=4
reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms=100

reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=2
reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms=100
```

Production decision: "queue it and it will eventually finish" is an anti-pattern for command
endpoints. If guaranteed completion is required, add idempotency keys, outbox, or durable workflow.
The consumer should keep short timeouts and low concurrency so provider/DB limits remain visible.

Different problems in this scenario and the exact property actions:

| Situation | Previous value | Change | Expected improvement | Cost / watch-out |
|-----------|----------------|--------|----------------------|------------------|
| Create overloads DB | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=8</code> or <code>10</code></small> | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=4</code><br><code>reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=100</code></small> | DB write pressure drops | Peak write RPS drops |
| Command repeats after timeout | <small><code>reactor.dubbo.retries=1</code><br>blind client retry</small> | <small><code>reactor.dubbo.retries=0</code><br>client idempotency key required</small> | Duplicate risk drops | Use durable workflow for guarantee |
| Patch routes hurt each other | Segment/status share high budget | <small><code>reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent=4</code><br><code>reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent=4</code></small> | Patch types are isolated | Lock/idempotency still needed |
| Delete rare but expensive | <small><code>reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=4</code></small> | <small><code>reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=2</code></small> | Safer destructive flow | Admin may see 503 |

### Campaign Listing Service: Many Pods, Strict Memory, Controlled 503

Many small consumer pods run in the same namespace. Each pod reads campaign lists and customer
summaries heavily. Some admin or command endpoints are rare. The goal is not maximum RPS per pod; it
is low RSS per pod and horizontal scaling.

API meaning:

| Real-world action | Sample endpoint | Traffic | Decision |
|-------------------|-----------------|---------|----------|
| Get campaign/catalog | `GET /api/v1/catalog/nested` | Very high | Medium budget |
| Get customer summary | `GET /api/v1/customers/db` | High | Budget based on DB capacity |
| Create customer | `POST /api/v1/customers` | Low | Small command budget |
| Delete customer | `DELETE /api/v1/customers/{id}` | Low | Smallest budget |

Kubernetes starting configuration:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "campaign-provider:20880,customer-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_RUST_JNI_WORKERS
    value: "1"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "1"
  - name: REACTOR_RUST_HTTP_MAX_CONNECTIONS
    value: "256"
  - name: REACTOR_RUST_RESPONSE_POOL_SMALL_CAPACITY
    value: "8"
  - name: REACTOR_RUST_RESPONSE_POOL_MEDIUM_CAPACITY
    value: "2"
```

Route budget:

```properties
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.post.api.v1.customers.max-concurrent=2
reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=1
```

Timeline:

| Step | What happened? | What you see |
|------|----------------|--------------|
| First deployment | Many small pods start. | Total capacity is high and per-pod RSS is low. |
| Campaign burst | Two hot read endpoints spike. | Some cold routes can receive 503. |
| Traffic quiets down | Pods become idle. | RSS should return near the expected idle level. |

Correct interpretation: 503 is not always bad in this scenario. It is an early reject signal that
protects the pod memory limit. The real problem is hiding 503 by making queues larger and increasing
p99 for the whole service.

Different problems in this scenario and the exact property actions:

| Situation | Previous value | Change | Expected improvement | Cost / watch-out |
|-----------|----------------|--------|----------------------|------------------|
| Per-pod RSS high | <small><code>reactor.rust.http.max-connections=512</code><br><code>reactor.rust.response-pool.small-capacity=32</code><br><code>reactor.rust.response-pool.medium-capacity=8</code></small> | <small><code>reactor.rust.http.max-connections=256</code><br><code>reactor.rust.response-pool.small-capacity=8</code><br><code>reactor.rust.response-pool.medium-capacity=2</code></small> | Fewer idle/native buffers | Scale horizontally |
| Campaign hot read 503 high | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=8</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16</code></small> | Hot read accepts more | Measure RSS/provider CPU |
| Admin/write hurts hot reads | Admin/write budget high | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=2</code><br><code>reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=1</code></small> | Rare routes stop stealing capacity | Admin fail-fast |
| Global queue reduced 503 | <small><code>reactor.rust.jni.queue-capacity=512</code></small> | <small><code>reactor.rust.jni.queue-capacity=128</code><br>split route budgets</small> | Prevents global p99 rise | Define hot endpoint budgets |

### Call-Center Lookup API: Memory Is Fine, p99 Is High

A call-center screen continuously reads customer and order data. Pod memory limit is comfortable,
but users wait on the screen. Here the goal is not the lowest possible memory; the goal is to reduce
p99 to an acceptable level.

API meaning:

| Real-world action | Sample endpoint | Traffic | Decision |
|-------------------|-----------------|---------|----------|
| Customer lookup | `GET /api/v1/customers/db` | Very high | More Dubbo capacity |
| Order/catalog lookup | `GET /api/v1/catalog/nested` | Very high | More route budget |
| Large customer history | `GET /api/v1/catalog/db/customers` | Medium | Separate large JSON budget |
| Command endpoints | `POST/PATCH/DELETE` | Low | Keep small budgets |

Starting configuration:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.jni.workers=2
reactor.dubbo.max-inflight=64
reactor.dubbo.native-connections-per-endpoint=4
reactor.dubbo.native-async-workers=2
reactor.rust.http.max-connections=768
```

Timeline:

| Step | What happened? | What you see |
|------|----------------|--------------|
| First measurement | Average latency looks good. | p99 is bad, so users still feel waiting. |
| Capacity is raised | Dubbo in-flight and native connections are increased. | Hot read p99 can improve. |
| Admission is removed | Every endpoint is free to enter. | One slow provider route slows the whole service. |
| Correct move | Route budgets stay enabled; only hot-read capacity is raised. | p99 becomes more balanced and cold routes do not dominate. |

Before using this profile, check:

| Check | Required signal |
|-------|-----------------|
| Provider CPU | Has headroom |
| Provider DB pool | Not saturated |
| Consumer RSS | Still below pod limit after warm load |
| `503` rate | Lower after tuning, not only hidden by a larger queue |
| p99 | Improves for hot endpoints, not only average latency |

Different problems in this scenario and the exact property actions:

| Situation | Previous value | Change | Expected improvement | Cost / watch-out |
|-----------|----------------|--------|----------------------|------------------|
| Provider CPU free, p99 high | <small><code>reactor.dubbo.max-inflight=24</code><br><code>reactor.dubbo.native-connections-per-endpoint=2</code></small> | <small><code>reactor.dubbo.max-inflight=64</code><br><code>reactor.dubbo.native-connections-per-endpoint=4</code></small> | More parallel Dubbo calls | Measure RSS/DB pool |
| Only lookup is slow | Same hot route budget | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=12</code><br>catalog route values stay unchanged</small> | Problem route isolated | Lower again if DB wait rises |
| Large history hurts lookup | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=12</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6</code><br>small lookup route budget stays higher</small> | Small lookup p99 protected | Large JSON RPS drops |
| Admission removed | No route budgets | Restore budgets<br>raise only hot reads | Slow route cannot lock service | Safer but more config |

## What `rust-java-rest` 3.2.2 Changes Here

This sample now targets `rust-java-rest` `3.2.2`. The application code model does not change:
handlers, service adapters, configuration classes, and business decisions still live in Java. The
change is mostly about the runtime path underneath those handlers.

| v3.2.2 change | What it means in this sample |
|------------|------------------------------|
| Lower-retention response pools | The consumer keeps fewer native response buffers when traffic is low. |
| Bounded in-flight response bytes | Large or slow responses cannot grow memory usage without a hard cap. |
| UTF-8 response/path/query fixes | Turkish characters are safe when request values and response bytes are UTF-8. |
| Raw/precomputed response path maturity | Provider JSON `byte[]` can be returned as `RawResponse.json(bytes)` without DTO parse/serialize work. |
| Startup component/route indexes | The sample can start without classpath scanning fallback; if an index is stale, startup fails early. |
| Route-level admission | Dubbo-backed routes have bounded in-flight limits before they can fill the global JNI queue. |
| Route diagnostics separation | Benchmark-only comparison routes stay out of production heavy-object-graph counts. |
| Anon evidence gate | RSS is explained by heap, JIT, class metadata, direct buffers, Rust-accounted memory, stack budget, and residual anon. |
| Conservative idle native trim | Low-traffic consumer pods can reclaim warmed native anonymous memory after idle, but it stays opt-in. |
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

### Production Dependency And RSS Measurement

This sample depends on the normal `rust-java-rest` Maven artifact:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>rust-java-rest</artifactId>
  <version>3.2.2</version>
</dependency>
```

That normal artifact does not include the framework repository's demo handlers, benchmark routes,
or Dubbo sample application classes. Those classes live only in `rust-java-rest-*-sample.jar`.

For this repository, read the rule like this:

| What you run | Correct meaning |
|--------------|-----------------|
| This consumer application with the normal Maven dependency | Production-like consumer shape |
| This consumer with `zookeeper-discovery` profile | Kubernetes/ZooKeeper discovery shape |
| Framework `rust-java-rest-*-sample.jar` | Framework demo routes only, not this consumer |
| Framework `target/classes` | Local framework debugging only |

If you measure memory, use this consumer image or your real application image. Do not use the
framework sample jar to decide this consumer's pod memory limit.

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
| `full-dubbo-consumer` | Full artifact + `hessian-lite`<br>Default profile | All endpoints, including POST/PATCH/DELETE | Larger classpath than read-only path |
| `native-static-consumer` | `native-static` classifier<br>No Hessian/ZooKeeper | Static provider + no-arg `byte[]` reads | Argument methods need full profile |
| `zookeeper-discovery` | Full artifact + `hessian-lite` + ZooKeeper client | K8s or required ZooKeeper discovery | Adds Java ZooKeeper classes/threads |

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

Use ZooKeeper mode only when discovery is required. In this sample, ZooKeeper discovery is selected
with `sample.dubbo.discovery=zookeeper` or `SAMPLE_DUBBO_DISCOVERY=zookeeper`; that also means the
Java ZooKeeper client is loaded in the REST process.

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

The classes and interfaces in this sample are not response DTOs. When a DTO is needed, the normal
choice is a Java `record`; runtime behavior, config, handlers, and Dubbo interfaces stay as Java
`class` or `interface` types.

| Type | Role | HTTP JSON contract type |
|------|------|-------------------------|
| `RestSampleDubboConsumerApplication` | Starts the process and HTTP server. | Java class (startup/runtime); does not produce an HTTP JSON body. |
| `ConsumerProperties` | Reads and validates runtime properties. | Java class (config); does not produce an HTTP JSON body. |
| `DubboConsumerConfiguration` | Creates and closes Dubbo client beans. | Java class (resource/config); does not produce an HTTP JSON body. |
| `NestedCatalogClient` | Adapter around native Dubbo method invokers. | Java class (RPC adapter); not a JSON DTO or POJO response contract. |
| `CustomerQueryClient` | Adapter around the customer Dubbo interface. | Java class (RPC adapter); not a JSON DTO or POJO response contract. |
| `CatalogHandler` | REST endpoint behavior. | Java class (HTTP handler/controller); not a body type. It can return `RawResponse` or a Java `record` DTO. |
| `CustomerHandler` | Customer REST endpoint behavior. | Java class (HTTP handler/controller); not a body type. Request/response DTOs should be separate Java `record` types. |
| `HealthHandler` | Health endpoint behavior. | Java class (HTTP handler/controller); not a body type. |
| `NestedCatalogService` | Dubbo interface contract. | Java interface (Dubbo RPC contract); not an HTTP JSON body type. |
| `CustomerQueryService` | Second Dubbo interface contract. | Java interface (Dubbo RPC contract); not an HTTP JSON body type. |

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

### Data Flow Decision: bytes or records?

This is not just a Java type choice. It decides which process reads JSON, where validation happens,
where object graphs are created, and how much p99/RSS cost the route carries.

| Choice | Data flow | Consumer cost | Validation owner | Use when |
|--------|-----------|---------------|------------------|----------|
| `byte[] -> byte[]` | HTTP bytes -> Dubbo bytes -> `RawResponse` | Lowest<br>no DTO graph | Provider or lightweight byte validator | Read-heavy pass-through JSON |
| `record -> record` | JSON -> record -> Dubbo -> record -> JSON | Medium<br>parse + serialize | Consumer typed validation | Consumer must inspect fields |
| `record -> byte[] -> RawResponse` | JSON -> validate record -> bytes -> provider bytes | Medium request<br>low response | Consumer validates request; provider validates domain | Good hybrid model |
| `record -> byte[] -> record` | Record request + response parse again | High<br>double parse/serialize | Only if consumer reads response fields | Use only when required |
| `record -> List<record>` | List + item record graph | Highest | Consumer processes typed result | Small bounded pages |

BEST choice: if the consumer only carries the provider result to the HTTP client, do not convert the
response into a record. Return `RawResponse.json(providerBytes)` and avoid response parse, object
allocation, and JSON serialization.

```java
@PostMapping(value = "/customers", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody CustomerCreateRequest request) {
    validateCreateRequest(request);
    byte[] commandJson = customerJsonWriter.write(request);

    return customerCommandClient.createCustomer(commandJson)
            .thenApply(providerJson -> ResponseEntity.ok(RawResponse.json(providerJson)));
}
```

In this hybrid model, the consumer validates the request with typed code. The provider still owns
domain rules, DB constraints, and the response JSON shape. The consumer does not inspect the provider
response, so no response object graph is created.

| Class | Model | Why |
|-------|-------|-----|
| BEST | `byte[] -> byte[]` + `RawResponse` | Lowest allocation and most stable p99 |
| BEST | `record request -> byte[] provider -> RawResponse` | Request validation without response parse |
| ACCEPTABLE | `record -> record` | Readable for small typed business responses |
| ACCEPTABLE | `List<record>` | Only with small page + strict `limit` |
| ANTI-PATTERN | Hot path `record -> byte[] -> record -> JSON` | Loses the byte-transport advantage |
| ANTI-PATTERN | Unbounded `List<record>` | Grows heap, GC, p99, and RSS |

Keep validation ownership explicit:

| Validation type | Where to do it | Why |
|-----------------|----------------|-----|
| Basic request shape | Consumer | Rejects bad requests before provider work |
| Domain rule / DB uniqueness | Provider | Provider owns the data |
| Auth / tenant boundary | Consumer or gateway | Fail fast near the HTTP boundary |
| Response contract check | Test/contract suite | Runtime parsing every response is expensive |
| Large JSON schema validation | Outside hot path | High CPU and allocation cost |

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
    <version>3.2.2</version>
</dependency>

<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo</artifactId>
    <version>0.1.0-rc4</version>
</dependency>

<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>hessian-lite</artifactId>
    <version>4.0.3</version>
</dependency>
```

`hessian-lite` is needed by the POST/PATCH/DELETE command examples because those Dubbo methods carry
arguments. It is also needed when a Dubbo method returns a typed record, `List`, `Map`, `String`, or
primitive wrapper through the legacy Hessian codec path. The no-argument `byte[]` read path can use
the native byte-array fast path without Hessian request encoding.

The sample contains the Dubbo interface sources only to keep the demo self-contained:

```java
package com.reactor.rust.dubbo.sample;

public interface NestedCatalogService {
    byte[] getNestedCatalogJson();

    String getCatalogTitle();

    int countCatalogItems();

    CatalogInfo getCatalogInfo();

    List<CatalogItem> listFeaturedItems(int limit);

    Map<String, String> getCatalogAttributes();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();

    CustomerSummary getCustomer(long customerId);

    List<CustomerSummary> findCustomersBySegment(String segment, int limit);

    CustomerStats getCustomerStats();

    boolean customerExists(long customerId);

    String getCustomerDisplayName(long customerId);
}

public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);

    CustomerMutationResult createCustomerTyped(CreateCustomerCommand command);

    CustomerMutationResult patchCustomerStatusTyped(long customerId, String status, String requestId);
}
```

In a real system, publish these interfaces from a shared `*-api` jar used by both provider and
consumer. Do not copy/paste slightly different versions into separate services.

### Dubbo Method Shape Catalog

| Method shape | Example / Endpoint | Use when | Runtime cost |
|--------------|--------------------|----------|--------------|
| `byte[]` JSON, no args | `getNestedCatalogJson()`<br>`GET /api/v1/catalog/nested` | Provider owns JSON shape<br>consumer only forwards | Lowest<br>native no-arg fast path, no DTO graph |
| `String` | `getCatalogTitle()`<br>`GET /api/v1/catalog/title` | Tiny label/title/single value | Small String allocation<br>REST JSON wrapper |
| Primitive/scalar | `countCatalogItems()`<br>`customerExists(id)` | Counts, flags, cheap lookup | Hessian decode<br>no large object graph |
| Java `record` | `getCatalogInfo()`<br>`getCustomer(id)` | Branching/validation/enrichment needs fields | Hessian record + REST JSON serialize |
| `List<record>` | `listFeaturedItems(limit)`<br>`findCustomersBySegment(...)` | Small bounded pages | List + item allocation<br>`limit` must be bounded |
| `Map<String,String>` | `getCatalogAttributes()`<br>`GET /api/v1/catalog/attributes` | Small metadata bags | Map allocation<br>avoid large dynamic payloads |
| `record -> record` command | `createCustomerTyped(...)`<br>`POST /api/v1/customers/typed` | Clear typed command contract | Request encode + response decode + REST serialize |
| `byte[] -> byte[]` command | `createCustomer(byte[])`<br>`POST /api/v1/customers` | Lowest-allocation command pass-through | Bytes are carried<br>validation stays in provider |

Production rule: do not replace every `byte[]` method with records just because records are easier to
read. Use typed records when the consumer needs typed business data. Keep `byte[] + RawResponse` for
hot read paths where the consumer only forwards provider JSON.

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
JVM system property (-Dkey=value)
  > environment variable
  > loaded rust-spring.properties
  > framework internal defaults
```

The checked-in `src/main/resources/rust-spring.properties` is packaged into the application classpath
and acts as the baseline configuration. In Kubernetes and Docker, prefer environment variables or JVM
`-D...` options for environment-specific values. Do not rely on a mounted `rust-spring.properties`
file overriding the packaged file; the framework loads the classpath file first and uses filesystem
paths only when no classpath file is present.

You can set the same value in three ways:

```properties
# src/main/resources/rust-spring.properties
sample.dubbo.discovery=zookeeper
reactor.dubbo.registry-address=zookeeper://127.0.0.1:2181
```

```powershell
# JVM system properties. Highest priority.
java `
  -Dsample.dubbo.discovery=zookeeper `
  -Dreactor.dubbo.registry-address=zookeeper://127.0.0.1:2181 `
  -cp "classes;dependency/*" `
  com.reactor.sample.dubbo.consumer.app.RestSampleDubboConsumerApplication
```

```yaml
# Kubernetes environment variables. Preferred for deployments.
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
```

All properties can be overridden as environment variables. Conversion rule:

```text
sample.dubbo.discovery -> SAMPLE_DUBBO_DISCOVERY
reactor.dubbo.native-connections-per-endpoint -> REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent -> REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_MAX_CONCURRENT
```

The rule is simple: uppercase the key, replace `.` with `_`, and replace `-` with `_`.

If you need to pass JVM system properties through a container without changing the command, use
`JAVA_TOOL_OPTIONS`:

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-Dreactor.dubbo.max-inflight=8 -Dreactor.dubbo.timeout-ms=1000"
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
| `reactor.dubbo.providers` | Static provider list. Can be one Service DNS or a comma-separated list: `rest-sample-dubbo-provider:20880` or `customer-provider:20880,order-provider:20880`. |
| `reactor.dubbo.registry-address` | ZooKeeper address used in discovery mode. |
| `reactor.dubbo.timeout-ms` | Per-RPC timeout. |
| `reactor.dubbo.max-inflight` | Bounded RPC concurrency. |
| `reactor.dubbo.native-connections-per-endpoint` | Native Dubbo TCP connection pool size per provider. Keep it low for memory-first services; increase only with p99/RSS measurements. |

Quick symptom lookup:

| If you see this | Start with these keys | What to check first |
|-----------------|-----------------------|---------------------|
| Request body rejected or create/patch payload is too small | `reactor.rust.http.max-request-body-bytes`, `reactor.rust.http.max-inflight-body-bytes` | Increase per-body and total in-flight body limits together. |
| Provider JSON is rejected as too large | `reactor.dubbo.max-response-bytes`, `reactor.rust.http.max-response-body-bytes`, `reactor.rust.http.max-inflight-response-bytes` | All three limits must allow the payload. |
| Many `503` under traffic spike | Route-specific `reactor.rust.route-admission.*`, `reactor.dubbo.max-inflight` | Raise route budget only if provider CPU/DB pool has headroom. |
| p99 grows but RSS is still low | `reactor.dubbo.native-connections-per-endpoint`, `reactor.dubbo.native-async-workers`, route queue timeout | Improve connection reuse before adding many Java workers. |
| RSS stays high after traffic stops | `reactor.rust.response-pool.*`, `reactor.rust.json.writer-retain-max-bytes`, optional `reactor.rust.native-trim.*` | Use idle trim only for low-traffic pods and measure p99. |
| Startup index error | `component-index.*`<br>`route-index.*`<br>`scan.fallback-enabled` | Regenerate index files after adding handlers/routes. |
| Static Kubernetes consumer cannot find provider | `sample.dubbo.discovery`, `reactor.dubbo.providers` | If `SAMPLE_DUBBO_DISCOVERY=static`, check provider Service DNS, port, and readiness. Do not use `127.0.0.1`. |
| K8s ZooKeeper consumer cannot find provider | `sample.dubbo.discovery`<br>`registry-address`<br>`registry-root` | Build with `zookeeper-discovery`.<br>Check registry DNS/provider node. |
| Static Docker consumer connects to the wrong place | `reactor.dubbo.providers` | Use container/service DNS, not `127.0.0.1`, inside Docker networks. |
| Slow/unstable write commands | `reactor.dubbo.retries`, command route admission keys, `reactor.dubbo.timeout-ms` | Keep retries `0`; tune bounded queue and timeout. |
| Too many idle HTTP clients hold resources | `reactor.rust.http.max-connections`, `reactor.rust.http.idle-timeout-ms`, `reactor.rust.http.keep-alive-enabled` | Lower idle timeout before disabling keep-alive. |

### Complete Runtime Property Guide

This is the full `src/main/resources/rust-spring.properties` key set for this sample. Treat these
values as the packaged baseline. In Kubernetes or standalone runtime, override only the keys that
match your scenario with `-D...` or environment variables.

Server and startup:

| Property | Default | What it means / when to change |
|----------|---------|--------------------------------|
| `server.port` | `8080` | HTTP port. Change when the pod/container port is different. |
| `server.host` | `0.0.0.0` | Bind address. Keep `0.0.0.0` in containers; use `127.0.0.1` only for local-only tests. |
| `reactor.runtime.profile` | `micro-dubbo` | Memory-first REST + native Dubbo preset. Change only when you intentionally move to a larger/balanced runtime. |
| `reactor.startup.component-index.enabled` | `true` | Uses `components.idx` instead of broad classpath scan. Keep on for production-like startup. |
| `reactor.startup.component-index.required` | `true` | Fails startup if component index is missing. Turn off only while prototyping new classes locally. |
| `reactor.startup.route-index.validate` | `true` | Validates actual routes against `routes.idx`. Keep on to catch stale route metadata. |
| `reactor.startup.route-index.required` | `true` | Fails startup if route index is missing. Turn off only for local experiments. |
| `reactor.startup.scan.fallback-enabled` | `false` | Prevents silent classpath scan fallback. Keep `false` when RSS/startup predictability matters. |

HTTP and request/response bounds:

| Property | Default | What it means / when to change |
|----------|---------|--------------------------------|
| `reactor.rust.http.max-request-body-bytes` | `1048576` | Maximum request body. Increase for larger POST/PATCH payloads; do not use it as an unlimited upload path. |
| `reactor.rust.http.max-response-body-bytes` | `8388608` | Maximum single REST response. Increase together with Dubbo and in-flight response limits for larger provider JSON. |
| `reactor.rust.http.max-inflight-body-bytes` | `16777216` | Total in-flight request body budget. Lower for small pods; raise only after measuring 413/503 behavior. |
| `reactor.rust.http.max-inflight-response-bytes` | `16777216` | Total in-flight response byte budget. If large responses get rejected, raise this before raising global workers. |
| `reactor.rust.http.max-connections` | `512` | Accepted HTTP connection cap. Increase only if connection pressure is real; too high can raise RSS. |
| `reactor.rust.http.max-request-header-bytes` | `16384` | Maximum header bytes per request. Increase only for known large headers/tokens. |
| `reactor.rust.http.max-request-headers` | `64` | Maximum header count. Useful when clients send many tracing/security headers. |
| `reactor.rust.http.header-read-timeout-ms` | `5000` | Header read timeout. Lower to reject slow clients faster; raise only for slow networks. |
| `reactor.rust.http.request-body-timeout-ms` | `10000` | Body read timeout. Tune for large command bodies or slow clients. |
| `reactor.rust.http.idle-timeout-ms` | `30000` | Keep-alive idle timeout. Lower for tiny pods with many idle clients; raise for connection reuse. |
| `reactor.rust.http.http1-only-enabled` | `true` | Keeps the sample on a smaller HTTP/1 surface. Change only if you intentionally need HTTP/2 behavior. |
| `reactor.rust.http.keep-alive-enabled` | `true` | Keeps connections reusable. Disable only for strict one-request connection tests. |

Runtime, queues, pools, and memory:

| Property | Default | What it means / when to change |
|----------|---------|--------------------------------|
| `reactor.rust.log.level` | `error` | Native log level. Raise temporarily for diagnostics; keep low on hot paths. |
| `reactor.rust.java.log.level` | `warn` | Java framework log level. Raise for debugging startup/config, not for steady production. |
| `reactor.rust.jni.workers` | `1` | Java handler worker count. Increase for more concurrent Java work; RSS and CPU contention rise. |
| `reactor.rust.jni.queue-capacity` | `128` | Global JNI queue cap. Raising can hide overload; prefer route admission first. |
| `reactor.rust.response-pool.small-capacity` | `8` | Small native response buffer retention. Higher reduces allocation churn; lower reduces idle RSS. |
| `reactor.rust.response-pool.medium-capacity` | `2` | Medium response buffer retention. Keep low for small pods. |
| `reactor.rust.response-pool.large-capacity` | `1` | Large response buffer retention. Raise only for repeated large responses with measured churn. |
| `reactor.rust.response-pool.huge-capacity` | `1` | Huge response buffer retention. Keep low unless the service repeatedly returns huge bounded payloads. |
| `reactor.rust.websocket.max-frame-bytes` | `262144` | WebSocket frame limit. This sample is REST/Dubbo-first; tune only if WebSocket routes are added. |
| `reactor.rust.websocket.outbound-queue-capacity` | `16` | Per-session outbound WebSocket queue. Keep bounded to avoid slow-consumer memory growth. |
| `reactor.rust.websocket.send-timeout-ms` | `1000` | WebSocket send timeout. Raise only when slow clients are acceptable. |
| `reactor.rust.runtime.worker-threads` | `1` | Native runtime worker threads. Increase for higher I/O concurrency after measuring RSS/p99. |
| `reactor.rust.runtime.max-blocking-threads` | `1` | Native blocking worker cap. Keep low unless file/blocking work is intentionally used. |
| `reactor.rust.runtime.thread-stack-bytes` | `262144` | Native runtime stack size. Keep the tested value unless stack-depth smoke tests prove smaller is safe. |
| `reactor.rust.native-cache.max-entries` | `0` | Native response cache entries. `0` means disabled; enable only for explicit read-heavy cacheable responses. |
| `reactor.rust.native-cache.max-bytes` | `0` | Native response cache byte cap. Must be set with `max-entries` if cache is enabled. |
| `reactor.rust.native-cache.ttl-ms` | `300000` | Native cache TTL. Relevant only when native cache is enabled. |
| `reactor.rust.async.max-inflight` | `64` | Framework async response cap. Raise if async completions queue up; measure p99 and memory. |
| `reactor.rust.async.response-timeout-ms` | `1500` | Async response timeout. Keep aligned with Dubbo timeout and HTTP budget. |
| `reactor.rust.json.writer-initial-bytes` | `4096` | Initial direct JSON writer buffer. Increase for consistently larger direct JSON responses. |
| `reactor.rust.json.writer-retain-max-bytes` | `32768` | Maximum retained JSON writer buffer. Lower for idle RSS; raise for repeated medium JSON generation. |

Route admission:

| Property | Default | What it means / when to change |
|----------|---------|--------------------------------|
| `reactor.rust.route-admission.enabled` | `true` | Enables bounded route-level overload control. Keep on for Dubbo-backed routes. |
| `reactor.rust.route-admission.default-max-concurrent` | `0` | Global default route limit. `0` means no default cap; this sample uses explicit route keys instead. |
| `reactor.rust.route-admission.default-queue-timeout-ms` | `0` | Global default queue wait. `0` means do not queue by default. |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent` | `16` | Read-heavy nested catalog cap. Raise for fast provider reads; lower if provider CPU/RSS rises. |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms` | `100` | Catalog wait budget before controlled overload. Raise for fewer 503s, lower for tighter p99. |
| `reactor.rust.route-admission.get.api.v1.catalog.title/count/info/items/attributes.*` | `16` / `100` | Typed catalog scalar/record/list/map route caps. Lower list/map routes if object allocation hurts p99. |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent` | `8` | DB-backed catalog route cap. Keep aligned with provider DB pool and method bulkhead. |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms` | `150` | DB-backed catalog wait budget. If p99 is high, lower this before increasing workers. |
| `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent` | `8` | Customer DB read route cap. Tune with provider `CustomerQueryService` concurrency. |
| `reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms` | `150` | Customer DB read queue wait. Lower for faster fail-fast under DB saturation. |
| `reactor.rust.route-admission.get.api.v1.customers.db.stats/by-segment/id.*` | `4-8` / `150` | Typed DB stats, list, and record lookup caps. Keep list queries lower than raw byte pass-through routes. |
| `reactor.rust.route-admission.get.api.v1.customers.id.exists/display-name.*` | `8` / `150` | Small scalar lookup caps. Tune with provider DB pool if these call the database. |
| `reactor.rust.route-admission.post.api.v1.customers.max-concurrent` | `8` | Create command cap. Keep bounded to avoid duplicate-write pressure and DB queue buildup. |
| `reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms` | `150` | Create command wait budget. Lower if write p99 matters more than absorbing bursts. |
| `reactor.rust.route-admission.post.api.v1.customers.typed.*` | `4` / `150` | Typed create command cap. Lower than byte pass-through because REST record parse and Hessian record encode/decode are involved. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent` | `8` | Segment patch cap. Keep aligned with command provider capacity. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms` | `150` | Segment patch queue wait. Raise only with idempotent caller behavior and measured p99. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent` | `8` | Status patch cap. Keep bounded for write-side stability. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms` | `150` | Status patch queue wait. Lower when overload should be visible quickly. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.typed.*` | `4` / `150` | Typed status command cap. Keep aligned with typed command provider method limit. |
| `reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent` | `8` | Delete command cap. Keep conservative; delete is usually a write/side-effect route. |
| `reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms` | `150` | Delete command wait budget. Lower for stricter fail-fast behavior. |

Dubbo consumer:

| Property | Default | What it means / when to change |
|----------|---------|--------------------------------|
| `sample.dubbo.discovery` | `static` | Sample switch: `static` uses `reactor.dubbo.providers`; `zookeeper` uses registry discovery. |
| `reactor.dubbo.enabled` | `true` | Enables the Dubbo consumer adapter. Keep true for this sample. |
| `reactor.dubbo.application-name` | `rest-sample-dubbo-consumer` | Dubbo application name used in client metadata. Change per service. |
| `reactor.dubbo.transport` | `native` | Uses the lightweight native data-plane. Keep native for the low-overhead path. |
| `reactor.dubbo.runtime-profile` | `micro-dubbo` | Dubbo runtime sizing preset. Increase only after measuring RPC p99/RSS. |
| `reactor.dubbo.registry-address` | `zookeeper://127.0.0.1:2181` | ZooKeeper address for discovery mode. Override in Kubernetes. |
| `reactor.dubbo.registry-root` | `dubbo` | ZooKeeper registry root. Must match provider registration. |
| `reactor.dubbo.registry-timeout-ms` | `3000` | ZooKeeper operation timeout. Raise only for slow registry networks. |
| `reactor.dubbo.registry-session-timeout-ms` | `30000` | ZooKeeper session timeout. Controls how quickly dead providers disappear. |
| `reactor.dubbo.registry-check` | `false` | If true, startup can fail when registry is unavailable. Keep false for rolling deploys. |
| `reactor.dubbo.providers` | `127.0.0.1:20880` | Static provider list.<br>Use Service DNS in Docker/K8s.<br>Example: `customer-provider:20880,order-provider:20880` |
| `reactor.dubbo.timeout-ms` | `1000` | Per-RPC timeout. Keep below the HTTP route timeout budget. |
| `reactor.dubbo.retries` | `0` | Retry count. Keep `0` for write/command routes to avoid duplicate execution. |
| `reactor.dubbo.check` | `false` | If true, reference startup can fail when provider is absent. Keep false for provider rolling restarts. |
| `reactor.dubbo.lazy` | `true` | Defers some connection/reference work. Keep true for smaller startup. |
| `reactor.dubbo.protocol` | `dubbo` | Protocol name. This sample is for classic `dubbo://`. |
| `reactor.dubbo.serialization` | `hessian2` | Serialization. Required for argument-carrying command methods. |
| `reactor.dubbo.cluster` | `failfast` | Failure strategy. Good for bounded low-latency calls; do not hide errors with deep retries. |
| `reactor.dubbo.loadbalance` | `random` | Provider selection when multiple providers exist. For one provider this has little effect. |
| `reactor.dubbo.connections` | `1` | Logical connection count. Native pool sizing is mainly controlled by native connection keys. |
| `reactor.dubbo.share-connections` | `1` | Shared connection setting for compatibility. Keep small. |
| `reactor.dubbo.refer-thread-num` | `1` | Reference thread count. Keep low for RSS. |
| `reactor.dubbo.max-inflight` | `32` | Bounded concurrent RPC calls. Raise for more throughput; lower for low-RSS/fail-fast behavior. |
| `reactor.dubbo.max-response-bytes` | `8388608` | Maximum Dubbo response. Raise with HTTP response limits for larger provider JSON. |
| `reactor.dubbo.native-connections-per-endpoint` | `1` | Native TCP connections per provider. First knob to raise for read-heavy p99 improvement. |
| `reactor.dubbo.native-async-workers` | `1` | Native Dubbo async workers. Raise for high concurrency; each worker adds thread/native cost. |
| `reactor.dubbo.native-async-queue-capacity` | `32` | Native Dubbo async queue. Raising absorbs bursts but can hide overload and increase tail latency. |

<details>
<summary>Complete sample property to environment variable map</summary>

| Property | Environment variable |
|----------|----------------------|
| `server.port` | `SERVER_PORT` |
| `server.host` | `SERVER_HOST` |
| `reactor.runtime.profile` | `REACTOR_RUNTIME_PROFILE` |
| `reactor.startup.component-index.enabled` | `REACTOR_STARTUP_COMPONENT_INDEX_ENABLED` |
| `reactor.startup.component-index.required` | `REACTOR_STARTUP_COMPONENT_INDEX_REQUIRED` |
| `reactor.startup.route-index.validate` | `REACTOR_STARTUP_ROUTE_INDEX_VALIDATE` |
| `reactor.startup.route-index.required` | `REACTOR_STARTUP_ROUTE_INDEX_REQUIRED` |
| `reactor.startup.scan.fallback-enabled` | `REACTOR_STARTUP_SCAN_FALLBACK_ENABLED` |
| `reactor.rust.http.max-request-body-bytes` | `REACTOR_RUST_HTTP_MAX_REQUEST_BODY_BYTES` |
| `reactor.rust.http.max-response-body-bytes` | `REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES` |
| `reactor.rust.http.max-inflight-body-bytes` | `REACTOR_RUST_HTTP_MAX_INFLIGHT_BODY_BYTES` |
| `reactor.rust.http.max-inflight-response-bytes` | `REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES` |
| `reactor.rust.http.max-connections` | `REACTOR_RUST_HTTP_MAX_CONNECTIONS` |
| `reactor.rust.http.max-request-header-bytes` | `REACTOR_RUST_HTTP_MAX_REQUEST_HEADER_BYTES` |
| `reactor.rust.http.max-request-headers` | `REACTOR_RUST_HTTP_MAX_REQUEST_HEADERS` |
| `reactor.rust.http.header-read-timeout-ms` | `REACTOR_RUST_HTTP_HEADER_READ_TIMEOUT_MS` |
| `reactor.rust.http.request-body-timeout-ms` | `REACTOR_RUST_HTTP_REQUEST_BODY_TIMEOUT_MS` |
| `reactor.rust.http.idle-timeout-ms` | `REACTOR_RUST_HTTP_IDLE_TIMEOUT_MS` |
| `reactor.rust.http.http1-only-enabled` | `REACTOR_RUST_HTTP_HTTP1_ONLY_ENABLED` |
| `reactor.rust.http.keep-alive-enabled` | `REACTOR_RUST_HTTP_KEEP_ALIVE_ENABLED` |
| `reactor.rust.log.level` | `REACTOR_RUST_LOG_LEVEL` |
| `reactor.rust.java.log.level` | `REACTOR_RUST_JAVA_LOG_LEVEL` |
| `reactor.rust.jni.workers` | `REACTOR_RUST_JNI_WORKERS` |
| `reactor.rust.jni.queue-capacity` | `REACTOR_RUST_JNI_QUEUE_CAPACITY` |
| `reactor.rust.response-pool.small-capacity` | `REACTOR_RUST_RESPONSE_POOL_SMALL_CAPACITY` |
| `reactor.rust.response-pool.medium-capacity` | `REACTOR_RUST_RESPONSE_POOL_MEDIUM_CAPACITY` |
| `reactor.rust.response-pool.large-capacity` | `REACTOR_RUST_RESPONSE_POOL_LARGE_CAPACITY` |
| `reactor.rust.response-pool.huge-capacity` | `REACTOR_RUST_RESPONSE_POOL_HUGE_CAPACITY` |
| `reactor.rust.websocket.max-frame-bytes` | `REACTOR_RUST_WEBSOCKET_MAX_FRAME_BYTES` |
| `reactor.rust.websocket.outbound-queue-capacity` | `REACTOR_RUST_WEBSOCKET_OUTBOUND_QUEUE_CAPACITY` |
| `reactor.rust.websocket.send-timeout-ms` | `REACTOR_RUST_WEBSOCKET_SEND_TIMEOUT_MS` |
| `reactor.rust.runtime.worker-threads` | `REACTOR_RUST_RUNTIME_WORKER_THREADS` |
| `reactor.rust.runtime.max-blocking-threads` | `REACTOR_RUST_RUNTIME_MAX_BLOCKING_THREADS` |
| `reactor.rust.runtime.thread-stack-bytes` | `REACTOR_RUST_RUNTIME_THREAD_STACK_BYTES` |
| `reactor.rust.native-cache.max-entries` | `REACTOR_RUST_NATIVE_CACHE_MAX_ENTRIES` |
| `reactor.rust.native-cache.max-bytes` | `REACTOR_RUST_NATIVE_CACHE_MAX_BYTES` |
| `reactor.rust.native-cache.ttl-ms` | `REACTOR_RUST_NATIVE_CACHE_TTL_MS` |
| `reactor.rust.async.max-inflight` | `REACTOR_RUST_ASYNC_MAX_INFLIGHT` |
| `reactor.rust.async.response-timeout-ms` | `REACTOR_RUST_ASYNC_RESPONSE_TIMEOUT_MS` |
| `reactor.rust.json.writer-initial-bytes` | `REACTOR_RUST_JSON_WRITER_INITIAL_BYTES` |
| `reactor.rust.json.writer-retain-max-bytes` | `REACTOR_RUST_JSON_WRITER_RETAIN_MAX_BYTES` |
| `reactor.rust.route-admission.enabled` | `REACTOR_RUST_ROUTE_ADMISSION_ENABLED` |
| `reactor.rust.route-admission.default-max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_DEFAULT_MAX_CONCURRENT` |
| `reactor.rust.route-admission.default-queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_DEFAULT_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.catalog.info.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_INFO_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.items.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_ITEMS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.attributes.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_ATTRIBUTES_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_DB_CUSTOMERS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_DB_CUSTOMERS_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.customers.db.by-segment.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_BY_SEGMENT_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.customers.db.id.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_ID_MAX_CONCURRENT` |
| `reactor.rust.route-admission.post.api.v1.customers.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.post.api.v1.customers.typed.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_TYPED_MAX_CONCURRENT` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_SEGMENT_MAX_CONCURRENT` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_SEGMENT_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_STATUS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_STATUS_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.typed.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_STATUS_TYPED_MAX_CONCURRENT` |
| `reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_DELETE_API_V1_CUSTOMERS_ID_MAX_CONCURRENT` |
| `reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_DELETE_API_V1_CUSTOMERS_ID_QUEUE_TIMEOUT_MS` |
| `sample.dubbo.discovery` | `SAMPLE_DUBBO_DISCOVERY` |
| `reactor.dubbo.enabled` | `REACTOR_DUBBO_ENABLED` |
| `reactor.dubbo.application-name` | `REACTOR_DUBBO_APPLICATION_NAME` |
| `reactor.dubbo.transport` | `REACTOR_DUBBO_TRANSPORT` |
| `reactor.dubbo.runtime-profile` | `REACTOR_DUBBO_RUNTIME_PROFILE` |
| `reactor.dubbo.registry-address` | `REACTOR_DUBBO_REGISTRY_ADDRESS` |
| `reactor.dubbo.registry-root` | `REACTOR_DUBBO_REGISTRY_ROOT` |
| `reactor.dubbo.registry-timeout-ms` | `REACTOR_DUBBO_REGISTRY_TIMEOUT_MS` |
| `reactor.dubbo.registry-session-timeout-ms` | `REACTOR_DUBBO_REGISTRY_SESSION_TIMEOUT_MS` |
| `reactor.dubbo.registry-check` | `REACTOR_DUBBO_REGISTRY_CHECK` |
| `reactor.dubbo.providers` | `REACTOR_DUBBO_PROVIDERS` |
| `reactor.dubbo.timeout-ms` | `REACTOR_DUBBO_TIMEOUT_MS` |
| `reactor.dubbo.retries` | `REACTOR_DUBBO_RETRIES` |
| `reactor.dubbo.check` | `REACTOR_DUBBO_CHECK` |
| `reactor.dubbo.lazy` | `REACTOR_DUBBO_LAZY` |
| `reactor.dubbo.protocol` | `REACTOR_DUBBO_PROTOCOL` |
| `reactor.dubbo.serialization` | `REACTOR_DUBBO_SERIALIZATION` |
| `reactor.dubbo.cluster` | `REACTOR_DUBBO_CLUSTER` |
| `reactor.dubbo.loadbalance` | `REACTOR_DUBBO_LOADBALANCE` |
| `reactor.dubbo.connections` | `REACTOR_DUBBO_CONNECTIONS` |
| `reactor.dubbo.share-connections` | `REACTOR_DUBBO_SHARE_CONNECTIONS` |
| `reactor.dubbo.refer-thread-num` | `REACTOR_DUBBO_REFER_THREAD_NUM` |
| `reactor.dubbo.max-inflight` | `REACTOR_DUBBO_MAX_INFLIGHT` |
| `reactor.dubbo.max-response-bytes` | `REACTOR_DUBBO_MAX_RESPONSE_BYTES` |
| `reactor.dubbo.native-connections-per-endpoint` | `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT` |
| `reactor.dubbo.native-async-workers` | `REACTOR_DUBBO_NATIVE_ASYNC_WORKERS` |
| `reactor.dubbo.native-async-queue-capacity` | `REACTOR_DUBBO_NATIVE_ASYNC_QUEUE_CAPACITY` |

</details>

## Real-World Tuning Recipes

Tune one bottleneck at a time. Every change should be measured with successful RPS, p95/p99 latency,
503 rate, provider error rate, and RSS after idle.

| Use case | Starting settings | Why |
|----------|-------------------|-----|
| Static K8s Service DNS | <small><code>SAMPLE_DUBBO_DISCOVERY=static</code><br><code>REACTOR_DUBBO_PROVIDERS=customer-provider:20880,order-provider:20880</code><br><code>REACTOR_RUNTIME_PROFILE=micro-dubbo</code><br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2</code><br><code>REACTOR_DUBBO_MAX_INFLIGHT=8-16</code></small> | No ZooKeeper client/thread/class cost |
| Mandatory ZooKeeper, low traffic | <small><code>SAMPLE_DUBBO_DISCOVERY=zookeeper</code><br><code>REACTOR_RUNTIME_PROFILE=micro-dubbo</code><br><code>REACTOR_RUST_JNI_WORKERS=1</code><br><code>REACTOR_DUBBO_MAX_INFLIGHT=8</code><br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=1</code></small> | Small REST process<br>controlled 503 |
| Read-heavy dashboard JSON | <small><code>REACTOR_DUBBO_MAX_INFLIGHT=16-32</code><br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2-4</code><br><code>REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_MAX_CONCURRENT=16-64</code></small> | More useful 200 RPS when provider returns ready JSON bytes |
| DB-backed query | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=4-8</code><br><code>REACTOR_DUBBO_TIMEOUT_MS=800-1500</code><br><code>reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=50-150</code></small> | Prevents consumer from amplifying DB saturation |
| POST/PATCH/DELETE command | <small><code>REACTOR_DUBBO_RETRIES=0</code><br><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=4-8</code><br><code>reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=100-200</code></small> | Avoids duplicate execution and bounds write pressure |
| Large JSON response | `REACTOR_DUBBO_MAX_RESPONSE_BYTES`<br>`REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES`<br>`REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES` | Dubbo, HTTP and total in-flight caps must all allow payload |
| Higher concurrency, memory constrained | <small>First <code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT</code><br>then <code>REACTOR_DUBBO_MAX_INFLIGHT</code><br>last <code>REACTOR_RUST_JNI_WORKERS</code></small> | Connection reuse often helps before extra Java workers |
| Rolling restart, K8s DNS | <small><code>SAMPLE_DUBBO_DISCOVERY=static</code><br>correct readiness probe<br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2</code><br><code>REACTOR_DUBBO_TIMEOUT_MS=1000</code></small> | K8s removes unhealthy pods from Service endpoints |
| Rolling restart, ZooKeeper | <small><code>SAMPLE_DUBBO_DISCOVERY=zookeeper</code><br><code>REACTOR_DUBBO_REGISTRY_CHECK=false</code><br><code>REACTOR_DUBBO_CHECK=false</code><br><code>REACTOR_DUBBO_TIMEOUT_MS=1000</code></small> | Routes return bounded failures until discovery catches up |

### Recipe: Low-Memory Kubernetes Consumer With ZooKeeper

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_RUST_JNI_WORKERS
    value: "1"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "1"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1000"
  - name: REACTOR_DUBBO_RETRIES
    value: "0"
```

BEST for: services with occasional or moderate traffic where memory matters more than absorbing every
burst.
Expected behavior: under overload, some calls may return controlled `503`; RSS stays bounded.

### Recipe: Read-Heavy Catalog Endpoint

```yaml
env:
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "32"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "4"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_MAX_CONCURRENT
    value: "32"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_QUEUE_TIMEOUT_MS
    value: "100"
```

BEST for: provider returns ready JSON `byte[]` and the consumer forwards it with `RawResponse`.
ANTI-PATTERN: enabling native cache without a clear invalidation rule. Cache only deliberately
cacheable responses.

### Recipe: DB-Backed Provider Query

```yaml
env:
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1200"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_MAX_CONCURRENT
    value: "6"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_QUEUE_TIMEOUT_MS
    value: "150"
```

If the provider Hikari pool has `10` connections and there are two consumer pods, do not let each
consumer push `32` concurrent DB-backed RPCs. Start with `4-6` per pod and measure provider pool wait.

### Recipe: Command Endpoint Without Duplicate Writes

```yaml
env:
  - name: REACTOR_DUBBO_RETRIES
    value: "0"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1000"
  - name: REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_MAX_CONCURRENT
    value: "6"
  - name: REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_QUEUE_TIMEOUT_MS
    value: "150"
```

BEST for: create/update/delete operations that are not safely retryable.
If you need retries, add idempotency keys at the provider contract first.

### Recipe: Larger Provider JSON

```yaml
env:
  - name: REACTOR_DUBBO_MAX_RESPONSE_BYTES
    value: "16777216"
  - name: REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES
    value: "16777216"
  - name: REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES
    value: "33554432"
  - name: REACTOR_RUST_JSON_WRITER_RETAIN_MAX_BYTES
    value: "32768"
```

Use this only when the endpoint really returns larger JSON. If the response is a file or export,
prefer the framework file/static/streaming response path instead of carrying one large Java body.

## Running By Environment

There are two separate decisions:

```text
Maven profile = what dependencies go into the application classpath.
Runtime properties/env = how the consumer finds Dubbo providers.
```

Do not treat them as the same thing. Kubernetes has two valid paths. If the provider can be
represented by a K8s Service DNS, static mode is smaller and more explicit. If your discovery
contract is ZooKeeper, build/run with the `zookeeper-discovery` Maven profile and set
`SAMPLE_DUBBO_DISCOVERY=zookeeper`.

| Environment | Use this Maven profile | Discovery mode | Provider address source |
|-------------|------------------------|----------------|-------------------------|
| Local standalone JVM, provider fixed on one address | default `full-dubbo-consumer` | `static` | `REACTOR_DUBBO_PROVIDERS=127.0.0.1:20880` |
| Local standalone JVM, provider must be found from ZooKeeper | `zookeeper-discovery` | `zookeeper` | `REACTOR_DUBBO_REGISTRY_ADDRESS=zookeeper://127.0.0.1:2181` |
| Docker on one Docker network | `zookeeper-discovery` if discovery is required, otherwise default/full | `zookeeper` or `static` | Docker service name, for example `zookeeper:2181` or `provider:20880` |
| Kubernetes, provider Service DNS is stable | default/full or `native-static-consumer` | `static` | `REACTOR_DUBBO_PROVIDERS=rest-sample-dubbo-provider:20880` |
| Kubernetes, Dubbo registry/governance is mandatory | `zookeeper-discovery` | `zookeeper` | Kubernetes DNS name of ZooKeeper, for example `zookeeper-client.platform.svc.cluster.local:2181` |

### Standalone JVM

Use this when you run the provider and consumer directly from Maven or from your IDE.

Static provider mode is the lightest standalone path:

```powershell
$env:SAMPLE_DUBBO_DISCOVERY="static"
$env:REACTOR_DUBBO_PROVIDERS="127.0.0.1:20880"
mvn -q exec:java
```

ZooKeeper discovery mode is the correct standalone test when you want the same provider discovery
behavior as Kubernetes:

```powershell
$env:SAMPLE_DUBBO_DISCOVERY="zookeeper"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://127.0.0.1:2181"
mvn -q -Pzookeeper-discovery exec:java
```

BEST: use the ZooKeeper profile during local testing if production will use ZooKeeper.  
ACCEPTABLE: use static provider mode for quick local smoke tests.  
ANTI-PATTERN: testing only static provider mode and then deploying ZooKeeper discovery for the first
time in Kubernetes.

### Docker

The sample does not require Spring Boot packaging. If you want a plain container image, copy compiled
classes and runtime dependencies into the image:

```powershell
mvn -q -Pzookeeper-discovery -DskipTests package dependency:copy-dependencies `
  -DincludeScope=runtime `
  -DoutputDirectory=target/dependency
```

Example Dockerfile:

```dockerfile
FROM ibm-semeru-runtimes:open-21-jre
WORKDIR /app
COPY target/classes /app/classes
COPY target/dependency /app/dependency
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-cp", "/app/classes:/app/dependency/*", "com.reactor.sample.dubbo.consumer.app.RestSampleDubboConsumerApplication"]
```

Build and run on a Docker network:

```powershell
docker network create reactor-dubbo
docker build -t rest-sample-dubbo-consumer:zk .

docker run --rm --name rest-sample-dubbo-consumer `
  --network reactor-dubbo `
  -p 8080:8080 `
  -e SAMPLE_DUBBO_DISCOVERY=zookeeper `
  -e REACTOR_DUBBO_REGISTRY_ADDRESS=zookeeper://zookeeper:2181 `
  -e REACTOR_RUNTIME_PROFILE=micro-dubbo `
  -e REACTOR_DUBBO_RUNTIME_PROFILE=micro-dubbo `
  rest-sample-dubbo-consumer:zk
```

If you run static provider mode in Docker, use the provider container or service name, not
`127.0.0.1`, because `127.0.0.1` points to the consumer container itself:

```powershell
docker run --rm --name rest-sample-dubbo-consumer `
  --network reactor-dubbo `
  -p 8080:8080 `
  -e SAMPLE_DUBBO_DISCOVERY=static `
  -e REACTOR_DUBBO_PROVIDERS=rest-sample-dubbo-provider:20880 `
  rest-sample-dubbo-consumer:zk
```

### OpenJ9 21 jlink Minimum Image

Use this when the sample must run as a small, non-root OpenJ9 container instead of carrying a full
JRE image. The provided `Dockerfile.jlink` files create a custom Java runtime with only the modules
needed by this consumer. This reduces image/runtime surface, but it does not remove application
features by itself; dependency profile selection still matters.

Two build modes are available:

| Build mode | Command | When to use |
|------------|---------|-------------|
| Workspace build | <small><code>docker build -f rest-sample-dubbo-consumer/Dockerfile.jlink.workspace -t rest-sample-dubbo-consumer:jlink .</code></small> | Use from `rust-spring-performance` root. It installs local `rust-java-rest` and `java-rust-dubbo` inside the image build, so no GitHub Packages token is needed. |
| Standalone repo build | <small><code>docker build --secret id=maven_settings,src=$env:USERPROFILE\.m2\settings.xml -f Dockerfile.jlink -t rest-sample-dubbo-consumer:jlink .</code></small> | Use when this sample is cloned alone. The Maven settings file must contain a valid GitHub Packages token for private package download. |

Static provider smoke on a Docker network:

```powershell
docker network create reactor-jlink-smoke

docker run --rm --name rest-sample-dubbo-consumer `
  --network reactor-jlink-smoke `
  -p 8080:8080 `
  -e SAMPLE_DUBBO_DISCOVERY=static `
  -e REACTOR_DUBBO_PROVIDERS=rest-sample-dubbo-provider:20880 `
  -e REACTOR_RUNTIME_PROFILE=micro-dubbo `
  -e REACTOR_DUBBO_RUNTIME_PROFILE=micro-dubbo `
  rest-sample-dubbo-consumer:jlink
```

Kubernetes static Service DNS example:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: JAVA_TOOL_OPTIONS
    value: "-Xms24m -Xmx96m -Xss192k -Xgc:threads=1 -XX:ActiveProcessorCount=1 -Xtune:virtualized"
```

Notes:

- Do not remove `java.desktop` from `JAVA_MODULES`. Dubbo/Hessian typed DTO support needs Java Beans classes from that module.
- The image runs as a non-root `app` user and keeps `/app/.reactor/native` writable for Rust native `.so` extraction.
- Local smoke in this workspace observed about `38.8 MiB` consumer RSS with static provider mode. Treat it as a smoke reference, not a capacity guarantee; real RSS depends on endpoints, Dubbo profile, and traffic.
- If you switch to `zookeeper-discovery`, rebuild with that Maven profile and expect extra ZooKeeper classes/threads.

### Kubernetes

Kubernetes has two valid models. Pick based on provider discovery requirements; running in
Kubernetes does not automatically make ZooKeeper mandatory.

#### Static Service DNS Mode

If the provider is behind a Kubernetes Service and you do not use Dubbo registry/governance, this is
the smallest consumer shape:

```text
Consumer pod -> rest-sample-dubbo-provider Service DNS -> provider pod endpoints
```

Use a normal Service for the provider:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: rest-sample-dubbo-provider
spec:
  selector:
    app: rest-sample-dubbo-provider
  sessionAffinity: None
  ports:
    - name: dubbo
      port: 20880
      targetPort: 20880
```

Consumer Deployment env example:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "2"
```

If different Dubbo interfaces are managed through different Service DNS names, list them in the same
property:

```properties
reactor.dubbo.providers=customer-provider:20880,order-provider:20880
```

In this model, the Kubernetes Service distributes TCP connections across pod replicas. It does not
load balance each Dubbo RPC request individually. If provider readiness is wrong, the Service can
send connections to a pod that is not actually ready; make the provider readiness probe turn ready
only when the RPC port can serve traffic.

#### ZooKeeper Discovery Mode

If your discovery contract is ZooKeeper, the intended flow is:

```text
Consumer pod -> ZooKeeper Service -> provider URL from registry -> Dubbo provider pod/service
```

Build the image with the `zookeeper-discovery` Maven profile. Setting only
`SAMPLE_DUBBO_DISCOVERY=zookeeper` at runtime is not enough if the image was built without the
ZooKeeper dependency.

Minimal ZooKeeper discovery Deployment shape:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rest-sample-dubbo-consumer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: rest-sample-dubbo-consumer
  template:
    metadata:
      labels:
        app: rest-sample-dubbo-consumer
    spec:
      containers:
        - name: app
          image: registry.example.com/rest-sample-dubbo-consumer:0.1.0-zk
          ports:
            - containerPort: 8080
          env:
            - name: SAMPLE_DUBBO_DISCOVERY
              value: "zookeeper"
            - name: REACTOR_DUBBO_REGISTRY_ADDRESS
              value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
            - name: REACTOR_RUNTIME_PROFILE
              value: "micro-dubbo"
            - name: REACTOR_DUBBO_RUNTIME_PROFILE
              value: "micro-dubbo"
            - name: REACTOR_DUBBO_MAX_INFLIGHT
              value: "8"
            - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
              value: "2"
          readinessProbe:
            httpGet:
              path: /app/health
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /app/health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 10
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "500m"
              memory: "256Mi"
```

```yaml
apiVersion: v1
kind: Service
metadata:
  name: rest-sample-dubbo-consumer
spec:
  selector:
    app: rest-sample-dubbo-consumer
  ports:
    - name: http
      port: 8080
      targetPort: 8080
```

Production notes for Kubernetes:

- Static Service DNS mode can start with a smaller memory budget because the consumer does not load a Java ZooKeeper client. Still, do not lower limits aggressively before an idle RSS and p99 test in your own image.
- Start with `160Mi-256Mi` memory limit when ZooKeeper discovery is enabled; then lower only after an idle RSS and p99 test in your own image.
- Keep `reactor.dubbo.max-inflight` bounded. Raising it blindly can protect RPS but damage p99 and provider stability.
- Keep `reactor.dubbo.retries=0` for low-latency APIs unless the operation is idempotent and retry-safe.
- `/app/health` is a process health endpoint. If you want readiness to depend on provider availability, add a separate readiness route; do not make liveness depend on Dubbo provider state.
- In static Service DNS mode, provider rolling restart recovery depends on Kubernetes readiness and EndpointSlice behavior. With a correct readiness probe, the Service removes unhealthy provider pods from its endpoint list.
- During provider rolling restarts, ZooKeeper should publish the new provider URL and the consumer should reconnect through discovery. If this does not happen, inspect provider registration under `/dubbo/{interface}/providers`.

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
| `GET /api/v1/catalog/nested` | Calls `NestedCatalogService.getNestedCatalogJson()` and forwards provider JSON bytes with `RawResponse`. |
| `GET /api/v1/catalog/title` | Calls `String getCatalogTitle()` and wraps the scalar value as JSON. |
| `GET /api/v1/catalog/count` | Calls `int countCatalogItems()` and returns a small scalar JSON response. |
| `GET /api/v1/catalog/info` | Calls `CatalogInfo getCatalogInfo()` and returns a typed record DTO shape. |
| `GET /api/v1/catalog/items?limit=2` | Calls `List<CatalogItem> listFeaturedItems(int limit)` with bounded query input. |
| `GET /api/v1/catalog/attributes` | Calls `Map<String,String> getCatalogAttributes()` for small metadata. |
| `GET /api/v1/customers/db` | Calls `CustomerQueryService.getDatabaseCustomersJson()` and forwards PostgreSQL-backed customer JSON bytes. |
| `GET /api/v1/customers/db/stats` | Calls `CustomerStats getCustomerStats()` and returns aggregate counts. |
| `GET /api/v1/customers/db/{id}` | Calls `CustomerSummary getCustomer(long id)` and returns 404 when provider returns null. |
| `GET /api/v1/customers/db/by-segment?segment=pilot&limit=10` | Calls `List<CustomerSummary> findCustomersBySegment(...)` with a bounded result size. |
| `GET /api/v1/customers/{id}/exists` | Calls `boolean customerExists(long id)`. |
| `GET /api/v1/customers/{id}/display-name` | Calls `String getCustomerDisplayName(long id)`. |
| `GET /api/v1/catalog/db/customers` | Compatibility alias that also calls `CustomerQueryService`. |
| `POST /api/v1/customers` | Sends compact command JSON bytes to `CustomerCommandService.createCustomer(byte[])`. |
| `POST /api/v1/customers/typed` | Parses REST body as `CreateCustomerCommand`, calls typed Dubbo command, returns `CustomerMutationResult` JSON. |
| `PATCH /api/v1/customers/{id}/segment` | Changes customer segment through a bounded DB command method. |
| `PATCH /api/v1/customers/{id}/status` | Changes customer lifecycle status through a bounded DB command method. |
| `PATCH /api/v1/customers/{id}/status/typed?status=passive&requestId=demo-1` | Calls `patchCustomerStatusTyped(long, String, String)`. |
| `DELETE /api/v1/customers/{id}` | Deletes a customer through a bounded DB command method. |
| `GET /api/v1/catalog/dubbo-metrics` | Shows native Dubbo client metrics. |

Quick typed Dubbo smoke calls:

```bash
curl http://localhost:8080/api/v1/catalog/title
curl http://localhost:8080/api/v1/catalog/count
curl http://localhost:8080/api/v1/catalog/info
curl "http://localhost:8080/api/v1/catalog/items?limit=2"
curl http://localhost:8080/api/v1/catalog/attributes

curl http://localhost:8080/api/v1/customers/db/stats
curl http://localhost:8080/api/v1/customers/db/1
curl "http://localhost:8080/api/v1/customers/db/by-segment?segment=pilot&limit=10"
curl http://localhost:8080/api/v1/customers/1/exists
curl http://localhost:8080/api/v1/customers/1/display-name

curl -X POST http://localhost:8080/api/v1/customers/typed \
  -H "Content-Type: application/json" \
  -d '{"customerNo":"CUST-9001","fullName":"Typed Demo Customer","segment":"pilot","email":"typed@example.com","requestId":"typed-001"}'

curl -X PATCH "http://localhost:8080/api/v1/customers/1/status/typed?status=passive&requestId=status-001"
```

## Copy/Paste Use Case Cookbook

This section is intentionally practical. If you are performance-first, start from the property
tables. If you are looking for a coding pattern, copy the Java snippets and keep the same boundaries:
REST handler in Java, Dubbo adapter in Java, HTTP I/O in Rust, DB mutation in the provider.

| Real-world need | Use this pattern | Why |
|-----------------|------------------|-----|
| Product catalog, dashboard config, branch list | `GET` + provider returns JSON `byte[]` + `RawResponse.json(bytes)` | Lowest allocation path for read-heavy pass-through JSON. |
| Small typed lookup | `GET` + provider returns `record`, `String`, `boolean`, or `int` | Useful when the consumer must inspect fields or branch on the value. |
| Small paged search | `GET` + provider returns `List<record>` with a strict `limit` | Clear DTO contract for small pages; do not use for unbounded exports. |
| Customer list from DB provider | `GET` + provider owns Hikari/SQL + consumer forwards bytes | REST process stays small; DB pool stays outside the Rust-Java HTTP process. |
| Create customer/order/payment command | `POST` + compact JSON command bytes | Write command reaches provider without building a large consumer DTO graph. |
| Typed customer command | `POST` + REST record body + Dubbo record command | Cleaner business contract; higher allocation than byte pass-through. |
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

## Endpoint Pattern Catalog

This section adds copy/paste handler patterns beyond the concrete sample endpoints. The goal is to
make it clear which request body and response type should be used for each HTTP verb.

| Verb | Pattern | Use when | Overhead | Note |
|------|---------|----------|----------|------|
| `GET` | No body + path/query<br>`RawResponse.json(bytes)` | Provider/cache already has JSON | Very low<br>no parse/serialize | Cheapest read path |
| `GET` | No body + path/query<br>Java `record` | Small status/lookup | Low<br>response serialize only | Measure p99 at high traffic |
| `GET` | Query filters<br>`List<record>` | Small paged list | Medium/high<br>list + item allocation | Pagination and limit required |
| `POST` | `byte[]` -> `RawResponse.json(bytes)` | Command pass-through | Low<br>no request DTO | Provider validates |
| `POST` | `byte[]` -> `RawResponse.bytes(...)` | Binary upload/echo | Low<br>no JSON | Use stream/file for large binary |
| `POST` | `byte[]` -> Java `record` | Receipt/id without parsing request | Low/medium | Response is serialized |
| `POST` | `record` -> `record` | Typed validation/business | Medium<br>parse + serialize | Not as cheap as byte[] hot path |
| `POST` | `record` -> `List<record>` | Body-driven search | High | Max result required |
| `PUT` | `record` -> `record`/error | Full update/replace | Medium | Body limit + route admission |
| `PATCH` | `record` -> `record` | Typed partial update | Low/medium | Low command concurrency |
| `PATCH` | `byte[]` -> `RawResponse.json(bytes)` | Partial command pass-through | Low<br>no parse | Validation in provider |
| `DELETE` | No body -> `204` | Delete without response body | Lowest | Audit via header/log/outbox |
| `DELETE` | Optional `byte[]` -> JSON/error | Audit reason/requestId | Low | Keep audit JSON small |

Notes:

- For real HTTP raw byte responses, prefer `RawResponse.bytes(...)` over `ResponseEntity<byte[]>`.
- For ready JSON byte responses, use `RawResponse.json(bytes)`.
- For normal HTTP JSON DTOs, use Java `record`; do not make POJO classes the default DTO shape.
- Since `List<CustomerDto>.class` does not exist in Java, list response examples use `responseType = List.class`; the method return type should still be `ResponseEntity<List<CustomerView>>`.
- If success and error bodies use different record types, the examples use `ResponseEntity<?>` and `responseType = Object.class`. For stricter production contracts, use a shared `ApiResult` envelope record.

### Copy/Paste: All Verb And Type Combinations

Put the following class into your own handler package and change the base path as needed. The
in-memory map exists only so the example can run by itself; in a real project, replace it with a
service or Dubbo client call.

```java
package com.example.orders;

import com.reactor.rust.annotations.DeleteMapping;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.MaxRequestBodySize;
import com.reactor.rust.annotations.PatchMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.PostMapping;
import com.reactor.rust.annotations.PutMapping;
import com.reactor.rust.annotations.RequestBody;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequestMapping("/api/v1/orders-demo")
public final class OrderEndpointExamples {

    private final AtomicLong ids = new AtomicLong(1000);
    private final Map<Long, OrderView> orders = new ConcurrentHashMap<>();

    public record OrderLine(String sku, int quantity) {}

    public record OrderView(long id, String customerNo, String status, List<OrderLine> lines) {}

    public record CreateOrderRequest(String requestId, String customerNo, List<OrderLine> lines) {}

    public record ReplaceOrderRequest(String customerNo, String status, List<OrderLine> lines) {}

    public record PatchStatusRequest(String requestId, String status, String reason) {}

    public record SearchOrdersRequest(String customerNo, String status) {}

    public record CreateReceipt(long id, String status) {}

    public record StatusResponse(String status, long orderCount) {}

    public record ErrorBody(String code, String message) {}

    @GetMapping(value = "/status", responseType = StatusResponse.class)
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(new StatusResponse("UP", orders.size()));
    }

    @GetMapping(value = "/{id}", responseType = Object.class)
    public ResponseEntity<?> getById(@PathVariable("id") long id) {
        OrderView order = orders.get(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("order_not_found", "Order " + id + " was not found"));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping(value = "", responseType = List.class)
    @RouteAdmission(maxConcurrent = 32, queueTimeoutMs = 100)
    public ResponseEntity<List<OrderView>> list(
            @RequestParam(value = "status", required = false, defaultValue = "") String status) {
        List<OrderView> result = orders.values().stream()
                .filter(order -> status.isBlank() || order.status().equalsIgnoreCase(status))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/raw-dashboard", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> rawDashboard() {
        byte[] json = "{\"screen\":\"orders\",\"enabled\":true}".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok(RawResponse.json(json));
    }

    @PostMapping(value = "/raw-json", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(32768)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public ResponseEntity<RawResponse> postByteArrayReturnJsonBytes(@RequestBody byte[] body) {
        if (body.length == 0) {
            return ResponseEntity.badRequest(errorJson("empty_body", "JSON body is required"));
        }
        return ResponseEntity.created(RawResponse.json(body));
    }

    @PostMapping(value = "/binary-echo", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(65536)
    public ResponseEntity<RawResponse> postByteArrayReturnBinary(@RequestBody byte[] body) {
        return ResponseEntity.ok(RawResponse.bytes(body, "application/octet-stream"));
    }

    @PostMapping(value = "/raw-to-record", requestType = byte[].class, responseType = Object.class)
    @MaxRequestBodySize(32768)
    public ResponseEntity<?> postByteArrayReturnRecord(@RequestBody byte[] body) {
        if (body.length == 0) {
            return ResponseEntity.badRequest(new ErrorBody("empty_body", "Body is required"));
        }
        long id = ids.incrementAndGet();
        return ResponseEntity.created(new CreateReceipt(id, "accepted"));
    }

    @PostMapping(value = "", requestType = CreateOrderRequest.class, responseType = Object.class)
    @MaxRequestBodySize(32768)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public ResponseEntity<?> postRecordReturnRecord(@RequestBody CreateOrderRequest request) {
        if (request.customerNo() == null || request.customerNo().isBlank()) {
            return ResponseEntity.badRequest(new ErrorBody("customer_required", "customerNo is required"));
        }
        long id = ids.incrementAndGet();
        OrderView created = new OrderView(id, request.customerNo(), "created", request.lines());
        orders.put(id, created);
        return ResponseEntity.created(created);
    }

    @PostMapping(value = "/search", requestType = SearchOrdersRequest.class, responseType = List.class)
    public ResponseEntity<List<OrderView>> postRecordReturnList(@RequestBody SearchOrdersRequest request) {
        List<OrderView> result = orders.values().stream()
                .filter(order -> request.customerNo() == null || request.customerNo().equals(order.customerNo()))
                .filter(order -> request.status() == null || request.status().equalsIgnoreCase(order.status()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping(value = "/{id}", requestType = ReplaceOrderRequest.class, responseType = Object.class)
    @MaxRequestBodySize(32768)
    public ResponseEntity<?> putRecordReturnRecord(
            @PathVariable("id") long id,
            @RequestBody ReplaceOrderRequest request) {
        if (!orders.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("order_not_found", "Order " + id + " was not found"));
        }
        OrderView replaced = new OrderView(id, request.customerNo(), request.status(), request.lines());
        orders.put(id, replaced);
        return ResponseEntity.ok(replaced);
    }

    @PatchMapping(value = "/{id}/status", requestType = PatchStatusRequest.class, responseType = Object.class)
    @MaxRequestBodySize(8192)
    public ResponseEntity<?> patchRecordReturnRecord(
            @PathVariable("id") long id,
            @RequestBody PatchStatusRequest request) {
        OrderView current = orders.get(id);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("order_not_found", "Order " + id + " was not found"));
        }
        OrderView updated = new OrderView(id, current.customerNo(), request.status(), current.lines());
        orders.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping(value = "/{id}", responseType = Void.class)
    public ResponseEntity<Void> deleteNoBody(@PathVariable("id") long id) {
        orders.remove(id);
        return ResponseEntity.noContent();
    }

    @DeleteMapping(value = "/{id}/audit", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(8192)
    public ResponseEntity<RawResponse> deleteWithOptionalByteBody(
            @PathVariable("id") long id,
            @RequestBody(required = false) byte[] auditBody) {
        orders.remove(id);
        byte[] audit = auditBody == null ? "{}".getBytes(StandardCharsets.UTF_8) : auditBody;
        return ResponseEntity.ok(RawResponse.json(audit));
    }

    private static RawResponse errorJson(String code, String message) {
        String json = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return RawResponse.json(json.getBytes(StandardCharsets.UTF_8));
    }
}
```

Quick PowerShell checks:

```powershell
curl http://127.0.0.1:8080/api/v1/orders-demo/status
curl "http://127.0.0.1:8080/api/v1/orders-demo?status=created"
curl http://127.0.0.1:8080/api/v1/orders-demo/raw-dashboard

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/raw-json `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r1\",\"raw\":true}"

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/binary-echo `
  -H "Content-Type: application/octet-stream" `
  --data-binary "binary-payload"

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r2\",\"customerNo\":\"CUST-1\",\"lines\":[{\"sku\":\"SKU-1\",\"quantity\":2}]}"

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/search `
  -H "Content-Type: application/json" `
  -d "{\"customerNo\":\"CUST-1\",\"status\":\"created\"}"

curl -X PUT http://127.0.0.1:8080/api/v1/orders-demo/1001 `
  -H "Content-Type: application/json" `
  -d "{\"customerNo\":\"CUST-1\",\"status\":\"paid\",\"lines\":[{\"sku\":\"SKU-1\",\"quantity\":2}]}"

curl -X PATCH http://127.0.0.1:8080/api/v1/orders-demo/1001/status `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r3\",\"status\":\"cancelled\",\"reason\":\"customer request\"}"

curl -X DELETE http://127.0.0.1:8080/api/v1/orders-demo/1001

curl -X DELETE http://127.0.0.1:8080/api/v1/orders-demo/1001/audit `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r4\",\"reason\":\"audit cleanup\"}"
```

Error scenario examples:

```powershell
# Returns 404 ErrorBody
curl http://127.0.0.1:8080/api/v1/orders-demo/999999

# Returns 400 ErrorBody
curl -X POST http://127.0.0.1:8080/api/v1/orders-demo `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"bad\",\"lines\":[]}"

# Returns 400 RawResponse JSON error
curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/raw-json `
  -H "Content-Type: application/json" `
  -d ""
```

Production decision: for high-traffic pass-through endpoints, `byte[] + RawResponse` is the lowest
allocation path. If the consumer must make a business decision from the request, use `record request
-> record response`. Do not increase concurrency for large lists or large JSON responses without
tuning route budget and in-flight byte limits separately.

### Profile And Property Selection

| User problem | Start with | Key setting |
|--------------|------------|-------------|
| Smallest pod, occasional traffic | `micro-dubbo` + static provider | <small><code>reactor.rust.jni.workers=1</code><br><code>reactor.dubbo.native-async-workers=1</code><br><code>reactor.rust.response-pool.small-capacity=8</code></small> |
| More successful writes at c256 | `micro-dubbo` + route tuning | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent</code><br>measure RSS/p99/503</small> |
| Dynamic provider discovery | `micro-dubbo` + `zookeeper-discovery` | `SAMPLE_DUBBO_DISCOVERY=zookeeper`<br>expect small RSS increase |
| Provider DB is slow | Keep consumer bounded | `sample.db.maximum-pool-size`<br>provider method limits<br>PostgreSQL latency |
| Best-practice template | Copy this sample shape | `handler -> client -> interface -> provider -> repository` |

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
