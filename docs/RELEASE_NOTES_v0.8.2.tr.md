# rest-sample-dubbo-consumer 0.8.2

[English](RELEASE_NOTES_v0.8.2.md) | [Türkçe](RELEASE_NOTES_v0.8.2.tr.md)

Koordineli güncelleme: 1 Kasım 2026.

## Bağımlılıklar

`com.reactor:rust-java-platform-parent:4.8.2` kullanın. Mevcut `full-dubbo-consumer`
profili/POM'u için tanımlar; REST starter ve compiler ayarlarını koruyun:

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

Utility, model `0.5.2` getirir; provider kontratlarını birlikte güncelleyin.
`direct-json-consumer` profilinin bağımsız Spring adapter/annotations/codegen hattı
artık `0.8.2` kullanır; adapter eşleşen runtime `0.8.2` getirir. Eşleşen native
dosyalarını kullanın. Sahiplik kuralları ve açık P05 HTTP riski için
[direct JSON rehberine](DIRECT_JSON.tr.md), paket erişimi için
[Maven erişimine](../README.tr.md#maven-package-erişimi) bakın.
Normal profili `mvn -B -ntp -DskipTests -Pfull-dubbo-consumer clean package` ile
yeniden paketleyin; bu kabul kanıtı değildir.

## Mevcut DB/Dubbo Örnekleri

[CustomerHandler](../src/main/java/com/reactor/sample/dubbo/consumer/handler/CustomerHandler.java),
`DubboResponses.ok`/`created` ve uygulama hatası eşlemesinden sonra son `replyAsync`
sınırını kullanır. 200/201, mevcut 503 kodları ve iptal sonrası geç gelen native
yanıtın temizlenmesi korunur. Mevcut dört anahtar bazlı PATCH/DELETE metodu
`@KeyAdmission(bean="customerCommandKeyAdmission", key="customerId", onRejected=...)`
kullanır. Mevcut bean süreç içi sınırlı kabul kontrolü sağlar; anahtar HTTP `id` alanına
bağlanan Java parametresidir, yeni HTTP parametresi değildir. Provider DB havuzu,
metot sınırları ve transaction/idempotency kuralları bağımsız kalır. Yeni API gerekmez.

Readiness seçimi ilk kez `0.8.1` ile sunuldu; güncel komut `0.8.2` JAR'ını kullanır.
Query-only readiness erişim kontrolü değildir. [Compose native overlay](../README.tr.md#compose),
yalnız katalog için `native-static-consumer` kullanır; tam customer/DB kontratını sunmaz.

## Geçiş Ve Kanıt

JNI ABI, dış HTTP sözleşmesi veya ayar anahtarı geçişi gerekmez.
Seçilen bağımlılığın paketlediği native dosyayı kullanın; Spring Dubbo ABI `3` değişmez.
Bu notlar yayın veya tam kabul kanıtı değildir. İlgili adayın kesin doğrulama kaydını
esas alın; hosted uyumluluk, EmaTech performans/soak kanıtının yerine geçmez ve
RPS/p99/bellek PASS anlamına gelmez.
