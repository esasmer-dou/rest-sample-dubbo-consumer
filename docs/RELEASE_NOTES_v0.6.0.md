# rest-sample-dubbo-consumer 0.6.0

`0.6.0` is the reference Rust-Java REST and native Dubbo consumer for the aligned `4.2.0` platform.

## What Changed

- Uses `rust-java-platform-parent:4.2.0`, `java-rust-dubbo:0.7.0`,
  `rest-sample-utility:0.4.0`, and `rust-sample-model:0.4.0`.
- Full, ZooKeeper, and native-static profiles compile only their required application surface.
- Generated conditional Dubbo clients replace handwritten catalog-only factories and module wiring.
- Static discovery can be declared as static-only and fails early if ZooKeeper is configured by
  mistake.
- REST handlers, validation, orchestration, and business error mapping remain Java code.

## Run Native Static

```powershell
mvn -Pnative-static-consumer clean verify
mvn -Pnative-static-consumer exec:java
```

Use the documented `zookeeper-discovery` profile only when provider discovery actually requires
ZooKeeper.
