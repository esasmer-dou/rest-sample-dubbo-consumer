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
- Minimal Dubbo consumer üzerinde gerçek GET/POST/PATCH/DELETE REST verbleri nasıl expose edilir.
- Static provider listesi ile consumer process nasıl küçük tutulur.
- ZooKeeper discovery sadece ihtiyaç olduğunda nasıl devreye alınır.
- Handler, config, RPC adapter ve runtime class'ları DTO record'lardan nasıl ayrılır.

Bu örnek tam kapsamlı bir Dubbo governance platformu değildir. Tüm Dubbo özelliklerini göstermek
yerine Rust-Java framework felsefesine uygun minimum-overhead consumer yolunu gösterir: business
logic Java'da kalır, HTTP I/O ve seçilmiş low-level transport işleri Rust/native tarafta yürür.

## Buradan Başlayın: Consumer Şeklinizi Seçin

Kullanıcı için en doğru başlangıç tüm property listesini ezberlemek değildir. Önce servisinizin
şeklini seçin, en yakın profile'ı kopyalayın, sonra sadece trafiğinizi etkileyen birkaç değeri tune
edin.

**Tüm sample verblerini lokal denemek**

- Maven profile: default `full-dubbo-consumer`
- Runtime profile: `micro-dubbo`
- Response yolu: `RawResponse.json(bytes)`
- Kazanç: GET, POST, PATCH ve DELETE örnekleri hemen çalışır.
- Bedel: read-only moda göre classpath daha büyüktür.

**En düşük memory read-only consumer**

- Maven profile: `native-static-consumer`
- Runtime profile: `micro-dubbo`
- Response yolu: argümansız Dubbo method UTF-8 JSON `byte[]` döner.
- Kazanç: consumer içinde ZooKeeper ve Hessian class'ları yoktur.
- Bedel: sadece argümansız read çağrıları desteklenir.

**Kubernetes Service DNS ile static consumer**

- Maven profile: default veya `native-static-consumer`
- Runtime profile: `micro-dubbo`
- Response yolu: provider adresi K8s Service DNS olur.
- Kazanç: Java ZooKeeper client/thread/class yükü consumer'a girmez.
- Bedel: Dubbo registry/governance yoktur; dağıtım TCP connection bazlıdır.

**Kubernetes'te ZooKeeper zorunlu consumer**

- Maven profile: `zookeeper-discovery`
- Runtime profile: `micro-dubbo`
- Response yolu: provider URL ZooKeeper'dan gelir.
- Kazanç: provider restart ve re-register akışını takip eder.
- Bedel: ZooKeeper client thread/class ekler.

**Read-heavy catalog veya lookup API**

- Maven profile: default veya `zookeeper-discovery`
- Runtime profile: `micro-dubbo`
- Response yolu: provider hazır JSON döner, consumer raw bytes forward eder.
- Kazanç: REST JVM DTO graph ve JSON reserialize yapmaz.
- Bedel: JSON shape/versioning provider sorumluluğudur.

**Typed Dubbo DTO örnekleri**

- Maven profile: default veya `zookeeper-discovery`
- Runtime profile: `micro-dubbo`
- Response yolu: provider record/list/map/scalar değerler döner.
- Kazanç: normal Dubbo object contract örneklerini gösterir.
- Bedel: Hessian encode/decode ve Java object graph allocation oluşur.

**Dubbo üzerinden write/command API**

- Maven profile: default veya `zookeeper-discovery`
- Runtime profile: `micro-dubbo`
- Response yolu: `byte[]` request body provider command method'una gider.
- Kazanç: REST handler ince ve açık kalır.
- Bedel: Hessian request encoding gerekir.

**Daha yüksek concurrency RPC servisi**

- Maven profile: default veya `zookeeper-discovery`
- Runtime profile: `micro-dubbo` ile başlayın, ölçerek balanced değerlere yaklaşın.
- Response yolu: aynı API yolu, daha büyük route budget.
- Kazanç: daha az overload reject.
- Bedel: daha yüksek RSS ve provider/DB baskısı.

Önerilen başlangıç: Kubernetes içinde provider zaten bir `Service` DNS arkasındaysa ve Dubbo
registry/governance ihtiyacınız yoksa static provider modu daha küçük ve daha sade başlangıçtır.
Provider discovery production'da ZooKeeper üzerinden zorunluysa consumer'ı `zookeeper-discovery` ile
build/run edin. ZooKeeper dependency olmadan build edilmiş bir image'a runtime'da sadece
`SAMPLE_DUBBO_DISCOVERY=zookeeper` vermek yeterli olmaz.

## ZooKeeper Ne Sağlar, Ne Zaman Gerekir?

ZooKeeper bu consumer için performans artırıcı bir HTTP bileşeni değildir. Görevi Dubbo provider
discovery ve registry bilgisidir.

| ZooKeeper görevi | Açıklama | K8s Service DNS bunu karşılar mı? |
|------------------|----------|----------------------------------|
| Provider registration | Provider hangi interface'i hangi IP/port ile sunduğunu registry'ye yazar. | Kısmen. K8s Service pod endpoint'lerini bilir ama Dubbo interface metadata'sı tutmaz. |
| Provider discovery | Consumer provider listesini registry'den okur. | Evet, basit tek-service senaryoda Service DNS provider adresi gibi kullanılabilir. |
| Provider değişimini izleme | Provider giderse veya yeni provider gelirse registry üzerinden görülebilir. | Evet, readiness + EndpointSlice ile pod değişimi izlenir; ama Dubbo registry event'i yoktur. |
| Interface/group/version metadata | Dubbo interface, group, version gibi bilgiler registry modelinde tutulur. | Hayır. Bunları Service adı/config ile siz yönetirsiniz. |
| Governance/routing | Disable/enable, özel route, group/version routing gibi Dubbo davranışlarına zemin sağlar. | Hayır. K8s Service basit endpoint dağıtımı yapar. |

**BEST:** K8s içinde tek provider service'iniz varsa ve consumer sadece
`rest-sample-dubbo-provider:20880` gibi stabil bir Service DNS'e gidecekse ZooKeeper kullanmayın.
Bu durumda consumer daha az class, thread ve registry state taşır.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "2"
```

**ACCEPTABLE:** Birden fazla provider interface'i farklı Kubernetes Service DNS'leriyle yönetilecekse
yine static kalabilirsiniz. Burada provider adreslerini config ile açık verirsiniz:

```properties
reactor.dubbo.providers=customer-provider:20880,order-provider:20880
```

Bu modelde Kubernetes arka tarafta kaç provider replica olduğundan bağımsız Service üzerinden TCP
connection dağıtımı yapar. Ancak dağıtım RPC başına değil, çoğunlukla TCP connection başınadır.
Bu yüzden `reactor.dubbo.native-connections-per-endpoint=2` veya `4` gibi kontrollü değerler daha
dengeli dağılım sağlayabilir.

**ZooKeeper gerekli olur:** provider'lar K8s Service DNS ile sabitlenemiyorsa, Dubbo
interface/group/version registry metadata'sı gerekiyorsa, VM/bare-metal provider'lar da aynı
registry'ye register oluyorsa veya disable/enable/routing gibi Dubbo governance özellikleri
production kararınızın parçasıysa.

## Production Reçeteleri

### Reçete 1: ZooKeeper Discovery Kullanan Kubernetes Consumer

Provider pod'ları restart olabilir, taşınabilir veya scale olabilir; consumer provider'ı ZooKeeper
registration üzerinden bulmak zorundaysa bu yolu kullanın.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "1"
  - name: REACTOR_DUBBO_NATIVE_ASYNC_WORKERS
    value: "1"
```

Image aynı dependency şekliyle build edilmelidir:

```powershell
mvn -q -Pzookeeper-discovery -DskipTests package
```

Etkisi:

| Ayar | Neyi kontrol eder? | Artırırsanız | Azaltırsanız |
|------|--------------------|--------------|--------------|
| `REACTOR_DUBBO_MAX_INFLIGHT` | Dubbo adapter'ın izin verdiği concurrent RPC sayısı | Daha çok request fail-fast olmadan bekler/çalışır; RSS ve provider baskısı artabilir | RSS düşer, spike anında 503 daha erken gelir |
| `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT` | Provider endpoint başına native Dubbo TCP connection sayısı | Yavaş provider'da paralellik artabilir | Connection/buffer footprint küçülür |
| `REACTOR_DUBBO_NATIVE_ASYNC_WORKERS` | Native RPC completion worker sayısı | Yüksek concurrency'de queueing azalabilir | Thread stack/native memory düşer |
| `REACTOR_RUST_JNI_WORKERS` | Java handler execution worker sayısı | Daha çok Java work aynı anda çalışır | RSS düşer ve CPU contention azalır |

### Reçete 2: Küçük Read-Only Consumer

Provider adresi biliniyorsa ve REST API sadece `/api/v1/catalog/nested` gibi read endpoint'leri
expose ediyorsa bunu kullanın.

```powershell
mvn -q -Pnative-static-consumer package
java -Xms8m -Xmx48m -Xss256k -XX:ActiveProcessorCount=1 `
  -Dreactor.dubbo.providers=provider:20880 `
  -jar target/rest-sample-dubbo-consumer-0.1.0.jar
```

Etkisi:

| Seçim | Neden önemli? |
|-------|---------------|
| `native-static-consumer` | Read-only consumer classpath'inden ZooKeeper ve Hessian'ı çıkarır. |
| `RawResponse.json(bytes)` | Provider JSON'u Java record'a parse edilip tekrar serialize edilmez. |
| `micro-dubbo` | Rust worker, JNI worker, queue ve pool değerlerini küçük tutar. |

### Reçete 3: Bounded Overload İle Command Endpoint

Her REST isteğinin bir provider command çağrısına dönüştüğü POST/PATCH/DELETE route'ları için bunu
kullanın.

```properties
reactor.rust.route-admission.post.api.v1.customers.max-concurrent=8
reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=150
reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent=8
reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=8
reactor.dubbo.timeout-ms=1000
reactor.dubbo.retries=0
```

Etkisi:

| Ayar | Production etkisi |
|------|-------------------|
| Route `max-concurrent` | REST process'i çok sayıda yavaş RPC/DB çağrısından korur. |
| Route `queue-timeout-ms` | REST katmanının controlled overload dönmeden önce ne kadar bekleyeceğini belirler. |
| `reactor.dubbo.timeout-ms` | Tek RPC çağrısını sınırlar. HTTP timeout budget'ınızdan düşük tutun. |
| `reactor.dubbo.retries=0` | Write command'lerinde retry storm riskini azaltır. Idempotency/retry kararını caller veya workflow katmanına koyun. |

### Reçete 4: Idle Sonrası Küçük Kalması Gereken Düşük Trafikli Pod

Sadece az trafik alan ve uzun süre idle kalan servislerde kullanın.

```properties
reactor.rust.native-trim.enabled=true
reactor.rust.native-trim.initial-delay-ms=30000
reactor.rust.native-trim.interval-ms=60000
reactor.rust.native-trim.min-idle-ms=10000
reactor.rust.native-trim.max-active-connections=0
reactor.rust.native-trim.max-active-requests=0
reactor.rust.native-trim.retain-small=16
reactor.rust.native-trim.allocator-trim-enabled=true
```

Etkisi: warmed native anonymous memory idle sonrası geri verilebilir. High-throughput servislerde
kör şekilde açmayın; trim açık/kapalı p99 ve 503 oranını birlikte ölçün.

### Reçete 5: Consumer Restart Etmeden Provider Rolling Restart

Provider'lar gün içinde redeploy oluyorsa ve consumer'ın ZooKeeper discovery ile toparlanması
gerekiyorsa bunu kullanın.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_DUBBO_REGISTRY_CHECK
    value: "false"
  - name: REACTOR_DUBBO_CHECK
    value: "false"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1000"
  - name: REACTOR_DUBBO_RETRIES
    value: "0"
```

Etkisi: provider kısa süre yokken consumer başlayabilir. Bu boşlukta REST route'ları queue
büyütmek yerine bounded failure döner. Provider ZooKeeper altında tekrar register olduğunda consumer
yeni provider adresini kullanabilir.

Pratik kontrol:

```powershell
curl http://localhost:8080/api/v1/catalog/dubbo-metrics
curl http://localhost:8080/api/v1/catalog/nested
```

### Reçete 6: Docker veya Kubernetes Service DNS İle Static Provider Adresi

Provider adresi Docker service adı veya Kubernetes Service DNS olarak sabitse ve consumer process
içinde ZooKeeper client yüklemek istemiyorsanız bunu kullanın.

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
```

Etkisi: consumer Java ZooKeeper client yüklemez ve verdiğiniz provider host'a doğrudan bağlanır. Bu
lokal, Docker Compose, K8s Service DNS veya sidecar-generated provider list gibi kontrollü ortamlar
için iyi bir şekildir.

Birden fazla provider interface'i ayrı Service DNS ile yönetiliyorsa virgülle ayrılmış liste verin:

```properties
reactor.dubbo.providers=customer-provider:20880,order-provider:20880
```

Bu modelde ZooKeeper zorunlu değildir. Kubernetes Service arkasındaki pod replica sayısı değişebilir;
consumer Service DNS'e bağlanır, K8s arka tarafta connection'ları endpoint'lere dağıtır. Bu dağıtım
request bazlı değil TCP connection bazlıdır. Bu yüzden tek connection ile başlarsanız consumer bir
süre aynı backend pod üzerinde kalabilir. Daha dengeli dağıtım gerekiyorsa
`reactor.dubbo.native-connections-per-endpoint=2` veya `4` deneyin; bunu RSS ve p99 ölçmeden default
yapmayın.

ZooKeeper reçetesine sadece provider adresleri Service DNS ile modellenemiyorsa, provider'lar
VM/bare-metal ortamdan registry'ye register oluyorsa, interface/group/version metadata'sı registry'den
gelmek zorundaysa veya Dubbo governance/routing özellikleri kullanılıyorsa geçin.

### Reçete 7: Daha Büyük Provider JSON Response

Provider catalog snapshot veya rapor gibi daha büyük JSON document dönüyorsa bunu kullanın.

```properties
reactor.rust.http.max-response-body-bytes=16777216
reactor.rust.http.max-inflight-response-bytes=33554432
reactor.dubbo.max-response-bytes=16777216
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=8
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=150
```

Etkisi:

| Ayar | Anlamı |
|------|--------|
| `max-response-body-bytes` | Rust-Java runtime'ın kabul edeceği tek HTTP response body üst limiti. |
| `max-inflight-response-bytes` | Aynı anda in-flight olan response'ların toplam byte limiti. |
| `reactor.dubbo.max-response-bytes` | Dubbo provider'dan kabul edilecek maksimum response boyutu. |
| Daha düşük route concurrency | Birkaç büyük response'un aynı anda memory'de tutulmasını sınırlar. |

Bu path için `RawResponse.json(bytes)` kullanın. Büyük provider JSON'unu Java object graph'a parse
edip sonra tekrar JSON'a çevirmeyin.

### Reçete 8: Daha Yüksek Read Concurrency, Ama Bounded

Catalog/read endpoint stabilse, provider CPU saturation altında değilse ve p99 hedefiniz için daha
fazla başarılı request gerekiyorsa bunu kullanın.

```properties
reactor.dubbo.max-inflight=64
reactor.dubbo.native-connections-per-endpoint=2
reactor.dubbo.native-async-workers=2
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=32
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=150
```

Etkisi: overload öncesi daha fazla read çağrısı ilerleyebilir. RSS ve provider CPU da artabilir. Bu
değerleri birlikte artırın; p99, 503 oranı, provider CPU ve memory değerlerini birlikte ölçün. Route
DB-backed ise önce provider Hikari pool ve provider method limitleriyle hizalayın.

### Reçete 9: Türkçe Karakterler ve UTF-8 Payload

Request body, query value, path variable veya provider JSON içinde `İstanbul`, `Şişli`, `Çağrı`,
`müşteri` gibi değerler varsa bunu dikkate alın.

