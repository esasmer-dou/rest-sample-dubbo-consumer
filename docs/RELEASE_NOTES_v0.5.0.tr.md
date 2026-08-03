# rest-sample-dubbo-consumer 0.5.0

`0.5.0`, deklaratif REST ve generated native Dubbo client modelini gösterir.

## Neler Değişti?

- `rust-java-rest:4.1.0`, `java-rust-dubbo:0.6.0`, `rest-sample-utility:0.3.1` ve
  `rust-sample-model:0.3.1` kullanılır.
- Ayrı ayrı yazılmış client definition sınıfları yerine tek `DubboClients` tanımı kullanılır.
- Bütün client interface'leri tek generated bounded Dubbo transport lifecycle paylaşır.
- Handler'lar constructor injection, `@RestController` ve generated route invoker kullanır.
- Sample'a özel command admission yerine framework `LongKeyAdmission` sınıfı kullanılır.
- Tekrar eden runtime plan ve configuration kodu kaldırıldı.

## Uyumluluk

REST adresleri, GET/POST/PATCH/DELETE payload'ları, Dubbo interface'leri, static/ZooKeeper discovery
property'leri ve Java iş akışı değişmedi.

## Çalıştırma

```powershell
mvn clean package
mvn exec:java
```

Release JAR'ı uygulama sınıflarını içerir. Seçilen Dubbo profilinin bağımlılıklarının doğru eklenmesi
için yukarıdaki Maven komutunu veya dokümanda anlatılan jlink image'larından birini kullanın.
