# rest-sample-dubbo-consumer 0.8.2

[English](RELEASE_NOTES_v0.8.2.md) | [Türkçe](RELEASE_NOTES_v0.8.2.tr.md)

Coordinated update: November 1, 2026.

## Dependencies

Use platform parent `com.reactor:rust-java-platform-parent:4.8.2`. Entries for the
existing `full-dubbo-consumer` profile/POM; keep its REST starter and compiler setup:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>java-rust-dubbo</artifactId>
  <version>0.9.2</version>
</dependency>
<dependency>
  <groupId>com.reactor.sample</groupId>
  <artifactId>rest-sample-utility</artifactId>
  <version>0.5.2</version>
</dependency>
```

Utility brings model `0.5.2`; update the provider contracts together. The independent
Spring adapter/annotations/codegen used by `direct-json-consumer` stay at `0.8.1`.
See [Maven access](../README.md#maven-package-access). Rebuild the normal profile with
`mvn -B -ntp -DskipTests -Pfull-dubbo-consumer clean package`; this is not qualification.

## Existing DB/Dubbo Examples

[CustomerHandler](../src/main/java/com/reactor/sample/dubbo/consumer/handler/CustomerHandler.java)
uses `DubboResponses.ok`/`created` and a final `replyAsync` boundary after domain-error
mapping. It preserves 200/201, existing 503 codes and late native-response cleanup
after cancellation. The four existing keyed PATCH/DELETE methods use
`@KeyAdmission(bean="customerCommandKeyAdmission", key="customerId", onRejected=...)`.
The existing bean supplies bounded process-local admission; the key is the Java
parameter bound to HTTP `id`, not a new HTTP parameter. Provider DB pools, method
caps and transaction/idempotency rules remain independent. No new API is required.

Readiness selection was first introduced in `0.8.1`; the current run command uses
the `0.8.2` JAR. Query-only readiness is not access control. The
[Compose native overlay](../README.md#compose) uses `native-static-consumer` for
catalog only, not the full customer/DB contract.

## Upgrade And Evidence

No JNI ABI, public HTTP contract or configuration-key migration is required.
Use the native files packaged with each selected dependency; Spring Dubbo ABI `3`
is unchanged. These notes do not establish publication or full qualification.
Use the exact candidate's receipt; hosted compatibility does not replace EmaTech
performance/soak evidence or imply an RPS/p99/memory PASS.