```java
@GetMapping(value = "/catalog/nested", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
    return catalogClient.getNestedCatalogJson()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

```powershell
curl "http://localhost:8080/api/v1/customers/db?city=%C4%B0stanbul"
```

Etkisi: JSON'u UTF-8 byte olarak tutup `RawResponse.json(bytes)` ile dönün. URL değerlerinde client
tarafı non-ASCII karakterleri percent-encode etmelidir. Request body için UTF-8 JSON gönderin ve
platform-default string/byte dönüşümlerinden kaçının.

### Reçete 10: Dubbo Baskısından Ayrı Health Endpoint

Kubernetes liveness için ucuz local health endpoint kullanın; Dubbo kontrollerini readiness veya
ayrı diagnostics endpoint üzerinde tutun.

```powershell
curl http://localhost:8080/app/health
curl http://localhost:8080/api/v1/catalog/dubbo-metrics
```

Etkisi: liveness ZooKeeper, provider CPU veya DB latency'ye bağımlı olmaz. Deployment politikanız
gerektiriyorsa readiness Dubbo/provider durumunu ayrıca kontrol edebilir.

## Production Senaryo Rehberi

Bu bölüm tek tek property ezberletmek için değil, gerçek hayatta karşılaşacağınız servis
şekillerine göre karar vermeniz için hazırlandı. Aynı consumer içinde müşteri okuma, sipariş
getirme, katalog listeleme, müşteri yaratma, statü güncelleme ve silme endpoint'leri birlikte
çalışabilir. Her endpoint aynı maliyette değildir; bu yüzden her endpoint'e aynı kuyruk, aynı
timeout ve aynı concurrency vermek production'da doğru davranış değildir.

### Önce Terimleri Netleştirelim

**`p99`**

- Basit açıklama: 100 isteğin 99 tanesi bu sürenin altında biter. Örnek: p99 120 ms ise isteklerin çoğu hızlıdır, ama en yavaş yüzde 1 için 120 ms görüyorsunuz.
- Bu projede etkisi: kullanıcının hissettiği yavaşlamayı average latency'den daha iyi gösterir.

**`503`**

- Basit açıklama: servisin "şu anda bu isteği alamam" demesidir. Hata gibi görünür ama kontrollü overload için bilinçli kullanılır.
- Bu projede etkisi: kuyruk şişip memory ve latency patlamasın diye bazı istekler erken reddedilir.

**Hot read**

- Basit açıklama: çok çağrılan okuma endpoint'i. Örnek: müşteri getir, sipariş getir, katalog getir.
- Bu projede etkisi: daha fazla route budget alabilir ama DB/provider kapasitesini aşmamalıdır.

**Cold route**

- Basit açıklama: seyrek çağrılan endpoint. Örnek: admin işlem, seyrek patch, silme.
- Bu projede etkisi: hot endpoint'lerin kapasitesini çalmamalıdır.

**Write command**

- Basit açıklama: veri değiştiren işlem. Örnek: müşteri yarat, sipariş iptal et, müşteri statüsü değiştir.
- Bu projede etkisi: retry ve derin kuyruk tehlikelidir; idempotency gerekir.

**Route budget**

- Basit açıklama: bir endpoint'in aynı anda kaç işi içeri alabileceğidir.
- Bu projede etkisi: `reactor.rust.route-admission...max-concurrent` ile ayarlanır.

**Queue timeout**

- Basit açıklama: route budget doluyken isteğin ne kadar bekleyebileceğidir.
- Bu projede etkisi: `queue-timeout-ms` büyürse 503 azalabilir ama p99 ve memory artabilir.

**In-flight**

- Basit açıklama: şu anda işlenmekte olan istek veya response demektir.
- Bu projede etkisi: çok büyürse RSS ve p99 yükselir.

**RSS**

- Basit açıklama: pod'un işletim sistemi gözünden tuttuğu gerçek memory miktarıdır.
- Bu projede etkisi: Kubernetes memory limitini asıl etkileyen değerdir.

**Consumer**

- Basit açıklama: bu proje REST isteğini alır, gerekiyorsa Dubbo provider'a gider ve response döner.
- Bu projede etkisi: REST + Dubbo ayarları bu tarafta yapılır.

**Provider**

- Basit açıklama: arkadaki Dubbo servisidir. DB'ye gider, iş mantığını çalıştırır veya JSON üretir.
- Bu projede etkisi: provider yavaşsa consumer kuyruğunu büyütmek tek başına çözüm değildir.

**RawResponse**

- Basit açıklama: provider'dan gelen JSON byte'larını Java DTO parse/serialize yapmadan direkt response olarak döndürme yoludur.
- Bu projede etkisi: düşük allocation, düşük CPU ve daha düşük RSS için önerilir.

Route admission key'leri path'e göre üretilir. Örneğin `GET /api/v1/customers/db` için örnek key
`reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent` şeklindedir. Kendi projenizde
path farklıysa property key'i de kendi path'inize göre değiştirin.

### E-Ticaret Müşteri ve Sipariş Servisi: 5 Endpoint, 3 Yoğun Endpoint

Bir e-ticaret uygulamasında REST consumer var. Mobil uygulama sık sık müşteri bilgisi,
sipariş özeti ve katalog bilgisi istiyor. Aynı servis müşteri yaratma, statü değiştirme ve silme
işlemlerini de Dubbo provider üzerinden yapıyor. Provider tarafında PostgreSQL ve Hikari pool var.

API'lerin anlamı:

| Gerçek hayattaki iş | Sample endpoint | Trafik | Arkadaki iş |
|---------------------|-----------------|--------|-------------|
| Katalog veya sipariş özeti getir | `GET /api/v1/catalog/nested` | Çok yüksek | Dubbo read provider JSON döner |
| Müşteri bilgisi getir | `GET /api/v1/customers/db` | Çok yüksek | Dubbo provider PostgreSQL'den okur |
| Müşteri yarat | `POST /api/v1/customers` | Yüksek | Dubbo provider PostgreSQL'e yazar |
| Müşteri statüsü değiştir | `PATCH /api/v1/customers/{id}/status` | Düşük | Dubbo write command |
| Müşteri sil | `DELETE /api/v1/customers/{id}` | Düşük | Dubbo write command |

Zaman akışı:

| Zaman | Ne oldu? | Gördüğünüz belirti |
|-------|----------|--------------------|
| İlk gün | Servisi `micro-dubbo` ile açtınız. | RSS düşük, endpoint'ler sağlıklı. |
| Trafik arttı | Üç yoğun endpoint aynı anda provider'a yük bindirdi. | `GET /customers/db` p99 yükseldi, `POST /customers` yavaşladı. |
| İlk yanlış hamle | Sadece global queue büyütüldü. | 503 azaldı ama p99 ve memory yükseldi. |
| Doğru hamle | Her endpoint'e ayrı route budget verildi. | Okuma endpoint'leri üretken kaldı, write command'lar provider'ı boğmadı. |

Başlangıç ayarı:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.jni.workers=1
reactor.rust.jni.queue-capacity=128

reactor.dubbo.max-inflight=24
reactor.dubbo.timeout-ms=1000
reactor.dubbo.retries=0
reactor.dubbo.native-connections-per-endpoint=2
reactor.dubbo.native-async-workers=1

reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=24
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=100

reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=150

reactor.rust.route-admission.post.api.v1.customers.max-concurrent=6
reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=150

reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent=2
reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=2
```

Neden böyle: katalog/sipariş özeti ucuz bir read ise daha büyük budget alır. DB'den müşteri okuma
daha pahalıdır, bu yüzden `8` ile başlar. Write command'lar daha düşük kalır çünkü provider ve DB
yazma kapasitesi sınırsız değildir. `retries=0` command tarafında retry storm'u önler.

Ölçmeniz gerekenler: endpoint bazlı başarılı `200` RPS, endpoint bazlı p99, endpoint bazlı `503`
oranı, provider DB pool wait süresi ve trafik durduktan 30-60 saniye sonra consumer RSS.

Bu senaryoda karşılaşabileceğiniz farklı durumlar ve doğrudan property karşılıkları:

**DB-backed müşteri read p99 yükseliyor**

- Durum: `GET /api/v1/customers/db` p99 yükseldi, provider DB pool wait artıyor.
- Önceki değer: `customers.db.max-concurrent=12` veya daha yüksek.
- Değişiklik: `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8`
- Beklenen düzelme: consumer DB-backed read'i provider'ın bitirebileceği seviyeye çeker.
- Bedel/dikkat: bir miktar `503` artabilir; bu DB'yi korumak için kabul edilir.

**Create customer yavaşlıyor**

- Durum: `POST /api/v1/customers` yavaşlıyor ve duplicate write riski var.
- Önceki değer: `post.customers.max-concurrent=10`, `reactor.dubbo.retries=1`
- Değişiklik: `post.customers.max-concurrent=6`, `reactor.dubbo.retries=0`
- Beklenen düzelme: write command daha tahmin edilebilir olur, retry storm azalır.
- Bedel/dikkat: client tarafında idempotency yoksa duplicate riskini ayrıca çözmelisiniz.

**Katalog/sipariş özeti ucuz ama 503 alıyor**

- Durum: provider CPU sağlıklı, ama ucuz hot read route fazla erken reject ediyor.
- Önceki değer: `catalog.nested.max-concurrent=16`, `native-connections-per-endpoint=1`
- Değişiklik: `catalog.nested.max-concurrent=24`, `native-connections-per-endpoint=2`
- Beklenen düzelme: ucuz hot read daha fazla üretkenlik alır.
- Bedel/dikkat: provider CPU sağlıklı değilse bu değişiklik p99'u kötüleştirir.

**Tüm endpoint'lerde p99 aynı anda yükseliyor**

- Durum: tek global kuyruk tüm endpoint'lerin bekleme süresini büyütüyor.
- Önceki değer: sadece `jni.queue-capacity` büyütüldü.
- Değişiklik: global queue büyütmeyi geri alın; route budget'ları ayrı ayarlayın.
- Beklenen düzelme: yavaş endpoint tüm sistemi boğmaz.
- Bedel/dikkat: her endpoint'i ayrı metrikle izlemeden tek config büyütmeyin.

### Sadakat Puan Servisi: Küçük Pod, 4 Endpoint, 2 Yoğun Endpoint

Kubernetes'te çok sayıda küçük pod koşuyor. Bu consumer sadece sadakat puanı ve müşteri
özeti gibi iki read endpoint'ini yoğun kullanıyor. İki command endpoint seyrek çalışıyor. Ortamda
memory limiti sıkı; amaç her spike'ı taşımak değil, pod'u küçük tutmak.

API'lerin anlamı:

| Gerçek hayattaki iş | Sample endpoint | Trafik | Karar |
|---------------------|-----------------|--------|-------|
| Puan/katalog özeti getir | `GET /api/v1/catalog/nested` | Çok yüksek | Raw JSON dön |
| Müşteri puan bilgisi getir | `GET /api/v1/customers/db` | Çok yüksek | DB-backed read budget düşük başlasın |
| Puan hesabı yarat | `POST /api/v1/customers` | Düşük | Command concurrency düşük |
| Hesabı pasifleştir | `DELETE /api/v1/customers/{id}` | Düşük | Hızlı fail kabul edilebilir |

Zaman akışı:

| Zaman | Ne oldu? | Gördüğünüz belirti |
|-------|----------|--------------------|
| İlk kurulum | Pod memory limiti düşük verildi. | Servis açılıyor ama warm load sonrası RSS önem kazanıyor. |
| Trafik spike aldı | İki hot read aynı anda geldi. | Bazı isteklerde 503 var ama pod memory sabit kalıyor. |
| Kullanıcı beklentisi netleşti | Bu servis memory-first çalışacak. | Kontrollü 503 kabul, limitsiz kuyruk kabul değil. |
| Idle dönem başladı | Servis uzun süre az çağrı aldı. | Native trim açılırsa idle RSS daha aşağı iner. |

Başlangıç ayarı:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.runtime.worker-threads=1
reactor.rust.runtime.max-blocking-threads=1
reactor.rust.runtime.thread-stack-bytes=262144
reactor.rust.jni.workers=1
reactor.rust.response-pool.small-capacity=8
reactor.rust.response-pool.medium-capacity=2
reactor.rust.response-pool.large-capacity=1
reactor.rust.json.writer-retain-max-bytes=32768
reactor.rust.http.max-connections=256

reactor.dubbo.max-inflight=8
reactor.dubbo.native-connections-per-endpoint=1
reactor.dubbo.native-async-workers=1
```

Servis uzun süre idle kalıyorsa ve kendi p99 testiniz geçiyorsa bu ayarı ayrıca açın:

```properties
reactor.rust.native-trim.enabled=true
reactor.rust.native-trim.initial-delay-ms=30000
reactor.rust.native-trim.interval-ms=60000
reactor.rust.native-trim.min-idle-ms=10000
reactor.rust.native-trim.max-active-connections=0
reactor.rust.native-trim.max-active-requests=0
reactor.rust.native-trim.retain-small=16
reactor.rust.native-trim.allocator-trim-enabled=true
```

Neden böyle: thread sayısı, response pool ve Dubbo connection sayısı küçük tutulur. Bu sayede RSS
kontrol altında kalır. Bedel olarak yüksek concurrency'de bazı istekler 503 alabilir. Eğer her istek
mutlaka 200 dönmeli diyorsanız bu artık memory-first senaryo değildir.

Bu senaryoda karşılaşabileceğiniz farklı durumlar ve doğrudan property karşılıkları:

**Idle RSS beklediğinizden yüksek**

- Önceki değer: `native-trim.enabled=false`, pool'lar daha geniş.
- Değişiklik: `native-trim.enabled=true`, `small-capacity=8`, `medium-capacity=2`, `large-capacity=1`
- Beklenen düzelme: trafik sakinleşince warmed native memory daha iyi geri bırakılır.
- Bedel/dikkat: trim hot path'te değil idle'da çalışmalı; aksi halde p99 spike olabilir.

**İki hot read altında çok fazla 503 var ama RSS hâlâ güvenli**

- Önceki değer: `catalog.nested.max-concurrent=12`, `customers.db.max-concurrent=6`
- Değişiklik: `catalog.nested.max-concurrent=16`, `customers.db.max-concurrent=8`
- Beklenen düzelme: faydalı `200` RPS artar.
- Bedel/dikkat: RSS ve provider DB wait tekrar ölçülmeden daha fazla artırmayın.

**Pod memory limiti çok sıkılaştı**

- Önceki değer: `max-connections=512`, `dubbo.max-inflight=16`
- Değişiklik: `max-connections=256`, `dubbo.max-inflight=8`
- Beklenen düzelme: aynı anda içeri alınan iş azalır, RSS daha kontrollü kalır.
- Bedel/dikkat: spike anında 503 artar; bu profilin bilinçli davranışıdır.

**Trim açıldıktan sonra p99 dalgalandı**

- Önceki değer: `native-trim.interval-ms=15000` gibi agresif değer.
- Değişiklik: `initial-delay-ms=30000`, `interval-ms=60000`, `min-idle-ms=10000`
- Beklenen düzelme: trim daha konservatif çalışır, latency etkisi azalır.
- Bedel/dikkat: RSS düşüşü daha yavaş görünebilir.

### Raporlama ve Snapshot Servisi: 10 Endpoint, 4 Yoğun Endpoint, 1 Büyük JSON

Bir iç operasyon ekranı consumer üzerinden müşteri snapshot, kampanya listesi, sipariş
durumu ve büyük rapor JSON'u istiyor. Toplam 10 endpoint var. Trafiğin çoğu 4 endpoint'e gidiyor.
Bu 4 endpoint'ten biri büyük JSON döndürüyor.

API'lerin anlamı:

| Gerçek hayattaki iş | Sample endpoint | Trafik | Risk |
|---------------------|-----------------|--------|------|
| Katalog/snapshot getir | `GET /api/v1/catalog/nested` | Çok yüksek | Ucuzsa yüksek budget alabilir |
| Müşteri DB bilgisi getir | `GET /api/v1/customers/db` | Çok yüksek | Provider DB pool'u bekletebilir |
| Müşteri+kampanya büyük JSON getir | `GET /api/v1/catalog/db/customers` | Yüksek | Büyük response aynı anda memory tutar |
| Metrik/health oku | `GET /api/v1/catalog/dubbo-metrics` | Orta | Hot route kapasitesini çalmamalı |
| Diğer 6 endpoint | Admin veya command | Düşük | Küçük budget ile kalmalı |

Zaman akışı:

| Zaman | Ne oldu? | Gördüğünüz belirti |
|-------|----------|--------------------|
| Başlangıç | Tüm endpoint'ler aynı ayarla çalıştı. | Küçük response'lar iyi, büyük JSON endpoint dalgalı. |
| Trafik arttı | Büyük JSON endpoint aynı anda fazla çağrı aldı. | RSS yükseldi, p99 kötüleşti. |
| Yanlış hamle | `max-connections` artırıldı. | Daha fazla büyük response aynı anda memory tuttu. |
| Doğru hamle | Büyük JSON limitleri birlikte ayarlandı ve route concurrency düşürüldü. | Memory kontrol edildi, küçük hot read endpoint'ler korunur hale geldi. |

Başlangıç ayarı:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.jni.workers=2
reactor.rust.jni.queue-capacity=128
reactor.dubbo.max-inflight=48
reactor.dubbo.native-connections-per-endpoint=3
reactor.dubbo.native-async-workers=2

reactor.rust.http.max-response-body-bytes=16777216
reactor.rust.http.max-inflight-response-bytes=33554432
reactor.dubbo.max-response-bytes=16777216

reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=32
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=125

reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=12
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=125

reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6
reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms=150
```

Neden böyle: büyük JSON için sadece tek response limiti yetmez. Dubbo response limiti, HTTP response
limiti ve toplam in-flight response byte limiti birlikte düşünülür. Büyük JSON endpoint'e düşük
concurrency verilir; aksi halde aynı anda çok sayıda büyük response RSS'i yükseltir.

Ölçmeniz gerekenler: büyük JSON endpoint p99, küçük hot read endpoint p99, `503` oranı, response
byte metriği, warm load sonrası RSS ve 30-60 saniye idle sonrası RSS.

Bu senaryoda karşılaşabileceğiniz farklı durumlar ve doğrudan property karşılıkları:

**Büyük JSON response limit'e takılıyor**

- Önceki değer: `max-response-body-bytes=8388608`, `dubbo.max-response-bytes=8388608`
- Değişiklik: ikisini de `16777216` yapın; toplam limit için `max-inflight-response-bytes=33554432`
- Beklenen düzelme: büyük response reddedilmeden dönebilir.
- Bedel/dikkat: sadece tek response limitini büyütmek yetmez; toplam in-flight limiti de kontrol edilmeli.

**Büyük JSON route RSS'i yükseltiyor**

- Önceki değer: `catalog.db.customers.max-concurrent=10` veya `12`
- Değişiklik: `catalog.db.customers.max-concurrent=6`
- Beklenen düzelme: aynı anda tutulan büyük body sayısı azalır.
- Bedel/dikkat: büyük endpoint'in RPS'i düşebilir ama pod memory korunur.

**Küçük hot read'ler büyük JSON yüzünden yavaşlıyor**

- Önceki değer: tüm route'lar aynı budget'ta.
- Değişiklik: küçük read için `catalog.nested.max-concurrent=32`, büyük JSON için `catalog.db.customers.max-concurrent=6`
- Beklenen düzelme: küçük response'lar büyük response kuyruğundan ayrışır.
- Bedel/dikkat: route key'leri path'e göre doğru yazılmalı.

