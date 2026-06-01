# rest-sample-dubbo-consumer

[English](README.md) | Türkçe

`java-rust-dubbo` adapter'ı ile Dubbo provider çağıran ve provider'dan gelen JSON cevapları
düşük overhead ile REST endpoint olarak dışarı açan minimal Rust-Java REST örnek uygulamasıdır.

Bu repo bilinçli olarak küçük tutuldu. Amaç, bir `rust-java-rest` uygulamasının Spring Boot, Dubbo
Spring Boot starter, resmi Dubbo consumer stack, Netty veya ZooKeeper'ı hot HTTP process içine
varsayılan olarak taşımadan nasıl Dubbo consumer olacağını göstermektir.

## Bu Örnek Ne İçin Hazırlandı?

Bu örnek şu konuları göstermek için hazırlandı:

- `rust-java-rest` uygulamasına `java-rust-dubbo` nasıl eklenir.
- Java REST handler içinden Dubbo provider nasıl çağrılır.
- Provider'ın ürettiği JSON `RawResponse` ile DTO graph kurulmadan nasıl HTTP response yapılır.
- Tek REST process içinde birden fazla Dubbo interface full Dubbo stack yüklenmeden nasıl consume edilir.
- Static provider listesi ile consumer process nasıl küçük tutulur.
- ZooKeeper discovery sadece ihtiyaç olduğunda nasıl devreye alınır.
- Handler, config, RPC adapter ve runtime class'ları DTO record'lardan nasıl ayrılır.

Bu örnek tam kapsamlı bir Dubbo governance platformu değildir. Tüm Dubbo özelliklerini göstermek
yerine Rust-Java framework felsefesine uygun minimum-overhead consumer yolunu gösterir: business
logic Java'da kalır, HTTP I/O ve seçilmiş low-level transport işleri Rust/native tarafta yürür.

## `rust-java-rest` 3.1.0 Bu Örnekte Ne Değiştiriyor?

Bu örnek artık `rust-java-rest` `3.1.0` kullanır. Uygulama kodu modeli değişmez: handler'lar,
service adapter'ları, configuration class'ları ve business kararlar Java'da kalır. Değişiklik daha
çok handler'ların altında çalışan runtime yolundadır.

| v3.1 değişikliği | Bu örnekte etkisi |
|-----------------|-------------------|
| Daha düşük retention yapan response pool'lar | Trafik düşükken consumer daha az native response buffer tutar. |
| Bounded in-flight response byte limiti | Büyük veya yavaş response'lar memory kullanımını limitsiz büyütemez. |
| UTF-8 response/path/query düzeltmeleri | Request değerleri ve response bytes UTF-8 ise Türkçe karakterler güvenli taşınır. |
| Raw/precomputed response yolunun olgunlaşması | Provider JSON `byte[]` döner, consumer `RawResponse.json(bytes)` ile DTO parse/serialize yapmadan döner. |
| Daha açık low-RSS tuning | Sample properties artık gizli default'a değil, açık v3.1 low-RSS değerlere dayanır. |

Bu örnek için en doğru akış hâlâ basittir:

```text
Provider UTF-8 JSON byte[] döner
Consumer RawResponse.json(bytes) döner
Rust-Java runtime HTTP response'u yazar
```

Native cache'i sadece response bilinçli şekilde cache edilebilir olduğunda ve key, TTL, invalidation
kuralınız netse kullanın. Bu sample default olarak response cache kullanmaz çünkü provider cevapları
database-backed olabilir.

Direct JSON writer, consumer kendi içinde hot ve sabit şekilli JSON üretiyorsa anlamlıdır. Normal
Dubbo forwarding yolunda gerekli değildir; çünkü provider zaten hazır JSON döner.

## Diğer Projelerle İlişkisi

Bu repo şunlara bağlıdır:

| Bağımlılık | Neden gerekli? |
|------------|----------------|
| `rust-java-rest` | Rust Hyper HTTP server, Java handler modeli, DI, `RawResponse` ve runtime config sağlar. |
| `java-rust-dubbo` | Bu örnekte kullanılan hafif Dubbo consumer adapter'ını sağlar. |
| `rest-sample-dubbo-provider` | Lokal uçtan uca test için kullanılan örnek Dubbo provider'dır. |
| ZooKeeper | Opsiyoneldir. Sadece discovery modunda gerekir. |

