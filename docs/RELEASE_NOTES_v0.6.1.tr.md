# rest-sample-dubbo-consumer 0.6.1

`0.6.1`, çalışan REST-to-Dubbo consumer uygulamasını `rust-java-rest:4.3.0`,
`java-rust-dubbo:0.7.1`, `rest-sample-utility:0.4.1` ve `rust-sample-model:0.4.1` ile hizalar.

- REST annotation'ları, Java handler'lar, service kontratları, static discovery ve ZooKeeper
  discovery değişmedi.
- Generated route invoker'ları trafik başlamadan çözülür.
- Native Dubbo response handle ve Java business akışı aynı kontratı korur.

Varsayılan native-static profili build edin:

```powershell
mvn clean verify
mvn exec:java
```