**Provider CPU boş, consumer tarafında p99 yüksek**

- Önceki değer: `native-connections-per-endpoint=1`, `native-async-workers=1`
- Değişiklik: `native-connections-per-endpoint=3`, `native-async-workers=2`
- Beklenen düzelme: Dubbo native data-plane daha fazla paralel iş taşıyabilir.
- Bedel/dikkat: provider CPU/DB doygunsa bu artış fayda vermez.

### CRM Komut Servisi: Create, Patch, Delete Trafiği Yüksek

Çağrı merkezi veya CRM ekranı müşteri yaratıyor, segment değiştiriyor, statü güncelliyor ve
bazen müşteri siliyor. Bunlar read değil, veri değiştiren command işlemleridir. Provider DB'ye yazar.
Bazı client'lar timeout görünce kendi tarafında tekrar deneyebilir.

API'lerin anlamı:

| Gerçek hayattaki iş | Sample endpoint | Trafik | Risk |
|---------------------|-----------------|--------|------|
| Müşteri yarat | `POST /api/v1/customers` | Yüksek | Duplicate create |
| Segment değiştir | `PATCH /api/v1/customers/{id}/segment` | Orta | Aynı müşteriye yarışan update |
| Statü değiştir | `PATCH /api/v1/customers/{id}/status` | Orta | Retry ile çakışan state |
| Müşteri sil | `DELETE /api/v1/customers/{id}` | Düşük | Geri alınması zor command |

Zaman akışı:

| Zaman | Ne oldu? | Gördüğünüz belirti |
|-------|----------|--------------------|
| İlk canlı kullanım | Read endpoint'ler iyi çalıştı. | Command route'larda occasional timeout görüldü. |
| Client retry yaptı | Aynı command tekrar geldi. | Provider ve DB üzerinde duplicate write baskısı oluştu. |
| Yanlış hamle | Consumer kuyruğu büyütüldü. | Problem gizlendi, p99 ve memory arttı. |
| Doğru hamle | Command concurrency düşük tutuldu, retry kapalı kaldı, idempotency planlandı. | Sistem daha tahmin edilebilir hale geldi. |

Başlangıç ayarı:

```properties
reactor.dubbo.retries=0
reactor.dubbo.timeout-ms=1000
reactor.dubbo.max-inflight=16

reactor.rust.route-admission.post.api.v1.customers.max-concurrent=4
reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=100

reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent=4
reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms=100

reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent=4
reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms=100

reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=2
reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms=100
```

Production kararı: command endpoint için "kuyruğa al, nasılsa sonra işler" yaklaşımı anti-pattern'dir.
Garantili tamamlanma gerekiyorsa idempotency key, outbox veya durable workflow ekleyin. Consumer
tarafı kısa timeout ve düşük concurrency ile provider/DB limitini görünür tutmalıdır.

Bu senaryoda karşılaşabileceğiniz farklı durumlar ve doğrudan property karşılıkları:

**Müşteri yaratma endpoint'i DB'yi zorluyor**

- Önceki değer: `post.customers.max-concurrent=8` veya `10`
- Değişiklik: `post.customers.max-concurrent=4`, `queue-timeout-ms=100`
- Beklenen düzelme: DB write pressure düşer, p99 daha tahmin edilebilir olur.
- Bedel/dikkat: peak write RPS düşer; bu doğru backpressure sinyalidir.

**Timeout sonrası aynı command tekrar geliyor**

- Önceki değer: `reactor.dubbo.retries=1` veya client-side blind retry.
- Değişiklik: `reactor.dubbo.retries=0`, client'a idempotency key zorunlu.
- Beklenen düzelme: duplicate write riski azalır.
- Bedel/dikkat: iş garantisi gerekiyorsa consumer queue değil durable workflow gerekir.

**Patch endpoint'leri birbirini eziyor**

- Önceki değer: segment/status route'ları aynı yüksek budget'ta.
- Değişiklik: segment ve status için ayrı ayrı `max-concurrent=4`.
- Beklenen düzelme: bir patch türü diğerini tamamen kilitlemez.
- Bedel/dikkat: gerçek domain'de aynı müşteri için optimistic lock/idempotency gerekir.

**Delete route nadir ama pahalı**

- Önceki değer: `delete.customers.id.max-concurrent=4`
- Değişiklik: `delete.customers.id.max-concurrent=2`
- Beklenen düzelme: geri alınması zor command daha kontrollü akar.
- Bedel/dikkat: admin kullanıcı spike anında 503 görebilir.

### Kampanya Listeleme Servisi: Çok Pod, Sıkı Memory, Kontrollü 503

Aynı namespace içinde çok sayıda küçük consumer pod var. Her pod kampanya listesi ve müşteri
özetini yoğun okuyor. Bazı admin veya command endpoint'ler seyrek çağrılıyor. Hedef maksimum RPS
değil, pod başına düşük RSS ile yatay ölçeklenmek.

API'lerin anlamı:

| Gerçek hayattaki iş | Sample endpoint | Trafik | Karar |
|---------------------|-----------------|--------|-------|
| Kampanya/katalog getir | `GET /api/v1/catalog/nested` | Çok yüksek | Orta budget |
| Müşteri özetini getir | `GET /api/v1/customers/db` | Yüksek | DB kapasitesine göre budget |
| Müşteri yarat | `POST /api/v1/customers` | Düşük | Küçük command budget |
| Müşteri sil | `DELETE /api/v1/customers/{id}` | Düşük | En küçük budget |

Kubernetes başlangıç ayarı:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "campaign-provider:20880,customer-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_RUST_JNI_WORKERS
    value: "1"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "1"
  - name: REACTOR_RUST_HTTP_MAX_CONNECTIONS
    value: "256"
  - name: REACTOR_RUST_RESPONSE_POOL_SMALL_CAPACITY
    value: "8"
  - name: REACTOR_RUST_RESPONSE_POOL_MEDIUM_CAPACITY
    value: "2"
```

Route budget:

```properties
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.post.api.v1.customers.max-concurrent=2
reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=1
```

Zaman akışı:

| Zaman | Ne oldu? | Gördüğünüz belirti |
|-------|----------|--------------------|
| İlk deployment | Çok sayıda küçük pod açıldı. | Toplam kapasite yüksek, pod başı RSS düşük. |
| Kampanya anı | İki hot read endpoint spike aldı. | Bazı cold route'lar 503 alabilir. |
| Trafik sakinleşti | Pod'lar idle kaldı. | RSS beklenen idle seviyeye geri dönmeli. |

Doğru yorum: bu senaryoda 503 tamamen kötü değildir. 503, pod memory limitini korumak için erken
reddetme sinyalidir. Asıl kötü olan, 503'ü gizlemek için kuyruğu büyütüp tüm servislerin p99'unu
yükseltmektir.

Bu senaryoda karşılaşabileceğiniz farklı durumlar ve doğrudan property karşılıkları:

**Pod başı RSS fazla, namespace toplam memory doluyor**

- Önceki değer: `max-connections=512`, `small-capacity=32`, `medium-capacity=8`
- Değişiklik: `max-connections=256`, `small-capacity=8`, `medium-capacity=2`
- Beklenen düzelme: her pod daha az idle/native buffer tutar.
- Bedel/dikkat: tek pod throughput düşer; yatay ölçekle dengeleyin.

**Kampanya anında hot read 503 oranı kabul edilemez seviyede**

- Önceki değer: `catalog.nested.max-concurrent=8`
- Değişiklik: `catalog.nested.max-concurrent=16`
- Beklenen düzelme: hot read daha fazla istek kabul eder.
- Bedel/dikkat: RSS ve provider CPU artışını ölçün.

**Admin/write route hot read'i etkiliyor**

- Önceki değer: admin/write route budget yüksek.
- Değişiklik: `post.customers.max-concurrent=2`, `delete.customers.id.max-concurrent=1`
- Beklenen düzelme: seyrek route'lar hot read kapasitesini çalmaz.
- Bedel/dikkat: admin işlemleri spike altında fail-fast davranır.

**503'ü azaltmak için global queue büyütüldü**

- Önceki değer: `jni.queue-capacity=512`
- Değişiklik: `jni.queue-capacity=128`, route budget ile ayrıştırma.
- Beklenen düzelme: global kuyruk şişmesi ve p99 artışı engellenir.
- Bedel/dikkat: her hot endpoint'e ayrı budget yazmanız gerekir.

### Çağrı Merkezi Lookup API'si: Memory Rahat, p99 Yüksek

Çağrı merkezi ekranı müşteri ve sipariş bilgisini sürekli okuyor. Pod memory limiti rahat,
ama kullanıcı ekranda bekleme görüyor. Burada hedef memory'yi en aşağı çekmek değil, p99'u makul
seviyeye indirmektir.

API'lerin anlamı:

| Gerçek hayattaki iş | Sample endpoint | Trafik | Karar |
|---------------------|-----------------|--------|-------|
| Müşteri lookup | `GET /api/v1/customers/db` | Çok yüksek | Daha fazla Dubbo kapasitesi |
| Sipariş/katalog lookup | `GET /api/v1/catalog/nested` | Çok yüksek | Daha fazla route budget |
| Büyük müşteri geçmişi | `GET /api/v1/catalog/db/customers` | Orta | Ayrı büyük JSON budget |
| Command endpoint'ler | `POST/PATCH/DELETE` | Düşük | Küçük budget korunur |

Başlangıç ayarı:

```properties
reactor.runtime.profile=micro-dubbo
reactor.rust.jni.workers=2
reactor.dubbo.max-inflight=64
reactor.dubbo.native-connections-per-endpoint=4
reactor.dubbo.native-async-workers=2
reactor.rust.http.max-connections=768
```

Zaman akışı:

| Zaman | Ne oldu? | Gördüğünüz belirti |
|-------|----------|--------------------|
| İlk ölçüm | Average latency iyi görünür. | p99 kötü olduğu için kullanıcı bekleme hisseder. |
| Kapasite artırıldı | Dubbo inflight ve native connection artırıldı. | Hot read p99 iyileşebilir. |
| Admission kaldırıldı | Tüm endpoint'ler serbest bırakıldı. | Bir yavaş provider route'u tüm servisi yavaşlatır. |
| Doğru hamle | Route budget korunur, sadece hot read kapasitesi artırılır. | p99 dengelenir, cold route'lar sistemi boğmaz. |

Bu profile geçmeden önce şunları kontrol edin:

| Kontrol | Gerekli sinyal |
|---------|----------------|
| Provider CPU | Headroom var. |
| Provider DB pool | Saturated değil. |
| Consumer RSS | Warm load sonrası pod limitinin altında. |
| `503` oranı | Ayar sonrası azalıyor; sadece daha büyük kuyrukla gizlenmiyor. |
| p99 | Sadece average latency değil, hot endpoint p99 değerleri de iyileşiyor. |

Bu senaryoda karşılaşabileceğiniz farklı durumlar ve doğrudan property karşılıkları:

**Provider CPU boş, consumer p99 yüksek**

- Önceki değer: `dubbo.max-inflight=24`, `native-connections-per-endpoint=2`
- Değişiklik: `dubbo.max-inflight=64`, `native-connections-per-endpoint=4`
- Beklenen düzelme: hot lookup endpoint'leri daha fazla paralel Dubbo çağrısı taşıyabilir.
- Bedel/dikkat: RSS ve provider DB pool tekrar ölçülmeli.

**Sadece müşteri lookup yavaş**

- Önceki değer: her hot route aynı budget'ta.
- Değişiklik: `customers.db.max-concurrent=12`, catalog budget aynı kalır.
- Beklenen düzelme: problemli route ayrı ayarlanır, diğer endpoint'ler etkilenmez.
- Bedel/dikkat: DB wait artarsa tekrar düşürün.

**Büyük müşteri geçmişi küçük lookup'ları bozuyor**

- Önceki değer: büyük JSON route `max-concurrent=12`
- Değişiklik: büyük JSON route `max-concurrent=6`, küçük lookup route'ları yüksek kalır.
- Beklenen düzelme: küçük lookup p99 korunur.
- Bedel/dikkat: büyük JSON endpoint daha düşük useful RPS verir.

**Admission tamamen kaldırıldı**

- Önceki değer: route budget yok.
- Değişiklik: route budget'ları geri koyun, sadece hot read değerlerini artırın.
- Beklenen düzelme: bir yavaş provider route'u tüm servisi kilitlemez.
- Bedel/dikkat: config biraz daha detaylı olur ama üretimde daha güvenlidir.

## `rust-java-rest` 3.2.2 Bu Örnekte Ne Değiştiriyor?

Bu örnek artık `rust-java-rest` `3.2.2` kullanır. Uygulama kodu modeli değişmez: handler'lar,
service adapter'ları, configuration class'ları ve business kararlar Java'da kalır. Değişiklik daha
çok handler'ların altında çalışan runtime yolundadır.

| v3.2.2 değişikliği | Bu örnekte etkisi |
|-----------------|-------------------|
| Daha düşük retention yapan response pool'lar | Trafik düşükken consumer daha az native response buffer tutar. |
| Bounded in-flight response byte limiti | Büyük veya yavaş response'lar memory kullanımını limitsiz büyütemez. |
| UTF-8 response/path/query düzeltmeleri | Request değerleri ve response bytes UTF-8 ise Türkçe karakterler güvenli taşınır. |
| Raw/precomputed response yolunun olgunlaşması | Provider JSON `byte[]` döner, consumer `RawResponse.json(bytes)` ile DTO parse/serialize yapmadan döner. |
| Startup component/route index'leri | Sample broad classpath scan fallback yapmadan açılabilir; index eskiyse startup erken fail eder. |
| Route-level admission | Dubbo çağıran route'lar global JNI kuyruğunu doldurmadan önce bounded in-flight limit kullanır. |
| Route diagnostics ayrımı | Benchmark-only comparison route'lar production heavy-object-graph sayımına karışmaz. |
| Anon evidence gate | RSS; heap, JIT, class metadata, direct buffer, Rust-accounted memory, stack budget ve residual anon olarak ayrılır. |
| Conservative idle native trim | Düşük trafikli consumer pod'ları idle sonrası warmed native anonymous memory reclaim edebilir; default değil, opt-in kalır. |
| Daha açık low-RSS tuning | Sample properties `micro-dubbo` kullanır: REST dar kalır, Dubbo native/static-provider ayarlarıyla çalışır. |
| Production artifact temizliği | Sample normal framework dependency kullanır; framework demo/sample class'ları production-like RSS ölçümüne karışmaz. |
| Açık property override düzeltmesi | `rust-spring.properties` içine yazdığınız değerler runtime profile default'ları tarafından ezilmez. |

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

### Production Dependency ve RSS Ölçümü

Bu sample normal `rust-java-rest` Maven artifact'ine bağlıdır:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>rust-java-rest</artifactId>
  <version>3.2.2</version>
</dependency>
```

Bu normal artifact framework repo'sundaki demo handler'ları, benchmark route'larını veya Dubbo
sample application class'larını içermez. Bu class'lar sadece `rust-java-rest-*-sample.jar` içinde
durur.

Bu repo için kuralı şöyle okuyun:

| Ne çalıştırıyorsunuz? | Doğru anlamı |
|-----------------------|--------------|
| Normal Maven dependency ile bu consumer uygulaması | Production-like consumer şekli |
| `zookeeper-discovery` profile ile bu consumer | Kubernetes/ZooKeeper discovery şekli |
| Framework `rust-java-rest-*-sample.jar` | Framework demo route'ları; bu consumer'ın memory resmi değil |
| Framework `target/classes` | Sadece lokal framework debugging |

Memory ölçerken bu consumer image'ını veya gerçek uygulama image'ınızı kullanın. Bu consumer'ın pod
memory limitini framework sample jar sonucuna göre belirlemeyin.

## Diğer Projelerle İlişkisi

Bu repo şunlara bağlıdır:

| Bağımlılık | Neden gerekli? |
|------------|----------------|
| `rust-java-rest` | Rust Hyper HTTP server, Java handler modeli, DI, `RawResponse` ve runtime config sağlar. |
| `java-rust-dubbo` | Bu örnekte kullanılan hafif Dubbo consumer adapter'ını sağlar. |
| `hessian-lite` | POST/PATCH/DELETE command örnekleri gibi argüman taşıyan Dubbo method'ları için gerekir. |
| `rest-sample-dubbo-provider` | Lokal uçtan uca test için kullanılan örnek Dubbo provider'dır. |
| ZooKeeper | Opsiyoneldir. Sadece discovery modunda gerekir. |

## Bu Sample'daki Maven Profile'ları

Sample üç farklı consumer dependency şekli sunar:

**`full-dubbo-consumer`**

- Ne kullanır: full `java-rust-dubbo` artifact ve `hessian-lite`; varsayılan aktiftir.
- Ne için uygun: POST/PATCH/DELETE dahil tüm sample endpoint'lerini çalıştırmak.
- Sınırı: en küçük read-only path'e göre classpath daha büyüktür.

**`native-static-consumer`**

- Ne kullanır: `java-rust-dubbo` `native-static` classifier; Hessian ve ZooKeeper dependency yoktur.
- Ne için uygun: static provider, no-arg `byte[]` read endpoint'leri için en küçük classpath yüzeyi.
- Sınırı: argüman taşıyan Dubbo method'ları full profile ister.

**`zookeeper-discovery`**

- Ne kullanır: full `java-rust-dubbo`, `hessian-lite` ve ZooKeeper client dependency ekler.
- Ne için uygun: Kubernetes veya provider discovery'nin mutlaka ZooKeeper'dan gelmesi gereken ortamlar.
- Sınırı: consumer process'e Java ZooKeeper class/thread maliyeti ekler.

