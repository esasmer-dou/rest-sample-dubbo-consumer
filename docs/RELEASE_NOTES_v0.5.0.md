# rest-sample-dubbo-consumer 0.5.0

`0.5.0` shows the declarative REST and generated native Dubbo client model.

## What Changed

- Uses `rust-java-rest:4.1.0`, `java-rust-dubbo:0.6.0`,
  `rest-sample-utility:0.3.1`, and `rust-sample-model:0.3.1`.
- Replaces separate handwritten client definitions with one repeatable `DubboClients` declaration.
- Uses one generated bounded Dubbo transport lifecycle for all client interfaces.
- Uses constructor-injected `@RestController` handlers and generated route invokers.
- Uses the framework `LongKeyAdmission` instead of a sample-specific command admission class.
- Removes duplicate runtime-plan and configuration boilerplate.

## Compatibility

REST URLs, GET/POST/PATCH/DELETE payloads, Dubbo interfaces, static/ZooKeeper discovery properties,
and Java business flow are unchanged.

## Run

```powershell
mvn clean package
mvn exec:java
```

The release JAR contains the application classes. Use Maven as above, or build one of the documented
jlink images, so the selected Dubbo profile and its dependencies are included correctly.

Use `native-static-consumer` for the smallest static-provider artifact, `full-dubbo-consumer` for the
complete sample, and `zookeeper-discovery` when registry-based provider updates are required.
