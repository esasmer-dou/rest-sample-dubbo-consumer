# rest-sample-dubbo-consumer 0.6.0

`0.6.0`, uyumlu `4.2.0` platformu için Rust-Java REST ve native Dubbo consumer referansıdır.

## Neler Değişti?

- `rust-java-platform-parent:4.2.0`, `java-rust-dubbo:0.7.0`,
  `rest-sample-utility:0.4.0` ve `rust-sample-model:0.4.0` kullanılır.
- Full, ZooKeeper ve native-static profilleri yalnız ihtiyaç duyduğu uygulama yüzeyini compile eder.
- Generated conditional Dubbo client'lar elle yazılmış catalog-only factory ve module wiring kodunun
  yerini alır.
- Static discovery, static-only olarak tanımlanabilir. Yanlışlıkla ZooKeeper verilirse startup erken
  durur.
- REST handler, validation, orchestration ve business error mapping Java kodunda kalır.

## Native Static Çalıştırma

```powershell
mvn -Pnative-static-consumer clean verify
mvn -Pnative-static-consumer exec:java
```

`zookeeper-discovery` profilini yalnız provider discovery gerçekten ZooKeeper gerektiriyorsa kullanın.