Tüm örnekleri kopyala-çalıştır yapmak istiyorsanız default/full profile kullanın:

```powershell
mvn -q test
mvn -q exec:java
```

Sadece en küçük read-only pass-through yolu test edecekseniz native-static profile kullanın:

```powershell
mvn -q -Pnative-static-consumer test
mvn -q -Pnative-static-consumer exec:java
```

`native-static-consumer` ile `/api/v1/catalog/nested` ve `/api/v1/catalog/db/customers` gibi no-argument read endpoint'lerini çağırın. POST/PATCH/DELETE command örnekleri method argümanı taşıdığı için bilinçli olarak Hessian request encoding kullanan full profile gerektirir.

Consumer provider'ları ZooKeeper'dan bulmak zorundaysa ZooKeeper profilini kullanın. Bu profil kendi başına yeterlidir; `full-dubbo-consumer` ile birlikte kullanmayın.

```powershell
$env:SAMPLE_DUBBO_DISCOVERY="zookeeper"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://zookeeper:2181"
mvn -q -Pzookeeper-discovery exec:java
```

Provider repo:

```text
git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
```

Varsayılan mod static provider modudur:

```text
consumer -> 127.0.0.1:20880
```

Bu path şu ayarlarla küçük tutulur:

```properties
reactor.runtime.profile=micro-dubbo
reactor.dubbo.enabled=true
reactor.dubbo.transport=native
reactor.dubbo.runtime-profile=micro-dubbo
reactor.dubbo.providers=127.0.0.1:20880
reactor.dubbo.native-connections-per-endpoint=1
reactor.dubbo.native-async-workers=1
reactor.dubbo.native-async-queue-capacity=32
```

Küçük pod'larda bu properties değerlerini OpenJ9 micro-RSS JVM opsiyonlarıyla birlikte kullanın:

```bash
-Xms8m -Xmx48m -Xss256k -Xquickstart -Xtune:virtualized -Xshareclasses:none -XX:ActiveProcessorCount=1
```

`-Xnojit` sadece çok düşük trafik alan ve memory'nin Java CPU throughput'tan daha önemli olduğu
servislerde düşünülmelidir. RPC-heavy route'larda benchmark yapmadan default kullanmayın.

### Pratik Pod Memory Başlangıç Noktaları

Bu değerler garanti değildir; bu sample'ın şekli için güvenli başlangıç limitleridir. RSS; JVM,
container image, CPU limit, trafik, response boyutu ve ZooKeeper discovery'nin açık olup olmamasına
göre değişir.

**Static provider, düşük trafik, sadece JSON pass-through**

- Başlangıç limiti: `128Mi`
- Neden: Rust-Java REST küçük kalır; consumer Java ZooKeeper/Dubbo Netty runtime yüklemez.

**Static provider ve DB-backed Dubbo route, orta yük**

- Başlangıç limiti: `160Mi`
- Neden: DB çağrıları spike anında queue ve retained buffer baskısını artırır.

**Consumer içinde ZooKeeper discovery açık**

- Başlangıç limiti: `160Mi` veya üstü
- Neden: Java ZooKeeper client thread/class/session state ekler. Sadece gerekiyorsa kullanın.

**Daha yüksek concurrency Dubbo workload**

- Başlangıç limiti: `192Mi` seviyesinden ölçün.
- Neden: route admission ve native connection birlikte artırılmalı; p99, 503 oranı ve RSS birlikte izlenmeli.

Servis günde az sayıda çağrı alıyorsa c1000 benchmark'a göre pod limiti seçmeyin. Static provider
moduyla başlayın, route admission bounded kalsın ve 30-60 saniye idle sonrası RSS'i kontrol edin.

ZooKeeper discovery modu:

```text
consumer -> ZooKeeper -> provider URL -> Dubbo provider
```

ZooKeeper modunu sadece discovery gerçekten gerekiyorsa kullanın. Bu sample'da ZooKeeper discovery
için `sample.dubbo.discovery=zookeeper` veya `SAMPLE_DUBBO_DISCOVERY=zookeeper` verilir; bu durumda
Java ZooKeeper client'i REST process içinde yüklenir.

## Dubbo Çağrıları İçin Route Admission

Sample, Dubbo-backed route'larda `@RouteAdmission` kullanır:

```java
@GetMapping(value = "/nested", responseType = RawResponse.class)
@RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
    return catalogClient.nestedCatalogJsonAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

Aynı değerler `rust-spring.properties` içinde de vardır. Böylece kodu değiştirmeden tune
edebilirsiniz:

```properties
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16
reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms=100
reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=8
reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms=150
```

Bu özelliği RPC, DB veya HTTP server'dan daha yavaşlayabilecek başka bir dependency çağıran
route'larda kullanın. Amaç trafiği agresif şekilde reddetmek değildir; amaç tek bir yavaş route'un
tüm worker'ları doldurmasını ve deep queue yüzünden RSS/p99 büyümesini engellemektir.

| Workload | İlk yapılacak ayar |
|----------|--------------------|
| Düşük trafik, memory-first servis | Checked-in limitleri koruyun. |
| c256 altında daha fazla 200 response gerekiyor | Önce route `max-concurrent` artırın, sonra RSS ve p99 ölçün. |
| RPS kabul edilebilir ama p99 büyüyor | `queue-timeout-ms` veya provider-side concurrency düşürün; sadece worker artırmayın. |
| DB route yavaşlıyor | Consumer route limitini provider service limiti ve Hikari max pool ile hizalayın. |

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

Bu sample artık üç Dubbo interface consume eder:

| REST alanı | Dubbo interface | Method | Neden ayrı? |
|------------|-----------------|--------|-------------|
| Catalog read | `NestedCatalogService` | `getNestedCatalogJson()` | Catalog payload DB-backed customer read'den bağımsızdır. |
| Customer read | `CustomerQueryService` | `getDatabaseCustomersJson()` | DB-backed customer query'nin lifecycle, repository ve scaling profili ayrıdır. |
| Customer write | `CustomerCommandService` | `createCustomer(...)`, `patchCustomer*`, `deleteCustomer(...)` | Write command'lerin concurrency, idempotency ve DB mutation riskleri read path'ten farklıdır. |

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

Bu örnekteki class ve interface'ler response DTO değildir. DTO gerekiyorsa normal tercih Java
`record` olmalıdır; runtime davranışı, config, handler ve Dubbo interface'leri Java `class` veya
`interface` olarak kalır.

| Tip | Rol | HTTP JSON contract tipi |
|-----|-----|-------------------------|
| `RestSampleDubboConsumerApplication` | Process ve HTTP server başlatır. | Java class (startup/runtime); HTTP JSON body üretmez. |
| `ConsumerProperties` | Runtime property okur ve validate eder. | Java class (config); HTTP JSON body üretmez. |
| `DubboConsumerConfiguration` | Dubbo client bean'lerini oluşturur/kapatır. | Java class (resource/config); HTTP JSON body üretmez. |
| `NestedCatalogClient` | Native Dubbo method invoker adapter'ıdır. | Java class (RPC adapter); JSON DTO veya POJO response contract değildir. |
| `CustomerQueryClient` | Customer Dubbo interface adapter'ıdır. | Java class (RPC adapter); JSON DTO veya POJO response contract değildir. |
| `CatalogHandler` | REST endpoint davranışını taşır. | Java class (HTTP handler/controller); body tipi değildir. Response `RawResponse` veya Java `record` DTO olabilir. |
| `CustomerHandler` | Customer REST endpoint davranışını taşır. | Java class (HTTP handler/controller); body tipi değildir. Request/response DTO ayrı Java `record` olmalıdır. |
| `HealthHandler` | Health endpoint davranışını taşır. | Java class (HTTP handler/controller); body tipi değildir. |
| `NestedCatalogService` | Dubbo interface kontratıdır. | Java interface (Dubbo RPC contract); HTTP JSON body tipi değildir. |
| `CustomerQueryService` | İkinci Dubbo interface kontratıdır. | Java interface (Dubbo RPC contract); HTTP JSON body tipi değildir. |

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
    <version>3.2.2</version>
</dependency>

<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo</artifactId>
    <version>0.1.0-rc3</version>
</dependency>

<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>hessian-lite</artifactId>
    <version>4.0.3</version>
</dependency>
```

`hessian-lite`, POST/PATCH/DELETE command örnekleri için gerekir; çünkü bu Dubbo method'ları argüman
taşır. Ayrıca Dubbo method'u typed `record`, `List`, `Map`, `String` veya primitive wrapper dönüyorsa
legacy Hessian codec path'i gerekir. No-argument `byte[]` read path native byte-array fast path'i
kullanabilir ve Hessian request encode yoluna girmez.

Demo kendi içinde Dubbo interface source'larını taşır:

```java
package com.reactor.rust.dubbo.sample;

public interface NestedCatalogService {
    byte[] getNestedCatalogJson();

    String getCatalogTitle();

    int countCatalogItems();

    CatalogInfo getCatalogInfo();

    List<CatalogItem> listFeaturedItems(int limit);

    Map<String, String> getCatalogAttributes();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();

    CustomerSummary getCustomer(long customerId);

    List<CustomerSummary> findCustomersBySegment(String segment, int limit);

    CustomerStats getCustomerStats();

    boolean customerExists(long customerId);

    String getCustomerDisplayName(long customerId);
}

public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);

    CustomerMutationResult createCustomerTyped(CreateCustomerCommand command);

    CustomerMutationResult patchCustomerStatusTyped(long customerId, String status, String requestId);
}
```

Gerçek sistemde bu interface'ler provider ve consumer tarafından kullanılan ortak bir `*-api` jar
içinden gelmelidir. Ayrı servislerde küçük farklarla copy/paste contract üretmeyin.

### Dubbo Method Veri Yapısı Kataloğu

**1. `byte[]` UTF-8 JSON, argümansız**

- Örnek method: `getNestedCatalogJson()`
- Consumer endpoint: `GET /api/v1/catalog/nested`
- Ne zaman: provider JSON shape'in sahibiyse ve consumer sadece forward edecekse.
- Maliyet: en düşük. Native no-arg byte-array fast path çalışır; consumer DTO graph kurmaz.

**2. `String`**

- Örnek method: `getCatalogTitle()`
- Consumer endpoint: `GET /api/v1/catalog/title`
- Ne zaman: küçük label, başlık veya tek değer dönecekseniz.
- Maliyet: küçük String allocation ve REST tarafında JSON wrapper.

**3. Primitive/scalar**

- Örnek method: `countCatalogItems()`, `customerExists(id)`
- Consumer endpoint: `GET /api/v1/catalog/count`, `GET /api/v1/customers/{id}/exists`
- Ne zaman: count, flag veya ucuz lookup kararı.
- Maliyet: typed çağrı için Hessian decode vardır; büyük object graph yoktur.

**4. Java `record`**

- Örnek method: `getCatalogInfo()`, `getCustomer(id)`
- Consumer endpoint: `GET /api/v1/catalog/info`, `GET /api/v1/customers/db/{id}`
- Ne zaman: consumer field'ları okuyup branching, validation veya enrichment yapacaksa.
- Maliyet: Hessian record oluşturur, sonra REST JSON serialize eder.

**5. `List<record>`**

- Örnek method: `listFeaturedItems(limit)`, `findCustomersBySegment(segment, limit)`
- Consumer endpoint: `GET /api/v1/catalog/items`, `GET /api/v1/customers/db/by-segment`
- Ne zaman: küçük ve bounded sayfalar.
- Maliyet: liste + her item için record allocation; `limit` her zaman bounded olmalı.

**6. `Map<String,String>`**

- Örnek method: `getCatalogAttributes()`
- Consumer endpoint: `GET /api/v1/catalog/attributes`
- Ne zaman: küçük metadata çantası.
- Maliyet: Map allocation ve JSON wrapper; büyük dinamik payload için kullanmayın.

**7. `record` command input ve `record` output**

- Örnek method: `createCustomerTyped(CreateCustomerCommand)`
- Consumer endpoint: `POST /api/v1/customers/typed`
- Ne zaman: daha okunabilir typed business command contract istiyorsanız.
- Maliyet: request encode + response decode + REST serialization.

**8. `byte[]` command input ve `byte[]` JSON output**

- Örnek method: `createCustomer(byte[])`
- Consumer endpoint: `POST /api/v1/customers`
- Ne zaman: en düşük allocation command pass-through.
- Maliyet: request/response bytes taşınır; validation provider'da kalır.

Production kuralı: record daha okunabilir diye tüm `byte[]` method'larını record'a çevirmeyin.
Consumer typed business data ile karar verecekse record kullanın. Consumer sadece provider JSON'unu
HTTP'ye taşıyorsa `byte[] + RawResponse` yolunu koruyun.

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
JVM system property (-Dkey=value)
  > environment variable
  > yüklenen rust-spring.properties
  > framework internal defaults
```

Repo içindeki `src/main/resources/rust-spring.properties` uygulama classpath'ine paketlenir ve
baseline konfigürasyon gibi çalışır. Kubernetes ve Docker ortamlarında ortama göre değişen değerler
için environment variable veya JVM `-D...` seçeneklerini tercih edin. Mount edilen bir
`rust-spring.properties` dosyasının packaged dosyanın üstüne yazacağını varsaymayın; framework önce
classpath dosyasını yükler, filesystem path'leri sadece classpath dosyası yoksa dener.

Aynı değeri üç farklı şekilde verebilirsiniz:

```properties
# src/main/resources/rust-spring.properties
sample.dubbo.discovery=zookeeper
reactor.dubbo.registry-address=zookeeper://127.0.0.1:2181
```

```powershell
# JVM system properties. En yüksek öncelik.
java `
  -Dsample.dubbo.discovery=zookeeper `
  -Dreactor.dubbo.registry-address=zookeeper://127.0.0.1:2181 `
  -cp "classes;dependency/*" `
  com.reactor.sample.dubbo.consumer.app.RestSampleDubboConsumerApplication
```

```yaml
# Kubernetes environment variables. Deployment için önerilen yol.
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
```

Tüm property'ler environment variable olarak override edilebilir. Dönüşüm kuralı:

```text
sample.dubbo.discovery -> SAMPLE_DUBBO_DISCOVERY
reactor.dubbo.native-connections-per-endpoint -> REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent -> REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_MAX_CONCURRENT
```

Kural basit: key'i uppercase yapın, `.` karakterlerini `_` yapın, `-` karakterlerini `_` yapın.

