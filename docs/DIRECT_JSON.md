# Forward A Provider List Without Building Consumer DTOs

[English](DIRECT_JSON.md) | [Turkish](DIRECT_JSON.tr.md)

The optional `direct-json-consumer` source profile demonstrates this flow:

```text
GET /api/v1/catalog/rows?count=32
  -> Java handler -> generated typed Dubbo client
  -> Rust TCP -> provider's Java service
  -> native Hessian response -> generated JSON writer -> Rust HTTP response
```

It does not load Spring, Netty or HessianLite in the consumer. Existing profiles
and their customer/command routes remain unchanged. This profile exposes only
the catalog examples below. These examples are present in this `0.8.0` source
checkout. Build matching modules and native assets together; this guide does not
establish published package availability.

## Build And Start

Build/install the shared model, utility, runtime and optional REST adapter first.
The adapter guide is in `java-rust-dubbo-spring-boot/java-rust-dubbo-rest-adapter`.

```sh
mvn -B -ntp -Pdirect-json-consumer clean package
mvn -B -ntp -Pdirect-json-consumer dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=lib
java -Drust.lib.path=/opt/native/librust_hyper.so \
  -Dreactor.dubbo.native.path=/opt/native/librust_dubbo.so \
  -Dsample.direct-json.providers=provider:20880 \
  -Dreactor.runtime.profile=micro-rest \
  -Dreactor.dubbo.direct-json.pool-slots=2 \
  -cp 'target-direct-json/rest-sample-dubbo-consumer-0.8.0.jar:lib/*' \
  com.reactor.sample.dubbo.consumer.directjson.DirectJsonConsumerApplication
```

`lib` must contain the runtime dependency closure, not annotation processors.
Use `;` instead of `:` for the Windows classpath and select matching DLLs.
The native Dubbo library is separate from the REST library. ABI numbers alone
do not identify all additive capabilities; use the matching source distribution.

```sh
curl 'http://localhost:8080/api/v1/catalog/rows?count=32'
curl 'http://localhost:8080/api/v1/catalog/rows?count=512'
curl 'http://localhost:8080/api/v1/catalog/literal'
curl 'http://localhost:8080/api/v1/catalog/health'
```

Rows include nested records, Turkish text, emoji, nullable fields and finite
floating-point values. Payload/response byte limits can reject a large response
before the row-count limit is reached. Data is never silently truncated.

### How `count` Is Bound

The handler uses the existing legacy `@DirectQueryInt` binding with default `32`
and range `0..10000`. It normalizes input; it is not a strict HTTP 400 validator.

| Query value | Value passed to the handler |
| --- | --- |
| Missing, empty or `abc` | `32` |
| `-1` | `0` |
| `10001` | `10000` |
| `12oops` | `32` |
| `10001oops` | `10000`: the legacy parser returns at the bound before reading the suffix. |

At route registration, reversed bounds are ordered and the configured default is
clamped into that range. If malformed or out-of-range values must be rejected,
use an explicitly validated parameter contract rather than relying on this annotation.

## Which Setting Changes What?

| Property | Default | Purpose |
| --- | --- | --- |
| `sample.direct-json.providers` | `127.0.0.1:20880` | Static provider address. In Kubernetes use its Service DNS. |
| `sample.direct-json.connections` | `2` | Bounded persistent Dubbo connections to that endpoint. |
| `sample.direct-json.timeout-ms` | `3000` | RPC deadline; a timed-out call is not replayed. |
| `sample.direct-json.enabled` | `true` | Direct conversion. `false` is the explicit materialized-DTO comparison path. |
| `reactor.dubbo.direct-json.pool-slots` | runtime default `16` | Concurrent JSON writers. Start with `2` for two JNI workers. |
| `reactor.json.swar` | `false` at build time | Generates word-sized constant field writes for `/literal`. Clean-rebuild with `-Dreactor.json.swar=true`. |

`sample.direct-json.timeout-ms` is supplied to the explicitly constructed client
and applies to its calls, not to one annotated method. The handler's `read()` path
is synchronous; direct conversion avoids row DTOs but does not make this RPC async
or select `tokio-demux`. REST async response budgets are separate from this option.

