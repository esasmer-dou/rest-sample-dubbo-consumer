# rest-sample-dubbo-consumer 0.6.1

`0.6.1` aligns the runnable REST-to-Dubbo consumer with `rust-java-rest:4.3.0`,
`java-rust-dubbo:0.7.1`, `rest-sample-utility:0.4.1`, and `rust-sample-model:0.4.1`.

- REST annotations, Java handlers, service contracts, static discovery, and ZooKeeper discovery are
  unchanged.
- Generated route invokers are resolved before traffic.
- Native Dubbo response handles and the Java business flow keep the same contract.

Build the default native-static profile:

```powershell
mvn clean verify
mvn exec:java
```