Container command'ını değiştirmeden JVM system property vermek isterseniz `JAVA_TOOL_OPTIONS`
kullanabilirsiniz:

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-Dreactor.dubbo.max-inflight=8 -Dreactor.dubbo.timeout-ms=1000"
```

Runtime default değerleri kodda tutulmaz. Eksik veya hatalı property startup sırasında fail-fast olur.
Repo içindeki değerler bilinçli olarak low-RSS yönlüdür; bu değerleri ancak throughput, p99 latency,
503 oranı ve RSS'i birlikte ölçtükten sonra büyütün.

Önemli property'ler:

| Property | Açıklama |
|----------|----------|
| `server.port` | Rust-Java REST HTTP portu. |
| `reactor.runtime.profile` | Runtime preset'i. Bu sample düşük RSS ve native Dubbo için `micro-dubbo` kullanır. |
| `reactor.startup.component-index.*` | Component index zorunlu tutulur; startup broad classpath scan'e sessizce dönmez. |
| `reactor.startup.route-index.*` | Route index doğrulanır; route metadata eskiyse startup fail-fast olur. |
| `reactor.rust.jni.workers` | JNI worker sayısı. Low RSS için küçük tutulur. |
| `reactor.rust.http.max-response-body-bytes` | Tek response body limiti. |
| `reactor.rust.http.max-inflight-response-bytes` | Toplam in-flight response byte limiti. |
| `reactor.rust.http.max-connections` | Kabul edilecek HTTP connection üst limiti. Kubernetes içinde bounded kalmalı. |
| `reactor.rust.response-pool.small-capacity` / `medium-capacity` / `large-capacity` | Native response buffer retention limitleri. Küçük değer idle RSS'i düşürür; büyük değer steady load altında allocation churn azaltır. |
| `reactor.rust.json.writer-retain-max-bytes` | Retain edilecek maksimum JSON writer buffer boyutu. Ara sıra gelen büyük response'ların kalıcı retained memory büyütmesini engeller. |
| `reactor.rust.native-cache.max-bytes` | Opsiyonel native response cache için hard limit. Endpoint bilinçli cache edilebilir değilse küçük veya kullanılmamış kalmalı. |
| `reactor.rust.route-admission.*` | Global JNI queue öncesinde route bazlı in-flight ve queue timeout limiti. |
| `sample.dubbo.discovery` | `static` veya `zookeeper`. |
| `reactor.dubbo.providers` | Static provider listesi. Tek Service DNS veya virgülle ayrılmış liste olabilir: `rest-sample-dubbo-provider:20880` veya `customer-provider:20880,order-provider:20880`. |
| `reactor.dubbo.registry-address` | Discovery modunda ZooKeeper adresi. |
| `reactor.dubbo.timeout-ms` | RPC timeout. |
| `reactor.dubbo.max-inflight` | Bounded RPC concurrency. |
| `reactor.dubbo.native-connections-per-endpoint` | Provider başına native Dubbo TCP connection pool boyutu. Memory-first servislerde düşük tutun; sadece p99/RSS ölçümüyle artırın. |

Hızlı semptom rehberi:

| Bunu görüyorsanız | Önce şu key'lere bakın | İlk kontrol |
|-------------------|-------------------------|-------------|
| Request body reject oluyor veya create/patch payload küçük kalıyor | `reactor.rust.http.max-request-body-bytes`, `reactor.rust.http.max-inflight-body-bytes` | Tek body ve toplam in-flight body limitini birlikte artırın. |
| Provider JSON çok büyük diye reject oluyor | `reactor.dubbo.max-response-bytes`, `reactor.rust.http.max-response-body-bytes`, `reactor.rust.http.max-inflight-response-bytes` | Üç limit de payload'a izin vermeli. |
| Trafik spike altında çok `503` var | Route-specific `reactor.rust.route-admission.*`, `reactor.dubbo.max-inflight` | Provider CPU/DB pool boşluğu varsa route budget artırın. |
| p99 büyüyor ama RSS hâlâ düşük | `reactor.dubbo.native-connections-per-endpoint`, `reactor.dubbo.native-async-workers`, route queue timeout | Çok Java worker eklemeden önce connection reuse iyileştirin. |
| Trafik durduktan sonra RSS yüksek kalıyor | `reactor.rust.response-pool.*`, `reactor.rust.json.writer-retain-max-bytes`, opsiyonel `reactor.rust.native-trim.*` | Idle trim sadece düşük trafikli podlarda açılmalı ve p99 ölçülmeli. |
| Startup component/route index hatası veriyor | `reactor.startup.component-index.*`, `reactor.startup.route-index.*`, `reactor.startup.scan.fallback-enabled` | Handler/route ekledikten sonra index dosyalarını güncelleyin. |
| Kubernetes static consumer provider bulamıyor | `sample.dubbo.discovery`, `reactor.dubbo.providers` | `SAMPLE_DUBBO_DISCOVERY=static` ise provider Service DNS, port ve readiness'ı kontrol edin. `127.0.0.1` kullanmayın. |
| Kubernetes ZooKeeper consumer provider bulamıyor | `sample.dubbo.discovery`, `reactor.dubbo.registry-address`, `reactor.dubbo.registry-root` | `zookeeper-discovery` ile build edin, registry DNS'i doğru verin ve provider node'unun registry altında oluştuğunu kontrol edin. |
| Docker static consumer yanlış yere bağlanıyor | `reactor.dubbo.providers` | Docker network içinde `127.0.0.1` değil container/service DNS kullanın. |
| Write command yavaş veya dengesiz | `reactor.dubbo.retries`, command route admission key'leri, `reactor.dubbo.timeout-ms` | Retry `0` kalsın; bounded queue ve timeout'u tune edin. |
| Çok sayıda idle HTTP client kaynak tutuyor | `reactor.rust.http.max-connections`, `reactor.rust.http.idle-timeout-ms`, `reactor.rust.http.keep-alive-enabled` | Keep-alive kapatmadan önce idle timeout'u düşürün. |

### Tam Runtime Property Rehberi

Bu bölüm sample içindeki `src/main/resources/rust-spring.properties` dosyasındaki tüm key setidir.
Bu değerleri packaged baseline olarak düşünün. Kubernetes veya standalone runtime'da sadece
senaryonuza uyan değerleri `-D...` veya environment variable ile override edin.

Server ve startup:

| Property | Default | Ne işe yarar / ne zaman değişir? |
|----------|---------|----------------------------------|
| `server.port` | `8080` | HTTP portu. Pod/container portu farklıysa değiştirin. |
| `server.host` | `0.0.0.0` | Bind adresi. Container içinde `0.0.0.0` kalsın; sadece lokal-only testte `127.0.0.1` kullanın. |
| `reactor.runtime.profile` | `micro-dubbo` | Memory-first REST + native Dubbo preset. Sadece bilinçli olarak daha büyük/balanced runtime'a geçerken değiştirin. |
| `reactor.startup.component-index.enabled` | `true` | Broad classpath scan yerine `components.idx` kullanır. Production-like startup için açık kalsın. |
| `reactor.startup.component-index.required` | `true` | Component index yoksa startup fail eder. Sadece lokal prototipte kapatın. |
| `reactor.startup.route-index.validate` | `true` | Actual route listesini `routes.idx` ile doğrular. Eski route metadata'yı yakalamak için açık kalsın. |
| `reactor.startup.route-index.required` | `true` | Route index yoksa startup fail eder. Sadece lokal deneylerde kapatın. |
| `reactor.startup.scan.fallback-enabled` | `false` | Sessiz classpath scan fallback'i engeller. RSS/startup predictability önemliyse `false` kalsın. |

HTTP ve request/response limitleri:

| Property | Default | Ne işe yarar / ne zaman değişir? |
|----------|---------|----------------------------------|
| `reactor.rust.http.max-request-body-bytes` | `1048576` | Maksimum request body. Büyük POST/PATCH payload varsa artırın; upload yolu gibi sınırsız kullanmayın. |
| `reactor.rust.http.max-response-body-bytes` | `8388608` | Tek REST response üst limiti. Büyük provider JSON için Dubbo ve in-flight response limitleriyle birlikte artırın. |
| `reactor.rust.http.max-inflight-body-bytes` | `16777216` | Toplam in-flight request body bütçesi. Küçük pod için düşürülebilir; 413/503 davranışını ölçerek artırın. |
| `reactor.rust.http.max-inflight-response-bytes` | `16777216` | Toplam in-flight response byte bütçesi. Büyük response reject oluyorsa global worker artırmadan önce buna bakın. |
| `reactor.rust.http.max-connections` | `512` | Kabul edilen HTTP connection üst limiti. Gerçek connection baskısı varsa artırın; çok yüksek değer RSS artırabilir. |
| `reactor.rust.http.max-request-header-bytes` | `16384` | Request header byte limiti. Büyük token/header varsa artırın. |
| `reactor.rust.http.max-request-headers` | `64` | Header sayısı limiti. Çok sayıda tracing/security header gönderen client'lar için kullanılır. |
| `reactor.rust.http.header-read-timeout-ms` | `5000` | Header okuma timeout'u. Slow client'ları daha hızlı düşürmek için azaltın; yavaş networkte artırın. |
| `reactor.rust.http.request-body-timeout-ms` | `10000` | Body okuma timeout'u. Büyük command body veya yavaş client varsa tune edilir. |
| `reactor.rust.http.idle-timeout-ms` | `30000` | Keep-alive idle timeout. Çok idle client olan küçük podlarda düşürün; connection reuse için artırın. |
| `reactor.rust.http.http1-only-enabled` | `true` | Sample'ı daha küçük HTTP/1 surface üzerinde tutar. HTTP/2 bilinçli gerekiyorsa değiştirin. |
| `reactor.rust.http.keep-alive-enabled` | `true` | Connection reuse sağlar. Sadece tek-request connection testlerinde kapatın. |

Runtime, queue, pool ve memory:

| Property | Default | Ne işe yarar / ne zaman değişir? |
|----------|---------|----------------------------------|
| `reactor.rust.log.level` | `error` | Native log seviyesi. Diagnose için geçici artırın; hot path'te düşük kalsın. |
| `reactor.rust.java.log.level` | `warn` | Java framework log seviyesi. Startup/config debug için artırın; steady production'da düşük tutun. |
| `reactor.rust.jni.workers` | `1` | Java handler worker sayısı. Daha çok concurrent Java iş için artırılır; RSS ve CPU contention artar. |
| `reactor.rust.jni.queue-capacity` | `128` | Global JNI queue limiti. Artırmak overload'u saklayabilir; önce route admission kullanın. |
| `reactor.rust.response-pool.small-capacity` | `8` | Küçük native response buffer retention. Büyük değer allocation churn azaltır; küçük değer idle RSS düşürür. |
| `reactor.rust.response-pool.medium-capacity` | `2` | Medium response buffer retention. Küçük podlarda düşük kalsın. |
| `reactor.rust.response-pool.large-capacity` | `1` | Large response buffer retention. Sadece tekrar eden büyük response churn ölçülürse artırın. |
| `reactor.rust.response-pool.huge-capacity` | `1` | Huge response buffer retention. Servis sık sık büyük bounded payload dönmüyorsa düşük kalsın. |
| `reactor.rust.websocket.max-frame-bytes` | `262144` | WebSocket frame limiti. Bu sample REST/Dubbo-first; WebSocket route eklenirse tune edilir. |
| `reactor.rust.websocket.outbound-queue-capacity` | `16` | Session başına outbound WebSocket queue. Slow consumer memory büyütmesin diye bounded kalmalı. |
| `reactor.rust.websocket.send-timeout-ms` | `1000` | WebSocket send timeout. Slow client kabul ediliyorsa artırın. |
| `reactor.rust.runtime.worker-threads` | `1` | Native runtime worker sayısı. Daha yüksek I/O concurrency için RSS/p99 ölçerek artırın. |
| `reactor.rust.runtime.max-blocking-threads` | `1` | Native blocking worker limiti. File/blocking iş bilinçli kullanılmıyorsa düşük kalsın. |
| `reactor.rust.runtime.thread-stack-bytes` | `262144` | Native runtime thread stack. Daha küçüğünü sadece stack-depth smoke test geçerse kullanın. |
| `reactor.rust.native-cache.max-entries` | `0` | Native response cache entry sayısı. `0` disabled demektir; sadece bilinçli cacheable response'larda açın. |
| `reactor.rust.native-cache.max-bytes` | `0` | Native response cache byte limiti. Cache açılırsa `max-entries` ile birlikte set edilmeli. |
| `reactor.rust.native-cache.ttl-ms` | `300000` | Native cache TTL. Cache disabled ise etkisizdir. |
| `reactor.rust.async.max-inflight` | `64` | Framework async response limiti. Async completion queue büyüyorsa artırın; p99 ve memory ölçün. |
| `reactor.rust.async.response-timeout-ms` | `1500` | Async response timeout. Dubbo timeout ve HTTP budget ile hizalı olmalı. |
| `reactor.rust.json.writer-initial-bytes` | `4096` | Direct JSON writer başlangıç buffer'ı. Sürekli büyük direct JSON üretiliyorsa artırın. |
| `reactor.rust.json.writer-retain-max-bytes` | `32768` | Retain edilecek maksimum JSON writer buffer. Idle RSS için düşürün; tekrar eden medium JSON için artırın. |

Route admission:

| Property | Default | Ne işe yarar / ne zaman değişir? |
|----------|---------|----------------------------------|
| `reactor.rust.route-admission.enabled` | `true` | Route-level bounded overload kontrolünü açar. Dubbo-backed route'larda açık kalsın. |
| `reactor.rust.route-admission.default-max-concurrent` | `0` | Global default route limiti. `0` default cap yok demektir; sample explicit route key kullanır. |
| `reactor.rust.route-admission.default-queue-timeout-ms` | `0` | Global default queue wait. `0` default olarak queue yapılmaz demektir. |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent` | `16` | Read-heavy nested catalog cap. Provider hızlıysa artırın; provider CPU/RSS artıyorsa düşürün. |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms` | `100` | Catalog wait budget. Daha az 503 için artırın, daha sıkı p99 için düşürün. |
| `reactor.rust.route-admission.get.api.v1.catalog.title/count/info/items/attributes.*` | `16` / `100` | Typed catalog scalar/record/list/map route cap'leri. List/map route'larında object allocation p99'u bozuyorsa düşürün. |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent` | `8` | DB-backed catalog route cap. Provider DB pool ve method bulkhead ile hizalayın. |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms` | `150` | DB-backed catalog wait budget. p99 yüksekse worker artırmadan önce bunu düşürün. |
| `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent` | `8` | Customer DB read route cap. Provider `CustomerQueryService` concurrency ile tune edilir. |
| `reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms` | `150` | Customer DB read queue wait. DB saturation altında hızlı fail-fast için düşürün. |
| `reactor.rust.route-admission.get.api.v1.customers.db.stats/by-segment/id.*` | `4-8` / `150` | Typed DB stats, list ve record lookup cap'leri. List query'leri raw byte pass-through route'larından düşük tutun. |
| `reactor.rust.route-admission.get.api.v1.customers.id.exists/display-name.*` | `8` / `150` | Küçük scalar lookup cap'leri. Bu method'lar DB'ye gidiyorsa provider DB pool ile tune edin. |
| `reactor.rust.route-admission.post.api.v1.customers.max-concurrent` | `8` | Create command cap. Duplicate write baskısı ve DB queue büyümesini önlemek için bounded kalmalı. |
| `reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms` | `150` | Create command wait budget. Write p99 burst absorption'dan önemliyse düşürün. |
| `reactor.rust.route-admission.post.api.v1.customers.typed.*` | `4` / `150` | Typed create command cap. REST record parse ve Hessian record encode/decode olduğu için byte pass-through'dan düşük tutulur. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent` | `8` | Segment patch cap. Command provider kapasitesiyle hizalayın. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms` | `150` | Segment patch queue wait. Sadece idempotent caller ve p99 ölçümü varsa artırın. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent` | `8` | Status patch cap. Write-side stabilite için bounded kalmalı. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms` | `150` | Status patch queue wait. Overload hızlı görünmeli ise düşürün. |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.typed.*` | `4` / `150` | Typed status command cap. Typed command provider method limitiyle hizalı tutun. |
| `reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent` | `8` | Delete command cap. Delete side-effect route olduğu için konservatif kalmalı. |
| `reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms` | `150` | Delete command wait budget. Daha strict fail-fast için düşürün. |

Dubbo consumer:

| Property | Default | Ne işe yarar / ne zaman değişir? |
|----------|---------|----------------------------------|
| `sample.dubbo.discovery` | `static` | Sample switch: `static` `reactor.dubbo.providers` kullanır; `zookeeper` registry discovery kullanır. |
| `reactor.dubbo.enabled` | `true` | Dubbo consumer adapter'ını açar. Bu sample için true kalmalı. |
| `reactor.dubbo.application-name` | `rest-sample-dubbo-consumer` | Dubbo client metadata içindeki uygulama adı. Servise göre değiştirin. |
| `reactor.dubbo.transport` | `native` | Lightweight native data-plane kullanır. Low-overhead path için native kalsın. |
| `reactor.dubbo.runtime-profile` | `micro-dubbo` | Dubbo runtime sizing preset. RPC p99/RSS ölçerek artırın. |
| `reactor.dubbo.registry-address` | `zookeeper://127.0.0.1:2181` | Discovery modunda ZooKeeper adresi. Kubernetes'te override edilmeli. |
| `reactor.dubbo.registry-root` | `dubbo` | ZooKeeper registry root. Provider registration ile aynı olmalı. |
| `reactor.dubbo.registry-timeout-ms` | `3000` | ZooKeeper operation timeout. Registry network yavaşsa artırın. |
| `reactor.dubbo.registry-session-timeout-ms` | `30000` | ZooKeeper session timeout. Ölü provider'ın ne kadar hızlı kaybolacağını etkiler. |
| `reactor.dubbo.registry-check` | `false` | True olursa registry yokken startup fail edebilir. Rolling deploy için false kalsın. |
| `reactor.dubbo.providers` | `127.0.0.1:20880` | Static provider listesi. Docker/K8s/controlled static ortamda service DNS ile override edin. Birden fazla interface Service DNS ile yönetiliyorsa `customer-provider:20880,order-provider:20880` formatını kullanın. |
| `reactor.dubbo.timeout-ms` | `1000` | RPC timeout. HTTP route timeout budget'ından düşük olmalı. |
| `reactor.dubbo.retries` | `0` | Retry sayısı. Write/command route'larında duplicate execution riskini azaltmak için `0` kalsın. |
| `reactor.dubbo.check` | `false` | True olursa provider yokken reference startup fail edebilir. Provider rolling restart için false kalsın. |
| `reactor.dubbo.lazy` | `true` | Bazı connection/reference işlerini erteler. Daha küçük startup için true kalsın. |
| `reactor.dubbo.protocol` | `dubbo` | Protocol adı. Bu sample classic `dubbo://` içindir. |
| `reactor.dubbo.serialization` | `hessian2` | Serialization. Argüman taşıyan command method'ları için gereklidir. |
| `reactor.dubbo.cluster` | `failfast` | Failure stratejisi. Bounded low-latency çağrılar için uygundur; deep retry ile hatayı saklamayın. |
| `reactor.dubbo.loadbalance` | `random` | Birden fazla provider varsa seçim stratejisi. Tek provider'da etkisi sınırlıdır. |
| `reactor.dubbo.connections` | `1` | Logical connection sayısı. Native pool sizing ağırlıklı olarak native connection key'leriyle yapılır. |
| `reactor.dubbo.share-connections` | `1` | Compatibility için shared connection ayarı. Küçük kalsın. |
| `reactor.dubbo.refer-thread-num` | `1` | Reference thread sayısı. RSS için düşük tutulur. |
| `reactor.dubbo.max-inflight` | `32` | Concurrent RPC üst limiti. Throughput için artırın; low-RSS/fail-fast için düşürün. |
| `reactor.dubbo.max-response-bytes` | `8388608` | Dubbo response üst limiti. Büyük provider JSON için HTTP response limitleriyle birlikte artırın. |
| `reactor.dubbo.native-connections-per-endpoint` | `1` | Provider başına native TCP connection sayısı. Read-heavy p99 iyileştirmede ilk artırılacak knob budur. |
| `reactor.dubbo.native-async-workers` | `1` | Native Dubbo async worker sayısı. Yüksek concurrency için artırılır; her worker thread/native maliyet getirir. |
| `reactor.dubbo.native-async-queue-capacity` | `32` | Native Dubbo async queue. Artırmak burst emer ama overload'u saklayabilir ve tail latency artırabilir. |

<details>
<summary>Tüm sample property -> environment variable map'i</summary>