Provider repo:

```text
git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
```

Varsayılan mod static provider modudur:

```text
consumer -> 127.0.0.1:20880
```

ZooKeeper discovery modu:

```text
consumer -> ZooKeeper -> provider URL -> Dubbo provider
```

## Mimari Akış

```text
HTTP client
  -> rust-java-rest içindeki Rust Hyper server
  -> Java handler
  -> küçük interface-specific client adapter
  -> java-rust-dubbo native consumer
  -> seçilen Dubbo interface
  -> JSON byte[]
  -> RawResponse.json(...)
  -> Rust HTTP response
```

Kritik nokta: consumer provider JSON'unu DTO'ya parse edip tekrar serialize etmez. Provider JSON
bytes üretiyorsa consumer bu bytes'ı direkt HTTP body olarak taşır.

Bu sample artık iki Dubbo interface consume eder:

| REST alanı | Dubbo interface | Method | Neden ayrı? |
|------------|-----------------|--------|-------------|
| Catalog read | `NestedCatalogService` | `getNestedCatalogJson()` | Catalog payload DB-backed customer read'den bağımsızdır. |
| Customer read | `CustomerQueryService` | `getDatabaseCustomersJson()` | DB-backed customer query'nin lifecycle, repository ve scaling profili ayrıdır. |

Bunları ayrı interface olarak tutmak "god RPC interface" oluşmasını engeller. Ayrıca her provider
contract'ı ayrı test edilebilir, tune edilebilir ve değiştirilebilir.

## Paket Yapısı

```text
com.reactor.sample.dubbo.consumer.app
  Uygulama bootstrap ve Rust HTTP server başlangıcı.

com.reactor.sample.dubbo.consumer.config
  Properties-only runtime config ve Dubbo consumer bean wiring.

com.reactor.sample.dubbo.consumer.handler
  REST endpoint handler'ları.

com.reactor.sample.dubbo.consumer.dubbo
  Native Dubbo client adapter ve metrics erişimi.

com.reactor.rust.dubbo.sample
  Ortak Dubbo interface örnekleri. Production'da shared API jar'a taşınmalı.
```

Main class:

```text
com.reactor.sample.dubbo.consumer.app.RestSampleDubboConsumerApplication
```

## DTO, Runtime Class ve RawResponse Ayrımı

Rust-Java framework standardı şudur:

```text
HTTP JSON request/response DTO = Java record
Runtime davranış/resource sahibi nesne = Java class
Hazır JSON/RPC payload = RawResponse
```

Bu örnekteki class'lar response DTO değildir:

| Tip | Rol | JSON DTO mu? |
|-----|-----|--------------|
| `RestSampleDubboConsumerApplication` | Process ve HTTP server başlatır. | Hayır |
| `ConsumerProperties` | Runtime property okur ve validate eder. | Hayır |
| `DubboConsumerConfiguration` | Dubbo client bean'lerini oluşturur/kapatır. | Hayır |
| `NestedCatalogClient` | Native Dubbo method invoker adapter'ıdır. | Hayır |
| `CustomerQueryClient` | Customer Dubbo interface adapter'ıdır. | Hayır |
| `CatalogHandler` | REST endpoint davranışını taşır. | Hayır |
| `CustomerHandler` | Customer REST endpoint davranışını taşır. | Hayır |
| `HealthHandler` | Health endpoint davranışını taşır. | Hayır |
| `NestedCatalogService` | Dubbo interface kontratıdır. | HTTP JSON DTO değil |
| `CustomerQueryService` | İkinci Dubbo interface kontratıdır. | HTTP JSON DTO değil |

### Use Case: Normal REST DTO

Endpoint kendi JSON response'unu üretiyorsa record kullanın:

```java
public record LocalStatusResponse(
        String status,
        String node,
        long activeCalls
) {}

@GetMapping(value = "/local/status", responseType = LocalStatusResponse.class)
public ResponseEntity<LocalStatusResponse> localStatus() {
    return ResponseEntity.ok(new LocalStatusResponse("UP", "consumer-1", 12));
}
```

### Use Case: Dubbo Provider Hazır JSON Döndürüyor

Provider zaten JSON bytes dönüyorsa parse edip tekrar serialize etmeyin:

