# rest-sample-dubbo-consumer 0.4.0

`0.4.0`, örneği generated REST ve native Dubbo client sürüm çizgisine taşır.

## Yenilikler

- `rust-java-rest:4.0.0`, `java-rust-dubbo:0.5.0` ve
  `rest-sample-utility:0.3.0` kullanılır.
- Elle yazılan Dubbo client wrapper sınıfları yerine derleme sırasında üretilen native client'lar
  kullanılır.
- Elle tutulan startup index dosyaları yerine component ve route bilgileri derleme sırasında üretilir.
- Runtime planları ve route limitleri açık, değişmez configuration sınıflarında toplanır.
- Full, native-static ve ZooKeeper seçenekleri ayrı çalışma biçimleri olarak korunur.
- Tekrarlanan `/api/v1/catalog/db/customers` adresi kaldırıldı. Bunun yerine
  `/api/v1/customers/db` adresini kullanın.

## Çalıştırma

```powershell
mvn clean package
java -jar target/rest-sample-dubbo-consumer-0.4.0.jar
```

REST handler'ları, validation, response kararları ve service akışı Java'da kalır. Rust, HTTP I/O ve
sınırlandırılmış native Dubbo transport işlerini yürütür.