| Property | Environment variable |
|----------|----------------------|
| `server.port` | `SERVER_PORT` |
| `server.host` | `SERVER_HOST` |
| `reactor.runtime.profile` | `REACTOR_RUNTIME_PROFILE` |
| `reactor.startup.component-index.enabled` | `REACTOR_STARTUP_COMPONENT_INDEX_ENABLED` |
| `reactor.startup.component-index.required` | `REACTOR_STARTUP_COMPONENT_INDEX_REQUIRED` |
| `reactor.startup.route-index.validate` | `REACTOR_STARTUP_ROUTE_INDEX_VALIDATE` |
| `reactor.startup.route-index.required` | `REACTOR_STARTUP_ROUTE_INDEX_REQUIRED` |
| `reactor.startup.scan.fallback-enabled` | `REACTOR_STARTUP_SCAN_FALLBACK_ENABLED` |
| `reactor.rust.http.max-request-body-bytes` | `REACTOR_RUST_HTTP_MAX_REQUEST_BODY_BYTES` |
| `reactor.rust.http.max-response-body-bytes` | `REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES` |
| `reactor.rust.http.max-inflight-body-bytes` | `REACTOR_RUST_HTTP_MAX_INFLIGHT_BODY_BYTES` |
| `reactor.rust.http.max-inflight-response-bytes` | `REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES` |
| `reactor.rust.http.max-connections` | `REACTOR_RUST_HTTP_MAX_CONNECTIONS` |
| `reactor.rust.http.max-request-header-bytes` | `REACTOR_RUST_HTTP_MAX_REQUEST_HEADER_BYTES` |
| `reactor.rust.http.max-request-headers` | `REACTOR_RUST_HTTP_MAX_REQUEST_HEADERS` |
| `reactor.rust.http.header-read-timeout-ms` | `REACTOR_RUST_HTTP_HEADER_READ_TIMEOUT_MS` |
| `reactor.rust.http.request-body-timeout-ms` | `REACTOR_RUST_HTTP_REQUEST_BODY_TIMEOUT_MS` |
| `reactor.rust.http.idle-timeout-ms` | `REACTOR_RUST_HTTP_IDLE_TIMEOUT_MS` |
| `reactor.rust.http.http1-only-enabled` | `REACTOR_RUST_HTTP_HTTP1_ONLY_ENABLED` |
| `reactor.rust.http.keep-alive-enabled` | `REACTOR_RUST_HTTP_KEEP_ALIVE_ENABLED` |
| `reactor.rust.log.level` | `REACTOR_RUST_LOG_LEVEL` |
| `reactor.rust.java.log.level` | `REACTOR_RUST_JAVA_LOG_LEVEL` |
| `reactor.rust.jni.workers` | `REACTOR_RUST_JNI_WORKERS` |
| `reactor.rust.jni.queue-capacity` | `REACTOR_RUST_JNI_QUEUE_CAPACITY` |
| `reactor.rust.response-pool.small-capacity` | `REACTOR_RUST_RESPONSE_POOL_SMALL_CAPACITY` |
| `reactor.rust.response-pool.medium-capacity` | `REACTOR_RUST_RESPONSE_POOL_MEDIUM_CAPACITY` |
| `reactor.rust.response-pool.large-capacity` | `REACTOR_RUST_RESPONSE_POOL_LARGE_CAPACITY` |
| `reactor.rust.response-pool.huge-capacity` | `REACTOR_RUST_RESPONSE_POOL_HUGE_CAPACITY` |
| `reactor.rust.websocket.max-frame-bytes` | `REACTOR_RUST_WEBSOCKET_MAX_FRAME_BYTES` |
| `reactor.rust.websocket.outbound-queue-capacity` | `REACTOR_RUST_WEBSOCKET_OUTBOUND_QUEUE_CAPACITY` |
| `reactor.rust.websocket.send-timeout-ms` | `REACTOR_RUST_WEBSOCKET_SEND_TIMEOUT_MS` |
| `reactor.rust.runtime.worker-threads` | `REACTOR_RUST_RUNTIME_WORKER_THREADS` |
| `reactor.rust.runtime.max-blocking-threads` | `REACTOR_RUST_RUNTIME_MAX_BLOCKING_THREADS` |
| `reactor.rust.runtime.thread-stack-bytes` | `REACTOR_RUST_RUNTIME_THREAD_STACK_BYTES` |
| `reactor.rust.native-cache.max-entries` | `REACTOR_RUST_NATIVE_CACHE_MAX_ENTRIES` |
| `reactor.rust.native-cache.max-bytes` | `REACTOR_RUST_NATIVE_CACHE_MAX_BYTES` |
| `reactor.rust.native-cache.ttl-ms` | `REACTOR_RUST_NATIVE_CACHE_TTL_MS` |
| `reactor.rust.async.max-inflight` | `REACTOR_RUST_ASYNC_MAX_INFLIGHT` |
| `reactor.rust.async.response-timeout-ms` | `REACTOR_RUST_ASYNC_RESPONSE_TIMEOUT_MS` |
| `reactor.rust.json.writer-initial-bytes` | `REACTOR_RUST_JSON_WRITER_INITIAL_BYTES` |
| `reactor.rust.json.writer-retain-max-bytes` | `REACTOR_RUST_JSON_WRITER_RETAIN_MAX_BYTES` |
| `reactor.rust.route-admission.enabled` | `REACTOR_RUST_ROUTE_ADMISSION_ENABLED` |
| `reactor.rust.route-admission.default-max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_DEFAULT_MAX_CONCURRENT` |
| `reactor.rust.route-admission.default-queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_DEFAULT_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.catalog.info.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_INFO_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.items.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_ITEMS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.attributes.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_ATTRIBUTES_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_DB_CUSTOMERS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.catalog.db.customers.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_DB_CUSTOMERS_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.get.api.v1.customers.db.by-segment.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_BY_SEGMENT_MAX_CONCURRENT` |
| `reactor.rust.route-admission.get.api.v1.customers.db.id.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_ID_MAX_CONCURRENT` |
| `reactor.rust.route-admission.post.api.v1.customers.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.post.api.v1.customers.typed.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_TYPED_MAX_CONCURRENT` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_SEGMENT_MAX_CONCURRENT` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.segment.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_SEGMENT_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_STATUS_MAX_CONCURRENT` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_STATUS_QUEUE_TIMEOUT_MS` |
| `reactor.rust.route-admission.patch.api.v1.customers.id.status.typed.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_PATCH_API_V1_CUSTOMERS_ID_STATUS_TYPED_MAX_CONCURRENT` |
| `reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent` | `REACTOR_RUST_ROUTE_ADMISSION_DELETE_API_V1_CUSTOMERS_ID_MAX_CONCURRENT` |
| `reactor.rust.route-admission.delete.api.v1.customers.id.queue-timeout-ms` | `REACTOR_RUST_ROUTE_ADMISSION_DELETE_API_V1_CUSTOMERS_ID_QUEUE_TIMEOUT_MS` |
| `sample.dubbo.discovery` | `SAMPLE_DUBBO_DISCOVERY` |
| `reactor.dubbo.enabled` | `REACTOR_DUBBO_ENABLED` |
| `reactor.dubbo.application-name` | `REACTOR_DUBBO_APPLICATION_NAME` |
| `reactor.dubbo.transport` | `REACTOR_DUBBO_TRANSPORT` |
| `reactor.dubbo.runtime-profile` | `REACTOR_DUBBO_RUNTIME_PROFILE` |
| `reactor.dubbo.registry-address` | `REACTOR_DUBBO_REGISTRY_ADDRESS` |
| `reactor.dubbo.registry-root` | `REACTOR_DUBBO_REGISTRY_ROOT` |
| `reactor.dubbo.registry-timeout-ms` | `REACTOR_DUBBO_REGISTRY_TIMEOUT_MS` |
| `reactor.dubbo.registry-session-timeout-ms` | `REACTOR_DUBBO_REGISTRY_SESSION_TIMEOUT_MS` |
| `reactor.dubbo.registry-check` | `REACTOR_DUBBO_REGISTRY_CHECK` |
| `reactor.dubbo.providers` | `REACTOR_DUBBO_PROVIDERS` |
| `reactor.dubbo.timeout-ms` | `REACTOR_DUBBO_TIMEOUT_MS` |
| `reactor.dubbo.retries` | `REACTOR_DUBBO_RETRIES` |
| `reactor.dubbo.check` | `REACTOR_DUBBO_CHECK` |
| `reactor.dubbo.lazy` | `REACTOR_DUBBO_LAZY` |
| `reactor.dubbo.protocol` | `REACTOR_DUBBO_PROTOCOL` |
| `reactor.dubbo.serialization` | `REACTOR_DUBBO_SERIALIZATION` |
| `reactor.dubbo.cluster` | `REACTOR_DUBBO_CLUSTER` |
| `reactor.dubbo.loadbalance` | `REACTOR_DUBBO_LOADBALANCE` |
| `reactor.dubbo.connections` | `REACTOR_DUBBO_CONNECTIONS` |
| `reactor.dubbo.share-connections` | `REACTOR_DUBBO_SHARE_CONNECTIONS` |
| `reactor.dubbo.refer-thread-num` | `REACTOR_DUBBO_REFER_THREAD_NUM` |
| `reactor.dubbo.max-inflight` | `REACTOR_DUBBO_MAX_INFLIGHT` |
| `reactor.dubbo.max-response-bytes` | `REACTOR_DUBBO_MAX_RESPONSE_BYTES` |
| `reactor.dubbo.native-connections-per-endpoint` | `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT` |
| `reactor.dubbo.native-async-workers` | `REACTOR_DUBBO_NATIVE_ASYNC_WORKERS` |
| `reactor.dubbo.native-async-queue-capacity` | `REACTOR_DUBBO_NATIVE_ASYNC_QUEUE_CAPACITY` |

</details>

## Gerçek Hayat Tuning Reçeteleri

Tek seferde tek darboğazı tune edin. Her değişiklik başarılı RPS, p95/p99 latency, 503 oranı,
provider hata oranı ve idle sonrası RSS ile birlikte ölçülmelidir.

**Kubernetes Service DNS ile düşük-memory static consumer**

- Başlangıç: `SAMPLE_DUBBO_DISCOVERY=static`
- Provider listesi: `REACTOR_DUBBO_PROVIDERS=customer-provider:20880,order-provider:20880`
- Runtime: `REACTOR_RUNTIME_PROFILE=micro-dubbo`
- RPC bütçesi: `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2`, `REACTOR_DUBBO_MAX_INFLIGHT=8-16`
- Neden: ZooKeeper client/thread/class yükü consumer'a girmez. Her interface ayrı Service DNS ile açık yönetilir.

**ZooKeeper zorunlu düşük trafikli Kubernetes servisi**

- Başlangıç: `SAMPLE_DUBBO_DISCOVERY=zookeeper`
- Runtime: `REACTOR_RUNTIME_PROFILE=micro-dubbo`, `REACTOR_DUBBO_RUNTIME_PROFILE=micro-dubbo`
- Küçük worker seti: `REACTOR_RUST_JNI_WORKERS=1`
- RPC bütçesi: `REACTOR_DUBBO_MAX_INFLIGHT=8`, `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=1`
- Neden: REST process küçük kalır; overload anında memory tutmak yerine kontrollü 503 kabul edilir.

**Read-heavy katalog veya dashboard JSON**

- Başlangıç: `REACTOR_DUBBO_MAX_INFLIGHT=16-32`
- Connection reuse: `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2-4`
- Route admission: read route için `16-64`
- Neden: provider hızlı ve hazır JSON `byte[]` dönüyorsa başarılı 200 RPS artar.

**Provider üzerinden DB-backed query**

- Route budget: consumer route max concurrent değerini provider kapasitesine yakın tutun, genelde pod başına `4-8`.
- Timeout: `REACTOR_DUBBO_TIMEOUT_MS=800-1500`
- Queue timeout: `50-150ms`
- Neden: consumer'ın provider DB pool saturation'ını büyütmesini engeller.

**POST/PATCH/DELETE command method'ları**

- Retry: `REACTOR_DUBBO_RETRIES=0`
- Command route budget: `4-8`
- Queue timeout: `100-200ms`
- Neden: yanlışlıkla duplicate write oluşmasını engeller ve write basıncını sınırlar.

**Büyük JSON response**

- Dubbo limit: `REACTOR_DUBBO_MAX_RESPONSE_BYTES`
- HTTP limit: `REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES`
- Toplam in-flight limit: `REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES`
- Neden: sadece Dubbo response limitini büyütmek yetmez; HTTP response ve toplam in-flight limit de payload'a izin vermelidir.

**Daha yüksek concurrency ama memory hâlâ sınırlı**

- İlk ayar: `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT`
- Sonra: `REACTOR_DUBBO_MAX_INFLIGHT`
- En son: `REACTOR_RUST_JNI_WORKERS`
- Neden: connection reuse çoğu zaman ekstra Java worker'dan önce p99'u toparlar.

**Provider rolling restart, K8s Service DNS**

- Discovery: `SAMPLE_DUBBO_DISCOVERY=static`
- Provider tarafı: doğru readiness probe
- Consumer tarafı: `REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2`, explicit RPC timeout
- Neden: K8s Service sağlıksız pod'u endpoint listesinden çıkarır. Consumer registry tutmaz; toparlanma readiness/EndpointSlice davranışına bağlıdır.

**Provider rolling restart, ZooKeeper registry**

- Discovery: `SAMPLE_DUBBO_DISCOVERY=zookeeper`
- Startup toleransı: `REACTOR_DUBBO_REGISTRY_CHECK=false`, `REACTOR_DUBBO_CHECK=false`
- Timeout: explicit RPC timeout
- Neden: pod provider geçişlerinde ayağa kalkabilir; discovery toparlanana kadar route'lar bounded failure döner.

### Reçete: ZooKeeper Zorunlu Low-Memory Kubernetes Consumer

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_RUST_JNI_WORKERS
    value: "1"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "1"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1000"
  - name: REACTOR_DUBBO_RETRIES
    value: "0"
```

BEST: ara sıra veya orta trafik alan, memory'nin burst absorption'dan daha önemli olduğu servisler.
Beklenen davranış: overload altında bazı çağrılar kontrollü `503` dönebilir; RSS bounded kalır.

### Reçete: Read-Heavy Catalog Endpoint

```yaml
env:
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "32"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "4"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_MAX_CONCURRENT
    value: "32"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_QUEUE_TIMEOUT_MS
    value: "100"
```

BEST: provider hazır JSON `byte[]` döndürüyor ve consumer `RawResponse` ile forward ediyor.
ANTI-PATTERN: invalidation kuralı olmadan native cache açmak. Sadece bilinçli cache edilebilir
response'ları cache edin.

### Reçete: DB-Backed Provider Query

```yaml
env:
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1200"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_MAX_CONCURRENT
    value: "6"
  - name: REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CUSTOMERS_DB_QUEUE_TIMEOUT_MS
    value: "150"
```

Provider Hikari pool `10` connection ise ve iki consumer pod varsa, her consumer'ın `32` concurrent
DB-backed RPC basmasına izin vermeyin. Pod başına `4-6` ile başlayın ve provider pool wait ölçün.

### Reçete: Duplicate Write İstemeyen Command Endpoint

```yaml
env:
  - name: REACTOR_DUBBO_RETRIES
    value: "0"
  - name: REACTOR_DUBBO_TIMEOUT_MS
    value: "1000"
  - name: REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_MAX_CONCURRENT
    value: "6"
  - name: REACTOR_RUST_ROUTE_ADMISSION_POST_API_V1_CUSTOMERS_QUEUE_TIMEOUT_MS
    value: "150"
```

BEST: güvenli retry yapılamayan create/update/delete işlemleri.
Retry gerekiyorsa önce provider contract'a idempotency key ekleyin.

### Reçete: Daha Büyük Provider JSON

```yaml
env:
  - name: REACTOR_DUBBO_MAX_RESPONSE_BYTES
    value: "16777216"
  - name: REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES
    value: "16777216"
  - name: REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES
    value: "33554432"
  - name: REACTOR_RUST_JSON_WRITER_RETAIN_MAX_BYTES
    value: "32768"
```

Bunu sadece endpoint gerçekten daha büyük JSON dönüyorsa kullanın. Response dosya veya export ise tek
büyük Java body taşımak yerine framework file/static/streaming response yolunu tercih edin.

## Ortama Göre Çalıştırma

Burada iki ayrı karar var:

```text
Maven profile = uygulama classpath'ine hangi dependency'ler girecek.
Runtime property/env = consumer Dubbo provider'ları nasıl bulacak.
```

Bunları aynı şey gibi düşünmeyin. Kubernetes içinde iki geçerli yol vardır. Provider bir K8s Service
DNS ile temsil edilebiliyorsa static mod daha küçük ve daha nettir. Discovery sözleşmeniz ZooKeeper
ise `zookeeper-discovery` Maven profile'ı ile build/run edin ve runtime'da
`SAMPLE_DUBBO_DISCOVERY=zookeeper` verin.

| Ortam | Maven profile | Discovery modu | Provider adresi nereden gelir? |
|-------|---------------|----------------|--------------------------------|
| Lokal standalone JVM, provider tek sabit adreste | default `full-dubbo-consumer` | `static` | `REACTOR_DUBBO_PROVIDERS=127.0.0.1:20880` |
| Lokal standalone JVM, provider ZooKeeper'dan bulunacak | `zookeeper-discovery` | `zookeeper` | `REACTOR_DUBBO_REGISTRY_ADDRESS=zookeeper://127.0.0.1:2181` |
| Tek Docker network içinde | Discovery gerekiyorsa `zookeeper-discovery`, değilse default/full | `zookeeper` veya `static` | Docker service adı, örn. `zookeeper:2181` veya `provider:20880` |
| Kubernetes, provider Service DNS sabit | default/full veya `native-static-consumer` | `static` | `REACTOR_DUBBO_PROVIDERS=rest-sample-dubbo-provider:20880` |
| Kubernetes, Dubbo registry/governance zorunlu | `zookeeper-discovery` | `zookeeper` | ZooKeeper Kubernetes DNS adı, örn. `zookeeper-client.platform.svc.cluster.local:2181` |