```java
@GetMapping(value = "/catalog/raw", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> catalogRaw() {
    return catalogClient.nestedCatalogJsonAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

### Use Case: Dubbo Object Contract

Provider JSON bytes değil de domain object dönecekse DTO'ları ortak API jar içinde record olarak tutun:

```java
public record CatalogItem(String sku, String name) {}

public record CatalogResponse(String source, List<CatalogItem> items) {}

public interface CatalogService {
    CatalogResponse getCatalog();
}
```

Bu model normal business API için okunabilir olabilir; ancak consumer tarafında object graph ve
serialization maliyeti oluşturur. Low-RSS/read-heavy JSON forwarding için `byte[] + RawResponse`
daha doğru yoldur.

### Dubbo Metodu Direkt Record Döner mi?

Java API seviyesinde evet:

```java
public interface CatalogService {
    CatalogResponse getCatalog();
}
```

Consumer tarafında invoker record dönüş tipiyle tanımlanabilir:

```java
DubboReferenceSpec<CatalogService> spec = DubboReferenceSpec.of(CatalogService.class);

NativeDubboMethodInvoker<CatalogResponse> invoker =
        client.method(spec, "getCatalog", CatalogResponse.class);

CompletableFuture<CatalogResponse> response = invoker.invokeAsync();
```

Ama runtime açısından bu "zero-copy record transfer" değildir. Provider record'u serialize eder,
consumer tekrar deserialize eder:

```text
provider CatalogResponse record
  -> Hessian2 serialization
  -> Dubbo TCP frame
  -> Rust native transport
  -> response bytes
  -> Java Hessian2 decode
  -> yeni CatalogResponse record instance
  -> HTTP JSON olarak dönülecekse rust-java-rest JSON serialization
```

Consumer bu objeyi okuyacak, filtreleyecek veya business karar verecekse bu yol doğrudur. Consumer
sadece provider cevabını HTTP client'a taşıyorsa en düşük overhead yol değildir.

Mevcut adapter davranışı:

| Dubbo method şekli | Runtime path | Ana overhead |
|--------------------|--------------|--------------|
| `byte[] method()` | No-arg byte-array çağrılar için native fast path. | En düşük allocation; Java hazır bytes alır. |
| `record method()` | Rust transport sonrası Java Hessian2 decode. | Record object graph allocation ve opsiyonel HTTP JSON serialization. |
| `record method(args...)` | Java Hessian2 encode ve decode. | Request object allocation, response graph allocation, codec CPU. |
| `String method()` | Object return path üzerinden çalışır. | String allocation ve sonradan JSON yapılırsa escaping/charset maliyeti. |
| Büyük nested object graph | Business logic gerçekten ihtiyaç duymuyorsa kaçının. | Heap pressure, GC, p99 latency, RSS artışı. |

Bu ortamda Hessian Lite 4.0.3 basit Java 21 record'ları serialize/deserialize edebiliyor. Yine de
production API için gerçek nested field'lar, collection tipleri, enum'lar, tarih alanları, null
değerler ve version değişimleriyle contract test yazılmalıdır. Küçük bir record çalıştı diye tüm DTO
şekillerinin risksiz olduğunu varsaymayın.

### Use Case Karar Örnekleri

REST consumer Dubbo sonucunun üzerinde business logic çalıştıracaksa `record` kullanın:

```java
public record CatalogSummary(String source, int itemCount) {}