The handler still owns authorization and business decisions. Choose normal DTO
consumption when Java must inspect, validate or change each returned row. Direct
conversion does not invoke consumer record constructors, so their validation and
normalization do not run. Use the ordinary DTO path when those rules are required.
Direct conversion also does not cache the provider response: every `/rows` request
calls the provider.

## Memory And Formatting Limits

Avoiding row DTOs does not mean that no response data is retained. One complete,
size-bounded Hessian response remains in native memory while it is converted. The
adapter writes JSON into REST's bounded response buffer and supports its capacity
retry protocol. It is not an unbounded HTTP chunk stream or a row-by-row network
cursor. Keep payload, JSON-output and total in-flight memory limits in place; use
a dedicated file/stream design for larger exports.

The matching native response store has a shared ceiling of 256 retained handles
and 32 MiB. This is not a preallocated slab or a total RSS cap. At capacity the
RPC result fails without replaying the call. The new direct path requires native
response policy `1` as well as ABI `3`; use the matching SO and JAR together.

Some common finite doubles use primitive formatting, but other finite values fall
back to the JDK's `Double.toString` formatter and can allocate. Removing the row DTO
graph is not a universal zero-allocation guarantee for every value or response.

The focused EmaTech check used four fresh process pairs under 1 CPU/128 MiB and
passed all nine control/32-row/512-row RPS/p99/memory cells. For 512 rows, direct
projection reduced consumer RSS by about 7.5–8.5 MiB and increased useful RPS by
about 26–30% against materializing the same rows. Each call still reached the
provider. These are measured sample results, not a guarantee for every schema or
deployment. Larger exports still need a separate streaming design.

## Optional SWAR Literal Writes

`/api/v1/catalog/literal` returns the annotated `SwarPayload` record; it does not
perform the `/rows` Hessian conversion. SWAR is off by default. To opt in, clean-build
this profile with `mvn -B -ntp -Pdirect-json-consumer -Dreactor.json.swar=true clean package`.
Clean-rebuild with `-Dreactor.json.swar=false` to return to the byte-table path. Changing a JVM property at
startup cannot change an already generated writer.

For 8..16-byte UTF-8 field prefixes, generation emits one fused
`writePrebakedLiteral(first, last, length)` call and retains no tail bytes. Longer
prefixes use full words plus 0..7-byte tails; shorter ones keep the byte-table path.
`JsonBufferWriter` writes full words with
absolute `ByteBuffer.putLong(index, word)` and reverses the word with
`Long.reverseBytes` for big-endian buffers. It does not use a VarHandle ByteBuffer
view, and preserves position, limit and byte order. Short output buffers still
return the negative exact required length for bounded serialization retry.

The recorded EmaTech comparison used four fresh process pairs and passed all six
control/literal RPS/p99/+3 MiB cells under 1 CPU/128 MiB. These historical results
do not verify the current deployment or platform packaging.
Literal RPS changed by -0.48%..+1.59%; steady RSS increased by 0.54..1.38 MiB.
This is not a memory reduction or a recommendation to enable SWAR everywhere.

`/api/v1/catalog/diagnostics` is intended for controlled diagnostics. It exposes
heap/native counters; restrict it before exposing a production service publicly.
Constructor counters are normally disabled. Enable
`-Dsample.stream-catalog.count-constructors=true` only for a functional diagnostic,
not a throughput comparison.

For ownership, buffer retry and size limits, read the optional adapter guide.

## Choose The Response Path

The three `reactor.dubbo.direct-json.*` writer settings use JVM `-D` options or
uppercase environment variables. They are not automatically read from
`rust-spring.properties`. Use the launch example or the optional adapter guide.

| Your need | Choose | Consumer-side work |
| --- | --- | --- |
| Provider already returns JSON bytes | Existing native response handle | Avoid DTO decoding and JSON rewriting. |
| Provider returns typed rows; forward them unchanged | Direct JSON projection | Read Hessian and write JSON; no row DTO graph. |
| Validate constructors, change values, sort or combine rows | Ordinary typed DTO/list | Java creates and processes values explicitly. |
| Very large file or export | Dedicated file/stream design | Do not raise this buffer limit without an in-flight memory budget. |