### Standalone JVM

Provider ve consumer'ı doğrudan Maven veya IDE üzerinden çalıştırıyorsanız bu yolu kullanın.

Static provider modu en hafif standalone yoldur:

```powershell
$env:SAMPLE_DUBBO_DISCOVERY="static"
$env:REACTOR_DUBBO_PROVIDERS="127.0.0.1:20880"
mvn -q exec:java
```

Kubernetes ile aynı discovery davranışını lokal test etmek istiyorsanız ZooKeeper discovery modunu
kullanın:

```powershell
$env:SAMPLE_DUBBO_DISCOVERY="zookeeper"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://127.0.0.1:2181"
mvn -q -Pzookeeper-discovery exec:java
```

BEST: production ZooKeeper kullanacaksa lokal testte de ZooKeeper profile'ını kullanın.  
ACCEPTABLE: hızlı smoke test için static provider modu kullanın.  
ANTI-PATTERN: sadece static provider modunu test edip, ZooKeeper discovery'yi ilk kez Kubernetes'te
denemek.

### Docker

Sample Spring Boot fat jar gerektirmez. Plain container image için compiled class'ları ve runtime
dependency'leri image içine kopyalayabilirsiniz:

```powershell
mvn -q -Pzookeeper-discovery -DskipTests package dependency:copy-dependencies `
  -DincludeScope=runtime `
  -DoutputDirectory=target/dependency
```

Örnek Dockerfile:

```dockerfile
FROM ibm-semeru-runtimes:open-21-jre
WORKDIR /app
COPY target/classes /app/classes
COPY target/dependency /app/dependency
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-cp", "/app/classes:/app/dependency/*", "com.reactor.sample.dubbo.consumer.app.RestSampleDubboConsumerApplication"]
```

Tek Docker network üzerinde build/run:

```powershell
docker network create reactor-dubbo
docker build -t rest-sample-dubbo-consumer:zk .

docker run --rm --name rest-sample-dubbo-consumer `
  --network reactor-dubbo `
  -p 8080:8080 `
  -e SAMPLE_DUBBO_DISCOVERY=zookeeper `
  -e REACTOR_DUBBO_REGISTRY_ADDRESS=zookeeper://zookeeper:2181 `
  -e REACTOR_RUNTIME_PROFILE=micro-dubbo `
  -e REACTOR_DUBBO_RUNTIME_PROFILE=micro-dubbo `
  rest-sample-dubbo-consumer:zk
```

Docker içinde static provider modu kullanacaksanız `127.0.0.1` yerine provider container veya service
adını verin. Çünkü `127.0.0.1` consumer container'ın kendisini gösterir:

```powershell
docker run --rm --name rest-sample-dubbo-consumer `
  --network reactor-dubbo `
  -p 8080:8080 `
  -e SAMPLE_DUBBO_DISCOVERY=static `
  -e REACTOR_DUBBO_PROVIDERS=rest-sample-dubbo-provider:20880 `
  rest-sample-dubbo-consumer:zk
```

### Kubernetes

Kubernetes içinde iki doğru model vardır. Hangisini seçeceğiniz provider discovery ihtiyacınıza
bağlıdır; Kubernetes'te çalışmak tek başına ZooKeeper zorunluluğu anlamına gelmez.

#### Static Service DNS Mode

Provider bir Kubernetes Service arkasındaysa ve Dubbo registry/governance kullanmıyorsanız en küçük
consumer şekli budur:

```text
Consumer pod -> rest-sample-dubbo-provider Service DNS -> provider pod endpoint'leri
```

Provider tarafında standart Service kullanın:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: rest-sample-dubbo-provider
spec:
  selector:
    app: rest-sample-dubbo-provider
  sessionAffinity: None
  ports:
    - name: dubbo
      port: 20880
      targetPort: 20880
```

Consumer Deployment env örneği:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: REACTOR_DUBBO_MAX_INFLIGHT
    value: "8"
  - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
    value: "2"
```

Farklı Dubbo interface'leri farklı Service DNS ile yönetiliyorsa aynı property içinde listeleyin:

```properties
reactor.dubbo.providers=customer-provider:20880,order-provider:20880
```

Bu modelde K8s Service replica arkasındaki podlara TCP connection dağıtır. Dubbo request'lerinin her
biri için ayrı load balancing yapılmaz. Provider readiness probe doğru değilse Service sağlıksız pod'a
connection gönderebilir; bu yüzden provider readiness'ı RPC portu gerçekten hazır olduğunda `ready`
olmalıdır.

#### ZooKeeper Discovery Mode

Discovery sözleşmeniz ZooKeeper ise doğru akış şudur:

```text
Consumer pod -> ZooKeeper Service -> registry'den provider URL -> Dubbo provider pod/service
```

Image'i `zookeeper-discovery` Maven profile'ı ile build edin. Sadece runtime'da
`SAMPLE_DUBBO_DISCOVERY=zookeeper` vermek yeterli değildir; image ZooKeeper dependency olmadan
build edildiyse uygulama discovery yapamaz.

Minimal ZooKeeper discovery Deployment şekli:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rest-sample-dubbo-consumer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: rest-sample-dubbo-consumer
  template:
    metadata:
      labels:
        app: rest-sample-dubbo-consumer
    spec:
      containers:
        - name: app
          image: registry.example.com/rest-sample-dubbo-consumer:0.1.0-zk
          ports:
            - containerPort: 8080
          env:
            - name: SAMPLE_DUBBO_DISCOVERY
              value: "zookeeper"
            - name: REACTOR_DUBBO_REGISTRY_ADDRESS
              value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
            - name: REACTOR_RUNTIME_PROFILE
              value: "micro-dubbo"
            - name: REACTOR_DUBBO_RUNTIME_PROFILE
              value: "micro-dubbo"
            - name: REACTOR_DUBBO_MAX_INFLIGHT
              value: "8"
            - name: REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT
              value: "2"
          readinessProbe:
            httpGet:
              path: /app/health
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /app/health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 10
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "500m"
              memory: "256Mi"
```

```yaml
apiVersion: v1
kind: Service
metadata:
  name: rest-sample-dubbo-consumer
spec:
  selector:
    app: rest-sample-dubbo-consumer
  ports:
    - name: http
      port: 8080
      targetPort: 8080
```

Kubernetes production notları:

- Static Service DNS modunda başlangıç memory limitini daha düşük tutabilirsiniz; consumer Java ZooKeeper client yüklemez. Yine de kendi image'inizde idle RSS ve p99 testi yapmadan limitleri agresif düşürmeyin.
- ZooKeeper discovery açıksa başlangıç memory limitini `160Mi-256Mi` aralığında tutun; sonra kendi image'inizde idle RSS ve p99 testiyle düşürün.
- `reactor.dubbo.max-inflight` bounded kalmalı. Kör şekilde artırmak RPS'i koruyabilir ama p99'u ve provider stabilitesini bozabilir.
- Low-latency API'lerde operation idempotent ve retry-safe değilse `reactor.dubbo.retries=0` kalsın.
- `/app/health` process health endpoint'idir. Readiness provider erişimine bağlı olsun istiyorsanız ayrı bir readiness route ekleyin; liveness'ı Dubbo provider durumuna bağlamayın.
- Static Service DNS modunda provider rolling restart toparlanması K8s readiness/EndpointSlice davranışına bağlıdır. Readiness düzgünse Service sağlıksız pod'u endpoint listesinden çıkarır.
- Provider rolling restart sırasında yeni provider URL'i ZooKeeper'a yazılmalı ve consumer discovery üzerinden yeniden bağlanmalıdır. Bu olmazsa `/dubbo/{interface}/providers` altındaki provider registration'ı kontrol edin.

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
- Sample ZooKeeper/PostgreSQL servisleri için Docker Desktop veya eşdeğer lokal container runtime
- Private dependency kullanıyorsanız GitHub Packages erişimi
- `127.0.0.1:20880` üzerinde çalışan Dubbo provider

Lokal altyapıyı başlatın. Consumer static provider modunda çalışabilir; ancak provider sample
kendisini ZooKeeper'a register eder ve PostgreSQL warmup yapar:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
docker run -d --name rest-sample-postgres `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 postgres:16-alpine
```

Provider repo'sunu başlatın:

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

Bu komut varsayılan `full-dubbo-consumer` profile'ını kullanır; bu yüzden GET/POST/PATCH/DELETE örneklerinin tamamı açıktır. Sadece en küçük static-provider read path'i istiyorsanız:

```powershell
mvn -q -Pnative-static-consumer exec:java
```

Test:

```powershell
curl http://127.0.0.1:8080/app/health
curl http://127.0.0.1:8080/api/v1/catalog/nested
curl http://127.0.0.1:8080/api/v1/customers/db
curl http://127.0.0.1:8080/api/v1/catalog/db/customers
curl http://127.0.0.1:8080/api/v1/catalog/dubbo-metrics
curl -X POST http://127.0.0.1:8080/api/v1/customers -H "Content-Type: application/json" -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Zeynep Şahin\",\"segment\":\"pilot\",\"email\":\"zeynep.sahin@example.com\"}"
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment -H "Content-Type: application/json" -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status -H "Content-Type: application/json" -d "{\"requestId\":\"req-1003\",\"status\":\"passive\"}"
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/3 -H "Content-Type: application/json" -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

Beklenen sonuç: yukarıdaki endpoint'lerin tamamı HTTP `200` dönmelidir. Customer endpoint'leri
provider'ın PostgreSQL-backed service'inden okur; PostgreSQL çalışmıyorsa bu endpoint'ler asılı
kalmak yerine kontrollü `503` dönmelidir.

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

Consumer'ı ZooKeeper profiliyle başlatın:

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
| `GET /api/v1/catalog/nested` | `NestedCatalogService.getNestedCatalogJson()` çağırır ve provider JSON byte'larını `RawResponse` ile forward eder. |
| `GET /api/v1/catalog/title` | `String getCatalogTitle()` çağırır ve scalar değeri JSON olarak sarar. |
| `GET /api/v1/catalog/count` | `int countCatalogItems()` çağırır ve küçük scalar JSON response döner. |
| `GET /api/v1/catalog/info` | `CatalogInfo getCatalogInfo()` çağırır ve typed record DTO shape döner. |
| `GET /api/v1/catalog/items?limit=2` | `List<CatalogItem> listFeaturedItems(int limit)` çağırır; query input bounded kalır. |
| `GET /api/v1/catalog/attributes` | Küçük metadata için `Map<String,String> getCatalogAttributes()` çağırır. |
| `GET /api/v1/customers/db` | `CustomerQueryService.getDatabaseCustomersJson()` çağırır ve PostgreSQL-backed customer JSON byte'larını forward eder. |
| `GET /api/v1/customers/db/stats` | `CustomerStats getCustomerStats()` çağırır ve aggregate count döner. |
| `GET /api/v1/customers/db/{id}` | `CustomerSummary getCustomer(long id)` çağırır; provider null dönerse 404 döner. |
| `GET /api/v1/customers/db/by-segment?segment=pilot&limit=10` | `List<CustomerSummary> findCustomersBySegment(...)` çağırır; result size bounded olur. |
| `GET /api/v1/customers/{id}/exists` | `boolean customerExists(long id)` çağırır. |
| `GET /api/v1/customers/{id}/display-name` | `String getCustomerDisplayName(long id)` çağırır. |
| `GET /api/v1/catalog/db/customers` | Compatibility alias; yine `CustomerQueryService` çağırır. |
| `POST /api/v1/customers` | Compact command JSON byte'larını `CustomerCommandService.createCustomer(byte[])` method'una gönderir. |
| `POST /api/v1/customers/typed` | REST body'yi `CreateCustomerCommand` olarak parse eder, typed Dubbo command çağırır, `CustomerMutationResult` JSON döner. |
| `PATCH /api/v1/customers/{id}/segment` | Bounded DB command method ile customer segment değiştirir. |
| `PATCH /api/v1/customers/{id}/status` | Bounded DB command method ile lifecycle status değiştirir. |
| `PATCH /api/v1/customers/{id}/status/typed?status=passive&requestId=demo-1` | `patchCustomerStatusTyped(long, String, String)` çağırır. |
| `DELETE /api/v1/customers/{id}` | Bounded DB command method ile customer delete yapar. |
| `GET /api/v1/catalog/dubbo-metrics` | Native Dubbo client metrics çıktısı. |

Hızlı typed Dubbo smoke çağrıları:

```bash
curl http://localhost:8080/api/v1/catalog/title
curl http://localhost:8080/api/v1/catalog/count
curl http://localhost:8080/api/v1/catalog/info
curl "http://localhost:8080/api/v1/catalog/items?limit=2"
curl http://localhost:8080/api/v1/catalog/attributes

curl http://localhost:8080/api/v1/customers/db/stats
curl http://localhost:8080/api/v1/customers/db/1
curl "http://localhost:8080/api/v1/customers/db/by-segment?segment=pilot&limit=10"
curl http://localhost:8080/api/v1/customers/1/exists
curl http://localhost:8080/api/v1/customers/1/display-name

curl -X POST http://localhost:8080/api/v1/customers/typed \
  -H "Content-Type: application/json" \
  -d '{"customerNo":"CUST-9001","fullName":"Typed Demo Customer","segment":"pilot","email":"typed@example.com","requestId":"typed-001"}'

curl -X PATCH "http://localhost:8080/api/v1/customers/1/status/typed?status=passive&requestId=status-001"
```

## Copy/Paste Use Case Cookbook

Bu bölüm bilinçli olarak pratiktir. Derdi performans olan kullanıcı property tablolarından
başlayabilir. Derdi doğru kod pattern'i bulmak olan kullanıcı Java snippet'lerini alıp aynı sınırları
koruyabilir: REST handler Java'da, Dubbo adapter Java'da, HTTP I/O Rust'ta, DB mutation provider'da.

| Gerçek ihtiyaç | Kullanılacak pattern | Neden |
|----------------|----------------------|-------|
| Ürün kataloğu, dashboard config, şube listesi | `GET` + provider JSON `byte[]` döner + `RawResponse.json(bytes)` | Read-heavy pass-through JSON için en düşük allocation yolu. |
| Küçük typed lookup | `GET` + provider `record`, `String`, `boolean` veya `int` döner | Consumer field okuyacak veya değere göre branch edecektir. |
| Küçük sayfalı arama | `GET` + provider strict `limit` ile `List<record>` döner | Küçük sayfa için net DTO contract; unbounded export için kullanılmamalı. |
| DB provider'dan customer listesi | `GET` + Hikari/SQL provider'da + consumer bytes forward eder | REST process küçük kalır; DB pool HTTP process içine taşınmaz. |
| Customer/order/payment create command | `POST` + compact JSON command bytes | Büyük consumer DTO graph kurmadan write command provider'a gider. |
| Typed customer command | `POST` + REST record body + Dubbo record command | Daha temiz business contract; byte pass-through'a göre allocation yükü daha fazladır. |
| Segment/status/adres gibi partial update | `PATCH` + path id + command body | Küçük payload, net command niyeti, bounded route admission. |
| Delete/deactivate request | `DELETE` + path id + opsiyonel reason body | Destructive operation açık olur; audit için reason/requestId taşınabilir. |
| Provider discovery zorunlu | ZooKeeper profile | Çalışır, fakat consumer process'e Java ZooKeeper client overhead ekler. |
| En düşük memory servis | Static provider list + `micro-dubbo` | Hot REST JVM içinde ZooKeeper client ve resmi Dubbo runtime yoktur. |

### Use Case 1: Read-Heavy Catalog Pass-Through

Provider JSON'u zaten üretiyorsa consumer içinde parse etmeyin.

```java
@GetMapping(value = "/nested", responseType = RawResponse.class)
@RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
public CompletableFuture<ResponseEntity<RawResponse>> nestedCatalog() {
    return catalogClient.nestedCatalogJsonAsync()
            .thenApply(json -> ResponseEntity.ok(RawResponse.json(json)));
}
```

Korunacak properties:

```properties
reactor.runtime.profile=micro-dubbo
reactor.dubbo.transport=native
reactor.dubbo.providers=127.0.0.1:20880
reactor.rust.native-cache.max-bytes=0
reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16
```

Native cache'i sadece catalog bilinçli cache edilebilir olduğunda ve TTL/invalidation kuralınız netse
açın. Bu sample default olarak kapalı tutar.

### Use Case 2: DB-Backed Customer Query

PostgreSQL/Hikari provider'ın sorumluluğudur. Consumer sadece REST endpoint açmak için kendi JDBC
pool'unu başlatmamalı.

```powershell
curl http://127.0.0.1:8080/api/v1/customers/db
```

Bu değerleri birlikte tune edin:

```properties
# consumer
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=150

# provider
sample.db.maximum-pool-size=2
dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1
```

BEST: p99 büyürse önce DB pool wait ve provider method limitlerini kontrol edin. ANTI-PATTERN:
provider DB pool saturation altındayken consumer JNI worker artırmak.

### Use Case 3: POST Customer Create

PowerShell:

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Zeynep Şahin\",\"segment\":\"pilot\",\"email\":\"zeynep.sahin@example.com\"}"
```

Consumer handler:

```java
@PostMapping(value = "", requestType = byte[].class, responseType = RawResponse.class)
@MaxRequestBodySize(32768)
@RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody byte[] body) {
    return customerCommandClient.createCustomerAsync(body)
            .thenApply(json -> ResponseEntity.created(RawResponse.json(json)));
}
```

Pass-through command JSON için `@RequestBody byte[]` kullanın. Consumer çağrıdan önce typed business
karar verecekse record DTO kullanmak kabul edilebilir.

### Use Case 4: PATCH Customer Segment

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
```