@GetMapping(value = "/catalog/summary", responseType = CatalogSummary.class)
public CompletableFuture<ResponseEntity<CatalogSummary>> summary() {
    return catalogRecordInvoker.invokeAsync()
            .thenApply(catalog -> ResponseEntity.ok(
                    new CatalogSummary(catalog.source(), catalog.items().size())));
}
```

Provider cevabı zaten JSON ise `byte[] + RawResponse` kullanın:

```java
@GetMapping(value = "/catalog", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> catalog() {
    return catalogJsonInvoker.invokeAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

Runtime davranış veya resource ownership için normal Java class kullanın:

```java
public final class CatalogClient {
    private final NativeDubboMethodInvoker<byte[]> catalogJson;

    public CompletableFuture<byte[]> catalogJsonAsync() {
        return catalogJson.invokeAsync();
    }
}
```

Büyük export veya büyük response için streaming/file/native response tasarımı tercih edilmelidir.
Büyük Dubbo object graph'ı consumer JVM'e çekip tekrar JSON'a çevirmek bu framework için anti-pattern'dir.

## Bağımlılıklar

```xml
<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>rust-java-rest</artifactId>
    <version>3.1.0</version>
</dependency>

<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo</artifactId>
    <version>0.1.0-rc2</version>
</dependency>
```

Demo kendi içinde Dubbo interface source'larını taşır:

```java
package com.reactor.rust.dubbo.sample;

public interface NestedCatalogService {
    byte[] getNestedCatalogJson();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();
}
```

Gerçek sistemde bu interface'ler provider ve consumer tarafından kullanılan ortak bir `*-api` jar
içinden gelmelidir. Ayrı servislerde küçük farklarla copy/paste contract üretmeyin.

## GitHub Packages

`rust-java-rest` ve `java-rust-dubbo` sadece GitHub Packages üzerinden yayınlandıysa Maven için repo ve
credential gerekir.

`pom.xml` içinde repository tanımları hazırdır:

```xml
<repositories>
    <repository>
        <id>github-rust-java-rest</id>
        <url>https://maven.pkg.github.com/esasmer-dou/rust-java-rest</url>
    </repository>
    <repository>
        <id>github-java-rust-dubbo</id>
        <url>https://maven.pkg.github.com/esasmer-dou/java-rust-dubbo</url>
    </repository>
</repositories>
```

`~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github-rust-java-rest</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
        <server>
            <id>github-java-rust-dubbo</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
    </servers>
</settings>
```

Private package için token'da `read:packages` ve ilgili private repo erişimi olmalıdır.

## Konfigürasyon

Ana config dosyası:

```text
src/main/resources/rust-spring.properties
```

Okuma sırası:

```text
system property > environment variable > classpath properties
```

Runtime default değerleri kodda tutulmaz. Eksik veya hatalı property startup sırasında fail-fast olur.
Repo içindeki değerler bilinçli olarak low-RSS yönlüdür; bu değerleri ancak throughput, p99 latency,
503 oranı ve RSS'i birlikte ölçtükten sonra büyütün.

Önemli property'ler:

| Property | Açıklama |
|----------|----------|
| `server.port` | Rust-Java REST HTTP portu. |
| `reactor.rust.jni.workers` | JNI worker sayısı. Low RSS için küçük tutulur. |
| `reactor.rust.http.max-response-body-bytes` | Tek response body limiti. |
| `reactor.rust.http.max-inflight-response-bytes` | Toplam in-flight response byte limiti. |
| `reactor.rust.http.max-connections` | Kabul edilecek HTTP connection üst limiti. Kubernetes içinde bounded kalmalı. |
| `reactor.rust.response-pool.small-capacity` / `medium-capacity` / `large-capacity` | Native response buffer retention limitleri. Küçük değer idle RSS'i düşürür; büyük değer steady load altında allocation churn azaltır. |
| `reactor.rust.json.writer-retain-max-bytes` | Retain edilecek maksimum JSON writer buffer boyutu. Ara sıra gelen büyük response'ların kalıcı retained memory büyütmesini engeller. |
| `reactor.rust.native-cache.max-bytes` | Opsiyonel native response cache için hard limit. Endpoint bilinçli cache edilebilir değilse küçük veya kullanılmamış kalmalı. |
| `sample.dubbo.discovery` | `static` veya `zookeeper`. |
| `reactor.dubbo.providers` | Static provider listesi, örn. `127.0.0.1:20880`. |
| `reactor.dubbo.registry-address` | Discovery modunda ZooKeeper adresi. |
| `reactor.dubbo.timeout-ms` | RPC timeout. |
| `reactor.dubbo.max-inflight` | Bounded RPC concurrency. |
| `reactor.dubbo.native-connections-per-endpoint` | Provider başına native Dubbo TCP connection pool boyutu. |

## Önerilen Lokal Çalıştırma Sırası

En küçük consumer process için static provider modu ile başlayın:

```text
1. DB-backed endpoint'i test edecekseniz PostgreSQL'i başlatın.
2. Provider sample kendisini register ettiği için ZooKeeper'ı başlatın.
3. rest-sample-dubbo-provider'ı başlatın.
4. Bu consumer'ı sample.dubbo.discovery=static ile başlatın.
5. REST endpoint'leri çağırın ve /api/v1/catalog/dubbo-metrics çıktısını kontrol edin.
```

Static consumer modu consumer JVM içinde Java ZooKeeper client yüklemez. ZooKeeper provider sample
için hâlâ faydalıdır; çünkü provider registration ve discovery modunu gösterir.

## Hızlı Başlangıç: Static Provider Modu

Gereksinimler:

- Java 21
- Maven 3.9+
- Private dependency kullanıyorsanız GitHub Packages erişimi
- `127.0.0.1:20880` üzerinde çalışan Dubbo provider

Önce provider'ı başlatın:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
cd rest-sample-dubbo-provider
mvn -q exec:java
```

Sonra consumer'ı başlatın:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-consumer.git
cd rest-sample-dubbo-consumer
mvn -q test
mvn -q exec:java
```

Test:

```powershell
curl http://127.0.0.1:8080/app/health
curl http://127.0.0.1:8080/api/v1/catalog/nested
curl http://127.0.0.1:8080/api/v1/customers/db
curl http://127.0.0.1:8080/api/v1/catalog/db/customers
curl http://127.0.0.1:8080/api/v1/catalog/dubbo-metrics
```

## Hızlı Başlangıç: ZooKeeper Discovery Modu

ZooKeeper başlatın:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
```

Provider'ı başlatın. Provider kendisini ZooKeeper'a register eder:

```powershell
cd rest-sample-dubbo-provider
mvn -q exec:java
```

Consumer'ı optional ZooKeeper profiliyle başlatın:

```powershell
cd rest-sample-dubbo-consumer
$env:SAMPLE_DUBBO_DISCOVERY="zookeeper"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://127.0.0.1:2181"
mvn -q -Pzookeeper-discovery exec:java
```

Bu modda `reactor.dubbo.providers` yok sayılır, provider URL'leri ZooKeeper'dan okunur.

## Endpoint'ler

| Endpoint | Açıklama |
|----------|----------|
| `GET /app/health` | Consumer application health endpoint'i. |
| `GET /api/v1/catalog/nested` | `NestedCatalogService` çağırır ve nested catalog JSON'u forward eder. |
| `GET /api/v1/customers/db` | `CustomerQueryService` çağırır ve PostgreSQL-backed customer JSON'u forward eder. |
| `GET /api/v1/catalog/db/customers` | Compatibility alias; yine `CustomerQueryService` çağırır. |
| `GET /api/v1/catalog/dubbo-metrics` | Native Dubbo client metrics çıktısı. |

## Neden Küçük?

Minimum-overhead yol static provider + native transport'tur:

- Hot JVM içinde Java ZooKeeper client başlamaz.
- Resmi Dubbo `ReferenceConfig` stack'i başlamaz.
- Consumer process içine Netty client stack taşınmaz.
- Provider JSON'u parse edilip tekrar serialize edilmez.
- Queue, timeout ve in-flight limitleri bounded kalır.

ZooKeeper discovery ihtiyaç olduğunda kullanılabilir. Ancak düşük RSS servislerde static provider,
Kubernetes Service DNS veya sidecar-generated provider list daha doğru başlangıçtır.

## Sorun Giderme

| Belirti | Kontrol |
|---------|---------|
| Maven `com.reactor` artifact'lerini çekemiyor | GitHub Packages repository ve `~/.m2/settings.xml` ayarlarını kontrol edin. |
| GitHub Packages `401` / `403` | Token `read:packages` ve private repo erişimine sahip olmalı. |
| `Dubbo provider ... timed out` | Provider `20880` üzerinde dinliyor mu, timeout yeterli mi kontrol edin. |
| ZooKeeper modunda provider bulunmuyor | Provider node'u `/dubbo/{interface}/providers` altında oluşmuş mu kontrol edin. |
| Yüksek concurrency'de p99 artıyor | `reactor.dubbo.max-inflight` ve native connection sayısını ölçerek artırın. |

## Production Notları

- Normal HTTP JSON contract için DTO'ları record yapın.
- JSON zaten serialize edilmişse `RawResponse` kullanın.
- Timeout ve in-flight limitlerini açık tutun.
- Runtime default'ları kodun içine saklamayın.
- Shared Dubbo interface'leri production'da gerçek bir API jar'a taşıyın.
- Metrics endpoint'lerini gerçek deployment'ta koruyun.
