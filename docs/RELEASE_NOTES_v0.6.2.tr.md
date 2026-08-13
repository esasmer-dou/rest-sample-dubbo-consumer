# rest-sample-dubbo-consumer 0.6.2

`0.6.2`, çalışan REST-to-Dubbo consumer uygulamasını `rust-java-rest:4.4.0`,
`java-rust-dubbo:0.7.2`, `rest-sample-utility:0.4.1` ve `rust-sample-model:0.4.1` ile hizalar.

- REST endpoint'leri, Dubbo kontratları, static/ZooKeeper discovery ve Java business logic değişmez.
- Ortak runtime REST ABI `28`, Dubbo ABI `7` ve Glowroot ABI `1` kullanır.
- Sınırlandırılmış HTTP ve native Dubbo telemetry kullanılabilir; varsayılan olarak kapalıdır.