Business classification, tenant move, fiyat segmenti veya pilot -> enterprise geçişleri için bu
pattern'i kullanın.

### Use Case 5: PATCH Customer Status

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1003\",\"status\":\"passive\"}"
```

`active`, `passive`, `blocked`, `pending-review` gibi lifecycle değişikliklerinde küçük payload ve
explicit route tercih edin. Her şeyi alan generic update endpoint'i default yapmayın.

### Use Case 6: DELETE Customer

```powershell
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/3 `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

Production'da audit veya recovery önemliyse hard delete yerine soft-delete/deactivate tercih edin.
Hard delete örneği kullanıcıların net bir `DELETE` verb örneği görmesi için vardır.

## Endpoint Pattern Kataloğu

Bu bölüm sample'daki gerçek endpoint'lere ek olarak, kendi projenize kopyalayabileceğiniz handler
pattern'lerini gösterir. Örneklerin amacı tüm HTTP verb'lerinde hangi request body ve response tipi
ne zaman kullanılmalı sorusunu netleştirmektir.

**GET, hazır JSON pass-through**

- Request tipi: body yok; `@PathVariable` ve `@RequestParam` kullanılabilir.
- Response tipi: `RawResponse.json(bytes)`
- Ne zaman: provider veya cache hazır JSON üretiyorsa.
- Java heap/GC: çok düşük; DTO object graph kurulmaz.
- JSON maliyeti: parse ve tekrar serialize yoktur.
- Rust/JNI/body etkisi: en ucuz read path. Native static/cache kullanılırsa per-request body taşıma da azalır.

**GET, küçük typed response**

- Request tipi: body yok; path/query parametreleri.
- Response tipi: Java `record`
- Ne zaman: küçük status, lookup veya consumer'ın hesapladığı response.
- Java heap/GC: düşük; sadece response record allocation.
- JSON maliyeti: sadece response serialize edilir.
- Rust/JNI/body etkisi: küçük JSON için uygun; high traffic'te DTO serialization p99'a yansıyabilir.

**GET, küçük sayfalı liste**

- Request tipi: body yok; filtre query parametreleri.
- Response tipi: `List<record>`
- Ne zaman: listeleme, arama sonucu veya küçük sayfalı response.
- Java heap/GC: orta/yüksek; liste + her item için record allocation.
- JSON maliyeti: tüm liste serialize edilir.
- Rust/JNI/body etkisi: büyük listelerde RSS ve p99 artar; pagination, route budget ve response byte limiti şart.

**POST, JSON command pass-through**

- Request tipi: `byte[]`
- Response tipi: `RawResponse.json(bytes)`
- Ne zaman: JSON command provider'a aynen gidecek ve provider JSON dönecekse.
- Java heap/GC: düşük; request DTO graph kurulmaz.
- JSON maliyeti: consumer tarafında parse yok; provider response tekrar serialize edilmez.
- Rust/JNI/body etkisi: request body byte[] taşınır. Command pass-through için en düşük allocation yolu.

**POST, binary payload**

- Request tipi: `byte[]`
- Response tipi: `RawResponse.bytes(...)`
- Ne zaman: binary upload, imza doğrulama payload'ı, küçük binary echo/download.
- Java heap/GC: düşük; body DTO yok.
- JSON maliyeti: yok.
- Rust/JNI/body etkisi: raw binary taşınır; büyük binary için `FileResponse` veya streaming tercih edilmeli.

**POST, parse etmeden receipt dönme**

- Request tipi: `byte[]`
- Response tipi: Java `record`
- Ne zaman: request'i parse etmeden sadece kabul bilgisi veya id dönecekseniz.
- Java heap/GC: düşük/orta; request DTO yok, response record var.
- JSON maliyeti: sadece response serialize edilir.
- Rust/JNI/body etkisi: command accept/receipt endpoint'i için iyi ara yol.

**POST, typed command**

- Request tipi: Java `record`
- Response tipi: Java `record`
- Ne zaman: consumer request üzerinde typed validation veya business karar verecekse.
- Java heap/GC: orta; request record + response record allocation.
- JSON maliyeti: request parse + response serialize vardır.
- Rust/JNI/body etkisi: en okunabilir normal REST DTO yolu; hot path'te `byte[] + RawResponse` kadar ucuz değildir.

**POST, body ile arama/list response**

- Request tipi: Java `record`
- Response tipi: `List<record>`
- Ne zaman: body ile filtre alan search/query endpoint'i.
- Java heap/GC: yüksek; request record + liste + item records.
- JSON maliyeti: request parse + liste serialize edilir.
- Rust/JNI/body etkisi: high traffic ve büyük result set için pahalıdır; pagination ve max result limit zorunlu olmalı.

**PUT, tam update/replace**

- Request tipi: Java `record`
- Response tipi: Java `record` veya hata body.
- Ne zaman: tam replace/update.
- Java heap/GC: orta; request ve response DTO oluşur.
- JSON maliyeti: request parse + response serialize vardır.
- Rust/JNI/body etkisi: idempotent update için net contract; body limit ve route admission ekleyin.

**PATCH, typed partial update**

- Request tipi: Java `record`
- Response tipi: Java `record`
- Ne zaman: status, segment, adres gibi typed partial update.
- Java heap/GC: düşük/orta; küçük command record + response record.
- JSON maliyeti: request parse + response serialize vardır.
- Rust/JNI/body etkisi: küçük payload için kabul edilebilir; command concurrency düşük tutulmalı.

**PATCH, command pass-through**

- Request tipi: `byte[]`
- Response tipi: `RawResponse.json(bytes)`
- Ne zaman: provider command JSON'u aynen işleyecekse.
- Java heap/GC: düşük; consumer DTO graph yok.
- JSON maliyeti: consumer parse/serialize yok.
- Rust/JNI/body etkisi: partial command pass-through için daha düşük RSS; validation provider'a kalır.

**DELETE, body dönmeyen command**

- Request tipi: body yok.
- Response tipi: `204 No Content`
- Ne zaman: silme/deactivate sonucu body gerekmiyorsa.
- Java heap/GC: en düşük; response object graph yok.
- JSON maliyeti: yok.
- Rust/JNI/body etkisi: en ucuz delete path; audit gerekiyorsa header/log/outbox kullanın.

**DELETE, audit body taşıyan command**

- Request tipi: opsiyonel `byte[]`
- Response tipi: `RawResponse.json(bytes)` veya hata body.
- Ne zaman: reason/requestId gibi audit body taşınacaksa.
- Java heap/GC: düşük; body varsa sadece byte[] taşınır.
- JSON maliyeti: consumer parse etmezse JSON maliyeti yok.
- Rust/JNI/body etkisi: audit JSON küçük tutulmalı; destructive command concurrency düşük olmalı.

Notlar:

- Gerçek HTTP raw byte response için `ResponseEntity<byte[]>` yerine `RawResponse.bytes(...)` kullanın.
- Hazır JSON byte response için `RawResponse.json(bytes)` kullanın.
- Normal HTTP JSON DTO için Java `record` kullanın; POJO class'ı DTO default'u yapmayın.
- `List<CustomerDto>.class` Java'da olmadığı için list response örneklerinde `responseType = List.class` kullanılır, method dönüş tipi yine `ResponseEntity<List<CustomerView>>` olmalıdır.
- Success ve error body farklı record tipleriyse örneklerde `ResponseEntity<?>` ve `responseType = Object.class` gösterilmiştir. Production'da daha katı contract istiyorsanız ortak bir `ApiResult` envelope record'u kullanabilirsiniz.

### Copy/Paste: Tüm Verb ve Tip Kombinasyonları

Aşağıdaki class'ı kendi handler package'ınıza koyup base path'i değiştirebilirsiniz. In-memory map
sadece örneğin kendi kendine çalışması içindir; gerçek projede buradaki map yerine service veya
Dubbo client çağrısı koyun.

```java
package com.example.orders;

import com.reactor.rust.annotations.DeleteMapping;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.MaxRequestBodySize;
import com.reactor.rust.annotations.PatchMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.PostMapping;
import com.reactor.rust.annotations.PutMapping;
import com.reactor.rust.annotations.RequestBody;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequestMapping("/api/v1/orders-demo")
public final class OrderEndpointExamples {

    private final AtomicLong ids = new AtomicLong(1000);
    private final Map<Long, OrderView> orders = new ConcurrentHashMap<>();

    public record OrderLine(String sku, int quantity) {}

    public record OrderView(long id, String customerNo, String status, List<OrderLine> lines) {}

    public record CreateOrderRequest(String requestId, String customerNo, List<OrderLine> lines) {}

    public record ReplaceOrderRequest(String customerNo, String status, List<OrderLine> lines) {}

    public record PatchStatusRequest(String requestId, String status, String reason) {}

    public record SearchOrdersRequest(String customerNo, String status) {}

    public record CreateReceipt(long id, String status) {}

    public record StatusResponse(String status, long orderCount) {}

    public record ErrorBody(String code, String message) {}

    @GetMapping(value = "/status", responseType = StatusResponse.class)
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(new StatusResponse("UP", orders.size()));
    }

    @GetMapping(value = "/{id}", responseType = Object.class)
    public ResponseEntity<?> getById(@PathVariable("id") long id) {
        OrderView order = orders.get(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("order_not_found", "Order " + id + " was not found"));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping(value = "", responseType = List.class)
    @RouteAdmission(maxConcurrent = 32, queueTimeoutMs = 100)
    public ResponseEntity<List<OrderView>> list(
            @RequestParam(value = "status", required = false, defaultValue = "") String status) {
        List<OrderView> result = orders.values().stream()
                .filter(order -> status.isBlank() || order.status().equalsIgnoreCase(status))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/raw-dashboard", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> rawDashboard() {
        byte[] json = "{\"screen\":\"orders\",\"enabled\":true}".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok(RawResponse.json(json));
    }

    @PostMapping(value = "/raw-json", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(32768)
    @RouteAdmission(maxConcurrent = 16, queueTimeoutMs = 100)
    public ResponseEntity<RawResponse> postByteArrayReturnJsonBytes(@RequestBody byte[] body) {
        if (body.length == 0) {
            return ResponseEntity.badRequest(errorJson("empty_body", "JSON body is required"));
        }
        return ResponseEntity.created(RawResponse.json(body));
    }

    @PostMapping(value = "/binary-echo", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(65536)
    public ResponseEntity<RawResponse> postByteArrayReturnBinary(@RequestBody byte[] body) {
        return ResponseEntity.ok(RawResponse.bytes(body, "application/octet-stream"));
    }

    @PostMapping(value = "/raw-to-record", requestType = byte[].class, responseType = Object.class)
    @MaxRequestBodySize(32768)
    public ResponseEntity<?> postByteArrayReturnRecord(@RequestBody byte[] body) {
        if (body.length == 0) {
            return ResponseEntity.badRequest(new ErrorBody("empty_body", "Body is required"));
        }
        long id = ids.incrementAndGet();
        return ResponseEntity.created(new CreateReceipt(id, "accepted"));
    }

    @PostMapping(value = "", requestType = CreateOrderRequest.class, responseType = Object.class)
    @MaxRequestBodySize(32768)
    @RouteAdmission(maxConcurrent = 8, queueTimeoutMs = 150)
    public ResponseEntity<?> postRecordReturnRecord(@RequestBody CreateOrderRequest request) {
        if (request.customerNo() == null || request.customerNo().isBlank()) {
            return ResponseEntity.badRequest(new ErrorBody("customer_required", "customerNo is required"));
        }
        long id = ids.incrementAndGet();
        OrderView created = new OrderView(id, request.customerNo(), "created", request.lines());
        orders.put(id, created);
        return ResponseEntity.created(created);
    }

    @PostMapping(value = "/search", requestType = SearchOrdersRequest.class, responseType = List.class)
    public ResponseEntity<List<OrderView>> postRecordReturnList(@RequestBody SearchOrdersRequest request) {
        List<OrderView> result = orders.values().stream()
                .filter(order -> request.customerNo() == null || request.customerNo().equals(order.customerNo()))
                .filter(order -> request.status() == null || request.status().equalsIgnoreCase(order.status()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping(value = "/{id}", requestType = ReplaceOrderRequest.class, responseType = Object.class)
    @MaxRequestBodySize(32768)
    public ResponseEntity<?> putRecordReturnRecord(
            @PathVariable("id") long id,
            @RequestBody ReplaceOrderRequest request) {
        if (!orders.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("order_not_found", "Order " + id + " was not found"));
        }
        OrderView replaced = new OrderView(id, request.customerNo(), request.status(), request.lines());
        orders.put(id, replaced);
        return ResponseEntity.ok(replaced);
    }

    @PatchMapping(value = "/{id}/status", requestType = PatchStatusRequest.class, responseType = Object.class)
    @MaxRequestBodySize(8192)
    public ResponseEntity<?> patchRecordReturnRecord(
            @PathVariable("id") long id,
            @RequestBody PatchStatusRequest request) {
        OrderView current = orders.get(id);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("order_not_found", "Order " + id + " was not found"));
        }
        OrderView updated = new OrderView(id, current.customerNo(), request.status(), current.lines());
        orders.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping(value = "/{id}", responseType = Void.class)
    public ResponseEntity<Void> deleteNoBody(@PathVariable("id") long id) {
        orders.remove(id);
        return ResponseEntity.noContent();
    }

    @DeleteMapping(value = "/{id}/audit", requestType = byte[].class, responseType = RawResponse.class)
    @MaxRequestBodySize(8192)
    public ResponseEntity<RawResponse> deleteWithOptionalByteBody(
            @PathVariable("id") long id,
            @RequestBody(required = false) byte[] auditBody) {
        orders.remove(id);
        byte[] audit = auditBody == null ? "{}".getBytes(StandardCharsets.UTF_8) : auditBody;
        return ResponseEntity.ok(RawResponse.json(audit));
    }

    private static RawResponse errorJson(String code, String message) {
        String json = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return RawResponse.json(json.getBytes(StandardCharsets.UTF_8));
    }
}
```

PowerShell ile hızlı deneme:

```powershell
curl http://127.0.0.1:8080/api/v1/orders-demo/status
curl "http://127.0.0.1:8080/api/v1/orders-demo?status=created"
curl http://127.0.0.1:8080/api/v1/orders-demo/raw-dashboard

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/raw-json `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r1\",\"raw\":true}"

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/binary-echo `
  -H "Content-Type: application/octet-stream" `
  --data-binary "binary-payload"

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r2\",\"customerNo\":\"CUST-1\",\"lines\":[{\"sku\":\"SKU-1\",\"quantity\":2}]}"

curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/search `
  -H "Content-Type: application/json" `
  -d "{\"customerNo\":\"CUST-1\",\"status\":\"created\"}"

curl -X PUT http://127.0.0.1:8080/api/v1/orders-demo/1001 `
  -H "Content-Type: application/json" `
  -d "{\"customerNo\":\"CUST-1\",\"status\":\"paid\",\"lines\":[{\"sku\":\"SKU-1\",\"quantity\":2}]}"

curl -X PATCH http://127.0.0.1:8080/api/v1/orders-demo/1001/status `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r3\",\"status\":\"cancelled\",\"reason\":\"customer request\"}"

curl -X DELETE http://127.0.0.1:8080/api/v1/orders-demo/1001

curl -X DELETE http://127.0.0.1:8080/api/v1/orders-demo/1001/audit `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"r4\",\"reason\":\"audit cleanup\"}"
```

Hata senaryosu örnekleri:

```powershell
# 404 ErrorBody döner
curl http://127.0.0.1:8080/api/v1/orders-demo/999999

# 400 ErrorBody döner
curl -X POST http://127.0.0.1:8080/api/v1/orders-demo `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"bad\",\"lines\":[]}"

# 400 RawResponse JSON error döner
curl -X POST http://127.0.0.1:8080/api/v1/orders-demo/raw-json `
  -H "Content-Type: application/json" `
  -d ""
```

Production kararı: yüksek trafikli pass-through endpoint'lerde `byte[] + RawResponse` en düşük
allocation yoludur. Consumer request üzerinde business karar verecekse `record request -> record
response` kullanın. Büyük list veya büyük JSON response için route budget ve in-flight byte limitini
ayrı ayarlamadan concurrency artırmayın.

### Profile Ve Property Seçimi

**Ara sıra çağrı alan en küçük pod lazım**

- Başlangıç profile: `micro-dubbo`, static provider.
- Kritik properties: `reactor.rust.jni.workers=1`, `reactor.dubbo.native-async-workers=1`, `reactor.rust.response-pool.small-capacity=8`.

**c256 altında daha fazla başarılı write lazım**

- Başlangıç profile: `micro-dubbo` + route-specific tuning.
- Kritik property: `reactor.rust.route-admission.post.api.v1.customers.max-concurrent`.
- Karar kuralı: değeri artırdıktan sonra RSS, p99 ve 503 oranını birlikte ölçün.

**Dynamic provider discovery lazım**

- Başlangıç profile: `micro-dubbo` + `zookeeper-discovery` Maven profile.
- Kritik property: `SAMPLE_DUBBO_DISCOVERY=zookeeper`.
- Beklenti: küçük RSS artışı normaldir.

**Provider DB yavaş**

- Başlangıç profile: consumer bounded kalsın, önce provider tune edilsin.
- Kritik alanlar: `sample.db.maximum-pool-size`, provider method limitleri, PostgreSQL latency.

**Best-practice kod şablonu lazım**

- Başlangıç profile: bu sample yapısını kopyalayın.
- Akış: `handler` -> `dubbo client` -> `shared interface` -> `provider service` -> `repository`.

Her problemi thread pool büyütme ile çözmeye çalışmayın. Bu framework için güvenli production sırası
genelde şudur: bounded route admission, explicit timeout, provider bulkhead, DB pool tuning, sonra
ölçümle worker artışı.

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
