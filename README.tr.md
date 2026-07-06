# rest-sample-dubbo-consumer

[English](https://github.com/esasmer-dou/rest-sample-dubbo-consumer/blob/master/README.md) | [Turkish](https://github.com/esasmer-dou/rest-sample-dubbo-consumer/blob/master/README.tr.md)

[Kısa Kullanıcı Rehberi](docs/USER_GUIDE.tr.md) | [PDF](docs/USER_GUIDE.tr.pdf)

`java-rust-dubbo` ile Dubbo provider çağıran minimal Rust-Java REST örneğidir.

Provider verisini REST endpoint olarak dışarı açar. Java handler mantığını taşır. Rust HTTP I/O ve düşük seviyeli Dubbo data path işini yapar.

Bu örnek hot REST process içine varsayılan olarak Spring Boot, resmi Dubbo consumer stack veya Netty taşımaz.

Ortak sample contract'ları `com.reactor.sample:rest-sample-utility:0.1.0` paketinden gelir. Ortak DTO
record'ları transitif olarak `com.reactor.sample:rust-sample-model:0.1.0` paketinden gelir. Dubbo
interface package adı `com.reactor.rust.dubbo.sample` olarak korunur. Böylece provider ve consumer
aynı service kimliğini kullanmaya devam eder.

## İçindekiler

1. [Bu Örnek Ne İçin Hazırlandı?](#bu-örnek-ne-için-hazırlandı)
2. [Kopyala-Yapıştır: Provider Üzerinden REST API Çalıştır](#kopyala-yapıştır-provider-üzerinden-rest-api-çalıştır)
3. [Buradan Başlayın: Consumer Şeklinizi Seçin](#buradan-başlayın-consumer-şeklinizi-seçin)
4. [ZooKeeper ve Static Discovery](#zookeeper-ne-sağlar-ne-zaman-gerekir)
5. [Production Reçeteleri](#production-reçeteleri)
6. [Production Senaryo Rehberi](#production-senaryo-rehberi)
7. [Konfigürasyon](#konfigürasyon)
8. [Tam Runtime Property Rehberi](#tam-runtime-property-rehberi)
9. [Ortama Göre Çalıştırma](#ortama-göre-çalıştırma)
10. [Endpoint'ler](#endpointler)
11. [Sözlük](#sözlük)
12. [Sorun Giderme](#sorun-giderme)

## Bu README Nasıl Okunmalı?

Sadece çalıştırmak istiyorsan kopyala-yapıştır bölümüyle başla.

Kubernetes'e çıkacaksan production reçetelerini oku.

RSS, p99, 503 oranı veya Dubbo failover ayarlıyorsan property rehberine bak.

Anlamadığın terim varsa sözlüğe bak.

## Property Katmanları

Varsayılan `src/main/resources/rust-spring.properties` minimum local dosyadır. Sadece server port,
profile, startup index politikası, discovery modu, provider adresi ve command key admission ayarını içerir.

Production ayarlarını overlay olarak kullanın:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties" ...
```

Advanced tuning dosyasını p99, 503 oranı, provider kapasitesi ve RSS ölçmeden kullanmayın:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties;src/main/resources/config/advanced-tuning.properties" ...
```

- `config/production.properties`: Kubernetes provider adresi, low-RSS Dubbo limitleri ve static/ZooKeeper geçiş örneklerini içerir.
- `config/advanced-tuning.properties`: route admission, Rust pool ayarları ve balanced-dubbo reçetesi içindir.
- Environment alternatifi: `REACTOR_CONFIG_FILE=/app/config/production.properties`.

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

## Kopyala-Yapıştır: Provider Üzerinden REST API Çalıştır

Bu bölüm en hızlı çalışan yerel akışı gösterir. Provider ve consumer aynı makinede çalışır.
ZooKeeper kullanılmaz. Consumer provider'a `127.0.0.1:20880` adresinden gider.

### 1. Provider'ı Açın

POST, PATCH ve DELETE örneklerini de denemek istiyorsanız provider'ı PostgreSQL destekli full modda
açın. Komutlar `rest-sample-dubbo-provider` README dosyasında hazırdır.

Kısa özet:

```powershell
cd ..\rest-sample-dubbo-provider
docker rm -f rs-provider-postgres-test 2>$null

docker run -d --name rs-provider-postgres-test `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 postgres:15.7-alpine

mvn -q clean package

java "-Dreactor.dubbo.registry-enabled=false" `
  "-Dsample.db.jdbc-url=jdbc:postgresql://127.0.0.1:15432/reactor_sample" `
  "-Dsample.db.username=reactor" `
  "-Dsample.db.password=reactor" `
  "-Dsample.db.schema-init=true" `
  "-Dsample.db.warmup=true" `
  -jar target/rest-sample-dubbo-provider-0.1.1.jar
```

Bu terminal açık kalmalıdır.

### 2. Consumer'ı Açın

Ayrı bir terminal açın. Komutları `rest-sample-dubbo-consumer` dizininde çalıştırın:

```powershell
$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"
mvn -q clean package

java "-Dserver.port=8080" `
  "-Dsample.dubbo.discovery=static" `
  "-Dreactor.dubbo.providers=127.0.0.1:20880" `
  "-Dreactor.runtime.profile=micro-dubbo" `
  "-Dreactor.dubbo.runtime-profile=micro-dubbo" `
  "-Dreactor.dubbo.native-connections-per-endpoint=2" `
  "-Dreactor.dubbo.native-async-workers=1" `
  "-Dreactor.dubbo.max-inflight=8" `
  -jar target/rest-sample-dubbo-consumer-0.1.1.jar
```

### 3. Endpoint'leri Deneyin

Üçüncü bir terminal açın:

```powershell
curl.exe http://127.0.0.1:8080/app/health
curl.exe http://127.0.0.1:8080/api/v1/catalog/nested
curl.exe http://127.0.0.1:8080/api/v1/catalog/info
curl.exe http://127.0.0.1:8080/api/v1/catalog/items
curl.exe http://127.0.0.1:8080/api/v1/customers/db
curl.exe http://127.0.0.1:8080/api/v1/customers/db/1
curl.exe "http://127.0.0.1:8080/api/v1/customers/db/by-segment?segment=pilot&limit=5"
curl.exe http://127.0.0.1:8080/api/v1/customers/db/stats
```

Yeni müşteri oluşturun. Bu endpoint JSON'u `byte[]` olarak provider'a taşır ve native response handle
ile döner. Bu yol en düşük overhead'li command örneğidir.

```powershell
curl.exe -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  --data "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Ayşe Yılmaz\",\"segment\":\"pilot\",\"email\":\"ayse.yilmaz@example.com\"}"
```

Typed DTO örneğini deneyin. Bu yol daha okunaklı contract verir. Bedeli Hessian ve Java object
allocation maliyetidir.

```powershell
curl.exe -X POST http://127.0.0.1:8080/api/v1/customers/typed `
  -H "Content-Type: application/json" `
  --data "{\"requestId\":\"req-1002\",\"customerNo\":\"CUST-9002\",\"fullName\":\"Mehmet Çelik\",\"segment\":\"enterprise\",\"email\":\"mehmet.celik@example.com\"}"
```

Segment ve status güncelleyin:

```powershell
curl.exe -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment `
  -H "Content-Type: application/json" `
  --data "{\"requestId\":\"req-1003\",\"segment\":\"enterprise\"}"

curl.exe -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  --data "{\"requestId\":\"req-1004\",\"status\":\"active\"}"
```

Typed status güncelleme query parametreleriyle çalışır:

```powershell
curl.exe -X PATCH "http://127.0.0.1:8080/api/v1/customers/1/status/typed?status=passive&requestId=req-1005"
```

Müşteri silme örneği:

```powershell
curl.exe -X DELETE http://127.0.0.1:8080/api/v1/customers/1 `
  -H "Content-Type: application/json" `
  --data "{\"requestId\":\"req-1006\",\"reason\":\"sample cleanup\"}"
```

Operasyonel durum için şu endpoint'leri kullanın:

```powershell
curl.exe http://127.0.0.1:8080/app/ready
curl.exe http://127.0.0.1:8080/app/native-metrics
curl.exe http://127.0.0.1:8080/app/native-diagnostics
curl.exe http://127.0.0.1:8080/app/command-key-admission
```

## Buradan Başlayın: Consumer Şeklinizi Seçin

Kullanıcı için en doğru başlangıç tüm property listesini ezberlemek değildir. Önce servisinizin
şeklini seçin, en yakın profile'ı kopyalayın, sonra sadece trafiğinizi etkileyen birkaç değeri tune
edin.

| Senaryo | Profile | Yol / Kazanç | Bedel |
|----------|---------|--------------|-------|
| Lokal tüm verbler | Maven: default `full-dubbo-consumer`<br>Runtime: `micro-dubbo` | `RawResponse.json(bytes)`<br>GET/POST/PATCH/DELETE hemen çalışır | Read-only moda göre classpath büyür |
| En düşük memory read-only | Maven: `native-static-consumer`<br>Runtime: `micro-dubbo` | No-arg Dubbo `byte[]` JSON<br>ZooKeeper/Hessian class yok | Sadece argümansız read |
| K8s Service DNS static | Default veya `native-static-consumer`<br>Runtime: `micro-dubbo` | Provider adresi K8s Service DNS<br>ZooKeeper client/thread yok | Registry/governance yok |
| K8s ZooKeeper zorunlu | Maven: `zookeeper-discovery`<br>Runtime: `micro-dubbo` | Provider URL ZooKeeper'dan gelir<br>Restart/re-register takip edilir | ZooKeeper thread/class ekler |
| Read-heavy catalog/lookup | Default veya `zookeeper-discovery`<br>`micro-dubbo` | Provider hazır JSON döner<br>DTO graph + reserialize yok | JSON shape provider sorumluluğu |
| Typed Dubbo DTO | Default veya `zookeeper-discovery`<br>`micro-dubbo` | `record/list/map/scalar` contract gösterir | Hessian + Java object allocation |
| Write/command API | Default veya `zookeeper-discovery`<br>`micro-dubbo` | `byte[]` request provider command'a gider<br>REST handler ince kalır | Hessian request encoding gerekir |
| Daha yüksek concurrency | Default veya `zookeeper-discovery`<br>Ölçerek balanced'a yaklaş | Aynı API, daha büyük route budget<br>Daha az overload reject | RSS ve provider/DB baskısı artar |

Önerilen başlangıç: Kubernetes içinde provider zaten bir `Service` DNS arkasındaysa ve Dubbo
registry/governance ihtiyacınız yoksa static provider modu daha küçük ve daha sade başlangıçtır.
Provider discovery production'da ZooKeeper üzerinden zorunluysa consumer'ı `zookeeper-discovery` ile
build/run edin. ZooKeeper dependency olmadan build edilmiş bir image'a runtime'da sadece
`SAMPLE_DUBBO_DISCOVERY=zookeeper` vermek yeterli olmaz.

## Senaryoya Göre Jlink Image Seçimi

Her şeyi destekleyen tek image çıkarmayın. Production'da gerçekten hangi senaryo çalışacaksa o
senaryonun en küçük image'ını build edin.

| Image | Build komutu | Ne zaman kullanılır? | Bilinçli desteklemez | Local smoke kanıtı |
|-------|--------------|----------------------|----------------------|--------------------|
| <small>`rest-sample-dubbo-consumer:native-static-jlink`</small> | <small>`docker build -f`<br>`rest-sample-dubbo-consumer/docker/images/`<br>`Dockerfile.jlink.native-static.workspace`<br>`-t rest-sample-dubbo-consumer:native-static-jlink .`</small> | <small>Static provider adresi, ZooKeeper yok, `GET /api/v1/catalog/nested` gibi argümansız raw JSON read.</small> | <small>Typed DTO route'ları, argümanlı Dubbo method'ları, ZooKeeper discovery.</small> | <small>JRE `61M`, app jar `72K`, Docker Desktop idle RSS yaklaşık `33 MiB`.</small> |
| <small>`rest-sample-dubbo-consumer:full-static-jlink`</small> | <small>`docker build -f`<br>`rest-sample-dubbo-consumer/docker/images/`<br>`Dockerfile.jlink.full-static.workspace`<br>`-t rest-sample-dubbo-consumer:full-static-jlink .`</small> | <small>Static provider adresiyle typed `record/list/map/scalar` örnekleri ve POST/PATCH/DELETE command örnekleri de gerekiyorsa.</small> | <small>ZooKeeper discovery.</small> | <small>JRE `80M`, Docker Desktop idle RSS yaklaşık `39 MiB`.</small> |
| <small>`rest-sample-dubbo-consumer:zookeeper-jlink`</small> | <small>`docker build -f`<br>`rest-sample-dubbo-consumer/docker/images/`<br>`Dockerfile.jlink.zookeeper.workspace`<br>`-t rest-sample-dubbo-consumer:zookeeper-jlink .`</small> | <small>Provider discovery mutlaka ZooKeeper'dan gelecekse.</small> | <small>En küçük static-only mod.</small> | <small>JRE `80M`, warm state'e göre Docker Desktop RSS yaklaşık `42-51 MiB`.</small> |

Seçim kuralı:

- **BEST:** Stabil provider Service arkasında read-only JSON pass-through varsa `native-static-jlink`.
- **ACCEPTABLE:** Aynı consumer typed DTO ve command örneklerini de taşıyacaksa `full-static-jlink`.
- **SADECE GEREKİYORSA:** Provider discovery production sözleşmesi ZooKeeper ise `zookeeper-jlink`.
- **ANTI-PATTERN:** Sadece uygulama Kubernetes'te çalışıyor diye basit Service DNS senaryosuna ZooKeeper image koymak.

`native-static-jlink` image raw-only `CatalogJsonService` contract'ını kullanır. Bu yüzden
`/api/v1/catalog/nested` çalışır, `/api/v1/catalog/info` gibi typed endpoint'ler bu image'da `404`
döner. Bu bilinçli bir sınırdır: desteklenmeyen route'lar class, serializer ve JDK modülü
taşımamalıdır.

## Postman Collection

Consumer endpoint'lerini curl yazmadan denemek istiyorsanız şu collection dosyasını Postman'a import
edin:

```text
artifacts/postman/rest-sample-dubbo-consumer.postman_collection.json
```

Collection default olarak `baseUrl=http://localhost:8080` kullanır. İçinde health, catalog read,
customer read ve POST/PATCH/DELETE command örnekleri vardır. Önce `rest-sample-dubbo-provider`,
sonra bu consumer uygulamasını başlatın.

## Typed DTO Kontrol Listesi

`POST /api/v1/customers/typed`, `GET /api/v1/customers/db/stats` ve
`GET /api/v1/customers/db/by-segment` gibi typed Dubbo örnekleri bilinçli olarak Java record
kullanır. Consumer field validate edecek veya typed sonuca göre karar verecekse bu yol okunaklıdır;
ama iki kayıt mekanizması açık olmalıdır:

| Dosya / ayar | Neden var? | Eksikse ne olur? |
|--------------|------------|------------------|
| DTO record'larında `@CompiledJson` | REST request parse için build-time DSL-JSON reader/writer üretir. | `@RequestBody CreateCustomerCommand` "Unable to find reader" hatası verir. |
| `META-INF/services/com.dslplatform.json.Configuration` | Üretilen DSL-JSON converter'ları reflection fallback açmadan framework'e tanıtır. | Converter class'ları oluşur ama framework yükleyemez. |
| `security/serialize.allowlist` | Dubbo/Hessian sadece güvenilen DTO paketini deserialize edebilsin diye vardır. | Provider typed command request için Hessian status `40` dönebilir. |
| `java-rust-dubbo` full profile + `hessian-lite` | Argümanlı method'lar ve typed record/list/scalar cevaplar için gerekir. | Sadece argümansız `byte[]` native-static çağrıları güvenli kalır. |

Typed DTO hatalarını serialization güvenliğini tamamen kapatarak veya genel JSON reflection fallback
açarak çözmeyin. Bu sample iki yolu da açık kayıtla yönetir; runtime davranışı öngörülebilir ve
düşük overhead kalır.

Sample ayrıca varsayılan olarak `sample.dubbo.read-retry-on-io-error=true` ile gelir. Bu genel bir
Dubbo business retry değildir; sadece read/query tarafı için dar bir güvenlik supabıdır. Önceki hatalı
Dubbo çağrısı veya provider restart sonrası native pool içinde stale TCP bağlantı kaldıysa,
idempotent GET/query çağrıları Windows `os error 10053` gibi connection abort hatasında bir kez daha
dener. POST/PATCH/DELETE command çağrılarında bu retry yoktur; çünkü otomatik write retry duplicate
business işlem yaratabilir.

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

## DB Read, Distributed Write ve Hot-Row Write Tuning

Tüm Dubbo route'larını tek bir sepet gibi tune etmeyin. Son sample üç farklı production baskısını
ayrı ele alır; çünkü bu üç sınıf farklı sebeplerle yavaşlar veya `503` üretir:

| Workload sınıfı | Örnek endpoint | Ana limit | `503` genelde ne demek? | İlk tuning hamlesi |
|-----------------|----------------|-----------|--------------------------|--------------------|
| DB read | `GET /api/v1/customers/db` | Consumer route admission + provider query bulkhead + Hikari pool | Read path, kabul edilen queue budget'tan yavaş. | `reactor.rust.route-admission.get.api.v1.customers.db.*` değerlerini provider `CustomerQueryService` ve `sample.db.maximum-pool-size` ile birlikte tune edin. |
| Distributed write | Çok farklı id'lere giden `PATCH /api/v1/customers/{id}/segment` | Route admission + provider command bulkhead + DB pool | Command path dolu, fakat tek bir row lock'a sıkışmış değil. | Balanced reçeteyi ancak useful 2xx RPS, p99, RSS ve provider DB wait birlikte iyiyse kullanın. |
| Hot-row write | Aynı id'ye giden çok sayıda `PATCH /api/v1/customers/1/segment` | Per-key command admission | Aynı business key aşırı yükleniyor; bu native Dubbo bug değil, bilinçli fail-fast davranışıdır. | Per-key admission açık kalsın. Her update kabul edilecekse idempotency, optimistic locking, outbox/queue veya serialized command flow tasarlayın. |

Consumer per-key command admission durumunu şu endpoint ile gösterir:

```text
GET /app/command-key-admission
```

Örnek cevap:

```json
{"enabled":true,"stripes":1024,"accepted":405,"rejected":19257}
```

Bu endpoint iki farklı problemi ayırmak için önemlidir:

| Belirti | Anlamı | Ne yapılmalı? |
|---------|--------|---------------|
| `route_admission_rejected` artıyor, `command_key_admission_rejected=0` | Route, daha derin katmana inmeden overload veriyor. | Route max-concurrent/queue timeout veya provider kapasitesini tune edin. |
| Tek id üzerinde `command_key_admission_rejected` artıyor | Aynı customer/order/account key paralel update almaya çalışıyor. | Global worker artırmayın; business write modelini veya per-key policy'yi düzeltin. |
| Native Dubbo error `0`, ama non-2xx artıyor | Transport sağlıklı; sistem overload control uyguluyor. | Native transport ayarı değiştirmeden önce route/provider/DB/key metriklerini okuyun. |

Faydalı diagnostic endpoint'ler:

| Endpoint | Ne için kullanılır? |
|----------|---------------------|
| `GET /app/native-metrics` | Prometheus formatında native route/JNI/body/cache counter'ları. |
| `GET /app/native-diagnostics` | RSS incelemesi için native memory ve allocator diagnostic bilgisi. |
| `GET /app/command-key-admission` | Per-key command admission counter'larının güncel durumu. |
| `GET /app/metrics/reset` | Ölçümlü benchmark öncesi counter resetlemek için. Public dışarı açmayın. |

Varsayılan key policy konservatiftir:

```properties
sample.command.customer-key-admission.enabled=true
sample.command.customer-key-admission.max-concurrent-per-key=1
sample.command.customer-key-admission.stripes=1024
```

`max-concurrent-per-key=1`, DB row update için en güvenli başlangıçtır. Bunu artırmak benchmark'ta
daha iyi görünebilir; fakat PostgreSQL row lock beklemesini p99 spike'a çevirebilir. Sadece provider
idempotency/optimistic locking kullanıyorsa ve DB aynı key için paralel update'i kaldırdığını
kanıtladıysa artırın.

### Benchmark Gate Nasıl Okunur?

Benchmark raporu sadece raw RPS vermez; bilinçli olarak şu alanları da taşır:

| Kolon | Neden önemli? |
|-------|---------------|
| `workload_class` | Endpoint'i native read, DB read, create command, distributed command veya hot-row command olarak ayırır. Bunları ayrı tune edin. |
| `successful_2xx_rps` | Faydalı throughput. Raw RPS kontrollü `503` cevaplarını da içerdiği için tek başına yanıltıcıdır. |
| `non2xx_rate_percent` | Overload/error yüzdesi. Low-RSS profile spike altında bilinçli olarak `503` dönebilir. |
| `route_admission_rejected` / `route_admission_timeout` | Consumer route budget, request'i daha derine indirmeden reddetti veya timeout verdi. |
| `command_key_admission_rejected` | Aynı business key için hot-row write koruması devreye girdi. |
| `native_dubbo_errors` / `native_dubbo_handle_errors` | Native Dubbo transport/handle sağlığı. Temiz gate'te `0` kalmalıdır. |

### Runtime Profile Ve Benchmark Reçeteleri

İki isim tipini ayrı düşünün.

| İsim tipi | Örnek | Nerede kullanılır? | Anlamı |
|-----------|-------|--------------------|--------|
| Runtime profile | `micro-dubbo`, `balanced-dubbo` | Gerçek application property ve Kubernetes env değerlerinde. | Memory/throughput davranışını seçer. |
| Benchmark reçetesi | `micro-1x1`, `micro-2x2`, `balanced-stable-4x4` | Benchmark raporlarında ve tuning notlarında. | Test sırasında kullanılan connection, worker, queue ve route limitlerini gösterir. |

`reactor.runtime.profile=micro-1x1` yazmayın. Bu gerçek profile adı değildir. Aynı davranışı istiyorsanız
`reactor.runtime.profile=micro-dubbo` kullanın ve aşağıdaki property'leri ayrıca verin.

| Terim | Basit anlamı |
|-------|--------------|
| `micro` | Memory-first. Küçük queue, az worker, az connection, spike anında kontrollü `503`. |
| `balanced` | Daha fazla başarılı RPS. Daha çok connection, worker, queue ve RSS kullanır. |
| `1x1` | Provider endpoint başına `1` native Dubbo connection ve `1` native async worker. |
| `2x2` | `2` native Dubbo connection ve `2` native async worker. |
| `4x4` | `4` native Dubbo connection ve `4` native async worker. |
| `wide` | Route budget daha geniştir. `503` azalabilir ama RSS ve p99 artabilir. |
| `mid` | Orta route budget kullanır. Provider kapasitesi çok yüksek değilse `wide` değerinden daha güvenlidir. |
| `stable` | Route budget daha dar, queue timeout daha kısadır. p99/RSS korur, `503` daha erken gelebilir. |

| Reçete | Ne zaman kullanılır? | Ana ayarlar | Bedel |
|--------|----------------------|-------------|-------|
| `micro-1x1` | En küçük Dubbo consumer pod, düşük/orta trafik, küçük DB pool kullanan provider. | <small><code>reactor.runtime.profile=micro-dubbo</code><br><code>reactor.dubbo.native-connections-per-endpoint=1</code><br><code>reactor.dubbo.native-async-workers=1</code><br><code>reactor.dubbo.native-async-queue-capacity=32</code><br><code>reactor.dubbo.max-inflight=16</code></small> | En düşük RSS. Spike altında kontrollü `503` dönebilir. |
| `micro-2x2` | Provider tarafında boş kapasite var ve `1x1` ile p99 yüksek kalıyor. | <small><code>reactor.dubbo.native-connections-per-endpoint=2</code><br><code>reactor.dubbo.native-async-workers=2</code><br><code>reactor.dubbo.native-async-queue-capacity=128</code><br><code>reactor.dubbo.max-inflight=32</code></small> | Paralellik artar. RSS ve provider baskısı artar. |
| `balanced-stable-4x4` | Read-heavy servis, provider hazır JSON dönüyor ve overload saklanmadan daha çok 2xx isteniyor. | <small><code>reactor.runtime.profile=balanced-dubbo</code><br><code>reactor.dubbo.native-connections-per-endpoint=4</code><br><code>reactor.dubbo.native-async-workers=4</code><br><code>reactor.dubbo.native-async-queue-capacity=512</code><br><code>reactor.dubbo.max-inflight=64</code></small> | RPS artar. Provider CPU/DB ölçümü gerekir. |
| `balanced-mid-4x4` | Distributed command endpoint'leri daha çok kabul edilen iş istiyor ve provider kapasitesi ölçüldü. | Aynı `4x4` native ayarları, `stable` değerinden daha geniş route budget. | Daha çok 2xx alınabilir. p99/RSS izlenmelidir. |
| `balanced-wide-4x4` | Sadece ölçülmüş yüksek kapasiteli provider için. Default yapılmamalıdır. | Aynı `4x4` native ayarları, en geniş route budget. | Overload queue içinde saklanabilir. Dikkatli kullanın. |

`micro-1x1` tarzı servis için kopyala-yapıştır başlangıç:

```properties
reactor.runtime.profile=micro-dubbo
reactor.dubbo.runtime-profile=micro-dubbo
reactor.dubbo.native-connections-per-endpoint=1
reactor.dubbo.native-max-idle-connections-per-endpoint=1
reactor.dubbo.native-async-workers=1
reactor.dubbo.native-async-queue-capacity=32
reactor.dubbo.native-async-transport=blocking
reactor.dubbo.max-inflight=16
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=150
```

DB-backed endpoint için kural basittir: consumer'ı önce client concurrency değerine göre değil,
provider ve Hikari kapasitesine göre boyutlandırın. Provider'da iki DB connection varsa `c64`
trafik ya kısa süre beklemeli ya da kontrollü `503` almalıdır. Derin queue sadece RSS ve p99 artırır.

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
  -jar target/rest-sample-dubbo-consumer-0.1.1.jar
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
curl http://localhost:8080/app/ready
curl http://localhost:8080/api/v1/catalog/dubbo-metrics
```

Etkisi: liveness ZooKeeper, provider CPU veya DB latency'ye bağımlı olmaz. Readiness için
`/app/ready` gerçek Dubbo/provider yolunu kontrol eder; Kubernetes pod'a trafik vermeden önce bu
yolun hazır olduğunu görür.

## Production Senaryo Rehberi

Bu bölüm tek tek property ezberletmek için değil, gerçek hayatta karşılaşacağınız servis
şekillerine göre karar vermeniz için hazırlandı. Aynı consumer içinde müşteri okuma, sipariş
getirme, katalog listeleme, müşteri yaratma, statü güncelleme ve silme endpoint'leri birlikte
çalışabilir. Her endpoint aynı maliyette değildir; bu yüzden her endpoint'e aynı kuyruk, aynı
timeout ve aynı concurrency vermek production'da doğru davranış değildir.

### Önce Terimleri Netleştirelim

| Terim | Basit açıklama | Bu projede neye etki eder? |
|-------|----------------|----------------------------|
| `p99` | 100 isteğin 99'u bu sürenin altında biter.<br>Örnek: p99 120 ms ise en yavaş yüzde 1 bu bandı görür. | Kullanıcının hissettiği yavaşlamayı average latency'den daha iyi gösterir. |
| `503` | Servisin "şu anda bu isteği alamam" demesidir. Hata gibi görünür ama kontrollü overload için bilinçli kullanılır. | Kuyruk şişip memory ve latency patlamasın diye bazı istekler erken reddedilir. |
| Hot read | Çok çağrılan okuma endpoint'i. Örnek: müşteri getir, sipariş getir, katalog getir. | Daha fazla route budget alabilir ama DB/provider kapasitesini aşmamalıdır. |
| Cold route | Seyrek çağrılan endpoint. Örnek: admin işlem, seyrek patch, silme. | Hot endpoint'lerin kapasitesini çalmamalıdır. |
| Write command | Veri değiştiren işlem. Örnek: müşteri yarat, sipariş iptal et, müşteri statüsü değiştir. | Retry ve derin kuyruk tehlikelidir; idempotency gerekir. |
| Route budget | Bir endpoint'in aynı anda kaç işi içeri alabileceğidir. | `reactor.rust.route-admission...max-concurrent` ile ayarlanır. |
| Queue timeout | Route budget doluyken isteğin ne kadar bekleyebileceğidir. | `queue-timeout-ms` büyürse 503 azalabilir ama p99 ve memory artabilir. |
| In-flight | Şu anda işlenmekte olan istek veya response demektir. | Çok büyürse RSS ve p99 yükselir. |
| RSS | Pod'un işletim sistemi gözünden tuttuğu gerçek memory miktarıdır. | Kubernetes memory limitini asıl etkileyen değerdir. |
| Consumer | Bu proje: REST isteğini alır, gerekiyorsa Dubbo provider'a gider ve response döner. | REST + Dubbo ayarları bu tarafta yapılır. |
| Provider | Arkadaki Dubbo servisidir. DB'ye gider, iş mantığını çalıştırır veya JSON üretir. | Provider yavaşsa consumer kuyruğunu büyütmek tek başına çözüm değildir. |
| RawResponse | Provider'dan gelen JSON byte'larını Java DTO parse/serialize yapmadan direkt response olarak döndürme yoludur. | Düşük allocation, düşük CPU ve daha düşük RSS için önerilir. |

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

| Durum | Önceki değer | Değişiklik | Beklenen düzelme | Bedel / dikkat |
|-------|--------------|------------|------------------|----------------|
| Customer DB p99 yükseldi | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=12</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8</code></small> | DB read provider hızına iner | `503` biraz artabilir |
| Create yavaş / duplicate riski | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=10</code><br><code>reactor.dubbo.retries=1</code></small> | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=6</code><br><code>reactor.dubbo.retries=0</code></small> | Retry storm azalır | Idempotency ayrıca çözülmeli |
| Ucuz katalog read 503 alıyor | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16</code><br><code>reactor.dubbo.native-connections-per-endpoint=1</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=24</code><br><code>reactor.dubbo.native-connections-per-endpoint=2</code></small> | Hot read üretkenliği artar | Provider CPU sağlıklı olmalı |
| Tüm endpoint p99 yükseliyor | Global queue büyütüldü | Queue'u geri al<br>route budget ayır | Yavaş route sistemi boğmaz | Endpoint metriği şart |

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

| Durum | Önceki değer | Değişiklik | Beklenen düzelme | Bedel / dikkat |
|-------|--------------|------------|------------------|----------------|
| Idle RSS yüksek | <small><code>reactor.rust.native-trim.enabled=false</code><br>response pool değerleri geniş</small> | <small><code>reactor.rust.native-trim.enabled=true</code><br><code>reactor.rust.response-pool.small-capacity=8</code><br><code>reactor.rust.response-pool.medium-capacity=2</code><br><code>reactor.rust.response-pool.large-capacity=1</code></small> | Idle memory geri bırakılır | Trim sadece idle'da |
| Hot read 503 yüksek, RSS güvenli | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=12</code><br><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=6</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16</code><br><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8</code></small> | Faydalı `200` RPS artar | RSS/DB wait tekrar ölçün |
| Pod limiti sıkı | <small><code>reactor.rust.http.max-connections=512</code><br><code>reactor.dubbo.max-inflight=16</code></small> | <small><code>reactor.rust.http.max-connections=256</code><br><code>reactor.dubbo.max-inflight=8</code></small> | RSS kontrollü kalır | Spike'ta 503 artar |
| Trim sonrası p99 dalgalı | <small><code>reactor.rust.native-trim.interval-ms=15000</code></small> | <small><code>reactor.rust.native-trim.initial-delay-ms=30000</code><br><code>reactor.rust.native-trim.interval-ms=60000</code><br><code>reactor.rust.native-trim.min-idle-ms=10000</code></small> | Trim daha sakin çalışır | RSS düşüşü yavaşlar |

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

| Durum | Önceki değer | Değişiklik | Beklenen düzelme | Bedel / dikkat |
|-------|--------------|------------|------------------|----------------|
| Büyük JSON limit'e takılıyor | <small><code>reactor.rust.http.max-response-body-bytes=8388608</code><br><code>reactor.dubbo.max-response-bytes=8388608</code></small> | <small><code>reactor.rust.http.max-response-body-bytes=16777216</code><br><code>reactor.dubbo.max-response-bytes=16777216</code><br><code>reactor.rust.http.max-inflight-response-bytes=33554432</code></small> | Response dönebilir | Toplam in-flight da şart |
| Büyük JSON RSS artırıyor | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=10</code> veya <code>12</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6</code></small> | Aynı anda büyük body azalır | Büyük endpoint RPS düşer |
| Küçük read büyük JSON'dan yavaş | Tüm route budget aynı | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=32</code><br><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6</code></small> | Kuyruklar ayrışır | Route key doğru olmalı |
| Provider CPU boş, p99 yüksek | <small><code>reactor.dubbo.native-connections-per-endpoint=1</code><br><code>reactor.dubbo.native-async-workers=1</code></small> | <small><code>reactor.dubbo.native-connections-per-endpoint=3</code><br><code>reactor.dubbo.native-async-workers=2</code></small> | Native data-plane paralelliği artar | Provider doygunsa fayda yok |

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

| Durum | Önceki değer | Değişiklik | Beklenen düzelme | Bedel / dikkat |
|-------|--------------|------------|------------------|----------------|
| Create DB'yi zorluyor | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=8</code> veya <code>10</code></small> | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=4</code><br><code>reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=100</code></small> | DB write pressure düşer | Peak write RPS düşer |
| Timeout sonrası tekrar | <small><code>reactor.dubbo.retries=1</code><br>blind client retry</small> | <small><code>reactor.dubbo.retries=0</code><br>client idempotency key zorunlu</small> | Duplicate riski azalır | Garanti için durable workflow |
| Patch route'ları eziyor | Segment/status aynı yüksek budget | <small><code>reactor.rust.route-admission.patch.api.v1.customers.id.segment.max-concurrent=4</code><br><code>reactor.rust.route-admission.patch.api.v1.customers.id.status.max-concurrent=4</code></small> | Patch türleri ayrışır | Optimistic lock/idempotency gerekir |
| Delete nadir ama pahalı | <small><code>reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=4</code></small> | <small><code>reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=2</code></small> | Daha kontrollü akar | Admin spike'ta 503 görebilir |

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

| Durum | Önceki değer | Değişiklik | Beklenen düzelme | Bedel / dikkat |
|-------|--------------|------------|------------------|----------------|
| Pod RSS yüksek | <small><code>reactor.rust.http.max-connections=512</code><br><code>reactor.rust.response-pool.small-capacity=32</code><br><code>reactor.rust.response-pool.medium-capacity=8</code></small> | <small><code>reactor.rust.http.max-connections=256</code><br><code>reactor.rust.response-pool.small-capacity=8</code><br><code>reactor.rust.response-pool.medium-capacity=2</code></small> | Daha az idle/native buffer | Yatay ölçekle dengeleyin |
| Kampanya hot read 503 yüksek | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=8</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16</code></small> | Hot read daha çok istek alır | RSS/provider CPU ölçün |
| Admin/write hot read'i etkiliyor | Admin/write budget yüksek | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=2</code><br><code>reactor.rust.route-admission.delete.api.v1.customers.id.max-concurrent=1</code></small> | Seyrek route kapasite çalmaz | Admin fail-fast olur |
| 503 için global queue büyüdü | <small><code>reactor.rust.jni.queue-capacity=512</code></small> | <small><code>reactor.rust.jni.queue-capacity=128</code><br>route budget ayrıştır</small> | Global p99 artışı azalır | Hot endpoint budget gerekir |

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

| Durum | Önceki değer | Değişiklik | Beklenen düzelme | Bedel / dikkat |
|-------|--------------|------------|------------------|----------------|
| Provider CPU boş, p99 yüksek | <small><code>reactor.dubbo.max-inflight=24</code><br><code>reactor.dubbo.native-connections-per-endpoint=2</code></small> | <small><code>reactor.dubbo.max-inflight=64</code><br><code>reactor.dubbo.native-connections-per-endpoint=4</code></small> | Daha fazla paralel Dubbo call | RSS/DB pool ölçün |
| Sadece lookup yavaş | Hot route budget aynı | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=12</code><br>catalog route değerleri aynı kalır</small> | Problemli route ayrılır | DB wait artarsa düşürün |
| Büyük history küçük lookup'ı bozuyor | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=12</code></small> | <small><code>reactor.rust.route-admission.get.api.v1.catalog.db.customers.max-concurrent=6</code><br>small lookup route budget yüksek kalır</small> | Küçük lookup p99 korunur | Büyük JSON RPS düşer |
| Admission kapatıldı | Route budget yok | Budget'ları geri koy<br>sadece hot read artır | Yavaş route sistemi kilitlemez | Config daha detaylıdır |

## `rust-java-rest` 3.2.7 Bu Örnekte Ne Değiştiriyor?

Bu örnek artık `rust-java-rest` `3.2.7` ve `java-rust-dubbo` `0.2.3` kullanır. Uygulama kodu modeli değişmez: handler'lar,
service adapter'ları, configuration class'ları ve business kararlar Java'da kalır. Değişiklik daha
çok handler'ların altında çalışan runtime yolundadır.

| Güncel değişiklik | Bu örnekte etkisi |
|-----------------|-------------------|
| `RestApplication` bootstrap | Tekrar eden HTTP startup/shutdown kodu framework'e taşındı; aktif handler listesi yine kodda explicit kalır. |
| `DubboConsumerSupport` | Static veya ZooKeeper discovery ve reference default'ları property'den okunur; servis interface listesi gizlenmez. |
| Daha düşük retention yapan response pool'lar | Trafik düşükken consumer daha az native response buffer tutar. |
| Bounded in-flight response byte limiti | Büyük veya yavaş response'lar memory kullanımını limitsiz büyütemez. |
| UTF-8 response/path/query düzeltmeleri | Request değerleri ve response bytes UTF-8 ise Türkçe karakterler güvenli taşınır. |
| Native response handle yolu | Java response body'yi okumayacaksa provider JSON `RawResponse.nativeResponse(handle.nativeId())` ile Java heap'e `byte[]` olarak alınmadan döner. |
| Raw/precomputed response yolunun olgunlaşması | Java response'u inceleyecek, dönüştürecek veya loglayacaksa provider JSON `byte[]` olarak alınıp `RawResponse.json(bytes)` ile döner. |
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
Provider JSON'u native handle yolu ile döner
Consumer RawResponse.nativeResponse(handle.nativeId()) döner
Rust-Java runtime HTTP response'u yazar
```

Handler provider payload'ını incelemek veya dönüştürmek zorundaysa `RawResponse.json(bytes)` kullanın.

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
  <version>3.2.7</version>
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

| Profile | Ne kullanır? | Ne için uygun? | Sınırı |
|---------|--------------|----------------|--------|
| `full-dubbo-consumer` | Full artifact + `hessian-lite`<br>Varsayılan profile | POST/PATCH/DELETE dahil tüm endpoint'ler | En küçük read-only path'e göre daha geniş classpath |
| `native-static-consumer` | `native-static` classifier<br>Hessian/ZooKeeper yok | Static provider + no-arg `byte[]` read | Argümanlı Dubbo method için full profile gerekir |
| `zookeeper-discovery` | Full artifact + `hessian-lite` + ZooKeeper client | K8s veya zorunlu ZooKeeper discovery | Java ZooKeeper class/thread maliyeti ekler |

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

### Deklaratif Consumer Yüzeyi

Consumer bootstrap için kural basittir: önce yüzeyi seç, sonra sadece o yüzeye ait handler'ları
register et.

| Yüzey | Nasıl seçilir? | Register edilen handler'lar | Ne zaman kullanılır? |
|-------|----------------|-----------------------------|----------------------|
| `full` | `sample.consumer.surface=full` | Health, catalog, customer query/command | Bütün sample endpoint'leri gerekiyorsa. |
| `catalog-only` | `sample.consumer.surface=catalog-only` | Health + sadece catalog | Daha küçük read-only consumer gerekiyorsa. |
| `native-static-consumer` profile | Maven profile + static provider adresi | Native static handler'lar | En küçük no-argument JSON pass-through yolu gerekiyorsa. |

Örnek:

```properties
sample.consumer.surface=catalog-only
sample.dubbo.discovery=static
reactor.dubbo.providers=rest-sample-dubbo-provider:20880
reactor.runtime.profile=micro-dubbo
```

Sample, business davranışı belirlemek için geniş bir reflection scanner kullanmaz. Aktif handler'lar
ve Dubbo client'lar açıkça görünür. Tekrar eden HTTP bootstrap ve Dubbo config builder kodu küçük
support class'larda durur. Bu yaklaşım class loading'i öngörülebilir tutar ve memory ölçümlerini
daha güvenilir hale getirir.

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

| Servis şekli | Başlangıç limiti | Neden |
|--------------|-----------------:|-------|
| Static provider, düşük trafik, sadece JSON pass-through | `128Mi` | Rust-Java REST küçük kalır; consumer Java ZooKeeper/Dubbo Netty runtime yüklemez. |
| Static provider ve DB-backed Dubbo route, orta yük | `160Mi` | DB çağrıları spike anında queue ve retained buffer baskısını artırır. |
| Consumer içinde ZooKeeper discovery açık | `160Mi` veya üstü | Java ZooKeeper client thread/class/session state ekler. Sadece gerekiyorsa kullanın. |
| Daha yüksek concurrency Dubbo workload | `192Mi` seviyesinden ölçün | Route admission ve native connection birlikte artırılmalı; p99, 503 oranı ve RSS birlikte izlenmeli. |

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
    return catalogClient.nestedCatalogNativeJsonAsync()
            .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())));
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
  -> Rust native memory içinde tutulan JSON bytes
  -> RawResponse.nativeResponse(nativeResponseId)
  -> Rust HTTP response
```

Kritik nokta: consumer provider JSON'unu DTO'ya parse edip tekrar serialize etmez. Provider JSON
bytes üretiyorsa ve consumer response'u incelemeyecekse `invokeNativeJsonResponseAsync()` ile
`RawResponse.nativeResponse(handle.nativeId())` kullanılır. Böylece response body Java heap'e
`byte[]` olarak alınmadan Rust HTTP response'a taşınır. Java response'u inceleyecek, dönüştürecek,
validate edecek veya loglayacaksa `invokeAsync().thenApply(bytes -> RawResponse.json(bytes))` yolu
hâlâ doğru ve desteklenen yoldur.

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

### Veri Akışı Kararı: byte mı, record mı?

Bu karar sadece Java tipi seçimi değildir. Hangi process'in JSON'u okuyacağını, validation'ın nerede
yapılacağını, hangi tarafta object graph oluşacağını ve p99/RSS maliyetini belirler.

| Seçim | Veri akışı | Consumer maliyeti | Validation yeri | Ne zaman |
|-------|------------|-------------------|-----------------|----------|
| `byte[] -> byte[]` | HTTP bytes -> Dubbo bytes -> `RawResponse` | En düşük<br>DTO graph yok | Provider veya lightweight byte validator | Read-heavy, pass-through JSON |
| `record -> record` | JSON -> record -> Dubbo -> record -> JSON | Orta<br>parse + serialize | Consumer typed validation | Consumer field okuyacaksa |
| `record -> byte[] -> RawResponse` | JSON -> record validate -> bytes -> provider bytes | Orta request<br>düşük response | Consumer request'i, provider domain'i validate eder | İyi hibrit model |
| `record -> byte[] -> record` | Record request + response tekrar parse | Yüksek<br>çift parse/serialize | Consumer response'u da okuyacaksa | Sadece gerçekten gerekirse |
| `record -> List<record>` | Liste + item record graph | En yüksek | Consumer typed result işler | Küçük, bounded page |

BEST seçim: consumer sadece provider cevabını HTTP client'a taşıyorsa response'u `record`'a çevirmeyin.
`RawResponse.json(providerBytes)` dönün. Böylece response tarafında parse, object allocation ve tekrar
JSON serialization oluşmaz.

```java
@PostMapping(value = "/customers", responseType = RawResponse.class)
public CompletableFuture<ResponseEntity<RawResponse>> createCustomer(@RequestBody CustomerCreateRequest request) {
    validateCreateRequest(request);
    byte[] commandJson = customerJsonWriter.write(request);

    return customerCommandClient.createCustomer(commandJson)
            .thenApply(providerJson -> ResponseEntity.ok(RawResponse.json(providerJson)));
}
```

Bu hibrit modelde consumer request'i typed şekilde validate eder; provider domain rule, DB constraint ve
response JSON shape'in sahibi kalır. Consumer provider response'unu okumadığı için response object graph
oluşmaz.

| Sınıf | Model | Neden |
|-------|-------|-------|
| BEST | `byte[] -> byte[]` + `RawResponse` | En düşük allocation ve en stabil p99 |
| BEST | `record request -> byte[] provider -> RawResponse` | Request validation var, response copy/parse az |
| ACCEPTABLE | `record -> record` | Küçük typed business response için okunabilir |
| ACCEPTABLE | `List<record>` | Sadece küçük sayfa + strict `limit` ile |
| ANTI-PATTERN | Hot path'te `record -> byte[] -> record -> JSON` | Byte transport avantajını kaybettirir |
| ANTI-PATTERN | Limitsiz `List<record>` | Heap, GC, p99 ve RSS büyür |

Validation sorumluluğunu da bilinçli ayırın:

| Validation tipi | Nerede yapılmalı? | Neden |
|-----------------|-------------------|-------|
| Basic request shape | Consumer | Hatalı request provider'a gitmeden reddedilir |
| Domain rule / DB uniqueness | Provider | Verinin sahibi provider'dır |
| Auth / tenant boundary | Consumer veya gateway | HTTP boundary'ye en yakın yerde fail-fast |
| Response contract check | Test/contract suite | Her response'u runtime'da parse etmek pahalıdır |
| Büyük JSON schema validation | Hot path dışında | CPU ve allocation maliyeti yüksektir |

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
| `byte[] method()` | No-arg byte-array çağrılar için native fast path. | En düşük heap için `invokeNativeJsonResponseAsync()` ve `RawResponse.nativeResponse(handle.nativeId())`; Java `byte[]` sadece `invokeAsync()` çağırırsanız oluşur. |
| `byte[] method(byte[] json)` | Body alan command/read route için native path. | Rust byte[] argümanı encode eder ve response JSON'u native memory'de tutar. Consumer response'u tekrar okumayacaksa idealdir. |
| `byte[] method(long id, byte[] json)` | Id + body alan command/read route için native path. | Primitive id Rust Hessian subset ile encode edilir; response yine Java heap'e alınmadan taşınır. |
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
    <version>3.2.7</version>
</dependency>

<dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo</artifactId>
    <version>0.2.3</version>
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

| Method şekli | Örnek / Endpoint | Kullanım | Runtime maliyeti |
|--------------|------------------|---------|------------------|
| `byte[]` JSON, no-arg | `getNestedCatalogJson()`<br>`GET /api/v1/catalog/nested` | Provider JSON shape sahibiyse<br>consumer sadece forward eder | En düşük<br>native no-arg fast path, DTO graph yok |
| `String` | `getCatalogTitle()`<br>`GET /api/v1/catalog/title` | Küçük label, başlık, tek değer | Küçük String allocation<br>REST JSON wrapper |
| Primitive/scalar | `countCatalogItems()`<br>`customerExists(id)` | Count, flag, ucuz lookup | Hessian decode var<br>büyük object graph yok |
| Java `record` | `getCatalogInfo()`<br>`getCustomer(id)` | Field okuyup branching/validation/enrichment yapılacaksa | Hessian record + REST JSON serialize |
| `List<record>` | `listFeaturedItems(limit)`<br>`findCustomersBySegment(...)` | Küçük bounded sayfalar | Liste + item allocation<br>`limit` zorunlu bounded |
| `Map<String,String>` | `getCatalogAttributes()`<br>`GET /api/v1/catalog/attributes` | Küçük metadata | Map allocation<br>büyük dynamic payload için kullanmayın |
| `record -> record` command | `createCustomerTyped(...)`<br>`POST /api/v1/customers/typed` | Okunabilir typed command contract | Request encode + response decode + REST serialize |
| `byte[] -> byte[]` command | `createCustomer(byte[])`<br>`POST /api/v1/customers` | En düşük allocation command pass-through | Bytes taşınır<br>validation provider'da kalır |

Production kuralı: record daha okunabilir diye tüm `byte[]` method'larını record'a çevirmeyin.
Consumer typed business data ile karar verecekse record kullanın. Consumer sadece provider JSON'unu
HTTP'ye taşıyorsa `byte[] + RawResponse` yolunu koruyun.

## GitHub Packages

`rust-java-rest`, `java-rust-dubbo`, `rest-sample-utility` ve `rust-sample-model` sadece GitHub Packages
üzerinden yayınlandıysa Maven için repo ve credential gerekir.

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
    <repository>
        <id>github-rest-sample-utility</id>
        <url>https://maven.pkg.github.com/esasmer-dou/rest-sample-utility</url>
    </repository>
    <repository>
        <id>github-rust-sample-model</id>
        <url>https://maven.pkg.github.com/esasmer-dou/rust-sample-model</url>
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
        <server>
            <id>github-rest-sample-utility</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
        <server>
            <id>github-rust-sample-model</id>
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

| Property | Ne işe yarar? | Ne zaman değiştirirsin? |
|----------|---------------|-------------------------|
| `server.port` | HTTP portunu belirler. | Container veya lokal port farklıysa değiştir. |
| `reactor.runtime.profile` | Runtime preset seçer. | Düşük RSS için `micro-dubbo` kalsın. Sadece benchmark sonrası değiştir. |
| `reactor.startup.component-index.*` | Checked-in component index kullanır. | Component eklediğinde index'i yeniden üret. |
| `reactor.startup.route-index.*` | Checked-in route index doğrular. | Route eklediğinde index'i yeniden üret. |
| `reactor.rust.jni.workers` | Java handler worker sayısını belirler. | Sadece Java handler işi darboğaz ise artır. |
| `reactor.rust.http.max-response-body-bytes` | Tek HTTP response body limitidir. | Büyük provider JSON için artır. |
| `reactor.rust.http.max-inflight-response-bytes` | Aktif tüm response byte bütçesidir. | Büyük response reject oluyorsa body limitiyle birlikte artır. |
| `reactor.rust.http.max-connections` | Kabul edilen HTTP connection sayısını sınırlar. | Kubernetes içinde bounded tut. |
| `reactor.rust.response-pool.*` | Native response buffer retention limitlerini belirler. | Idle RSS için düşür. Allocation churn ölçülürse artır. |
| `reactor.rust.json.writer-retain-max-bytes` | JSON writer buffer retention üst sınırıdır. | Idle RSS önemliyse düşür. |
| `reactor.rust.native-cache.max-bytes` | Opsiyonel native response cache byte limitidir. | Sadece bilinçli cacheable response için kullan. |
| `reactor.rust.route-admission.*` | Her route'u global queue öncesinde sınırlar. | Global worker artırmadan önce hot endpoint'i tune et. |
| `sample.dubbo.discovery` | `static` veya `zookeeper` modunu seçer. | Service DNS için `static`; discovery zorunluysa `zookeeper`. |
| `reactor.dubbo.providers` | Static provider adreslerini verir. | Static modda doldur. Kubernetes'te `127.0.0.1` kullanma. |
| `reactor.dubbo.registry-address` | ZooKeeper adresini verir. | Sadece ZooKeeper modunda doldur. |
| `reactor.dubbo.timeout-ms` | RPC timeout değeridir. | Provider p99 ve HTTP timeout ile hizala. |
| `reactor.dubbo.max-inflight` | Eşzamanlı RPC çağrılarını sınırlar. | Memory için düşür. Provider boşluğu varsa artır. |
| `reactor.dubbo.native-connections-per-endpoint` | Provider başına native TCP pool boyutudur. | Memory-first servislerde düşük tut. Sadece p99/RSS kanıtıyla artır. |

Hızlı semptom rehberi:

| Bunu görüyorsanız | Önce şu key'lere bakın | İlk kontrol |
|-------------------|-------------------------|-------------|
| Request body reject / payload küçük | `max-request-body-bytes`<br>`max-inflight-body-bytes` | Tek body ve toplam in-flight limitini birlikte artırın. |
| Provider JSON çok büyük diye reject oluyor | `reactor.dubbo.max-response-bytes`, `reactor.rust.http.max-response-body-bytes`, `reactor.rust.http.max-inflight-response-bytes` | Üç limit de payload'a izin vermeli. |
| Trafik spike altında çok `503` var | Route-specific `reactor.rust.route-admission.*`, `reactor.dubbo.max-inflight` | Provider CPU/DB pool boşluğu varsa route budget artırın. |
| p99 büyüyor ama RSS hâlâ düşük | `reactor.dubbo.native-connections-per-endpoint`, `reactor.dubbo.native-async-workers`, route queue timeout | Çok Java worker eklemeden önce connection reuse iyileştirin. |
| Trafik durduktan sonra RSS yüksek kalıyor | `reactor.rust.response-pool.*`<br>`reactor.rust.json.writer-retain-max-bytes`<br>`reactor.rust.native-trim.*` | Idle trim'i düşük trafikli podda açın; p99'u ölçün. |
| Startup index hatası | `component-index.*`<br>`route-index.*`<br>`scan.fallback-enabled` | Handler/route ekledikten sonra index dosyalarını güncelleyin. |
| Kubernetes static consumer provider bulamıyor | `sample.dubbo.discovery`<br>`reactor.dubbo.providers` | Service DNS, port ve readiness'ı kontrol edin.<br>`127.0.0.1` kullanmayın. |
| K8s ZooKeeper consumer provider bulamıyor | `sample.dubbo.discovery`<br>`registry-address`<br>`registry-root` | `zookeeper-discovery` ile build edin.<br>Registry DNS ve provider node'u kontrol edin. |
| Docker static consumer yanlış yere bağlanıyor | `reactor.dubbo.providers` | Docker network içinde `127.0.0.1` değil container/service DNS kullanın. |
| Write command yavaş veya dengesiz | `reactor.dubbo.retries`, command route admission key'leri, `reactor.dubbo.timeout-ms` | Retry `0` kalsın; bounded queue ve timeout'u tune edin. |
| Çok sayıda idle HTTP client kaynak tutuyor | `reactor.rust.http.max-connections`, `reactor.rust.http.idle-timeout-ms`, `reactor.rust.http.keep-alive-enabled` | Keep-alive kapatmadan önce idle timeout'u düşürün. |

### Tam Runtime Property Rehberi

Bu bölüm sample'ın tüm runtime yüzeyini anlatır: küçük
`src/main/resources/rust-spring.properties` başlangıç dosyası, isteğe bağlı
`src/main/resources/config/production.properties` overlay dosyası ve ölçüm sonrası kullanılan
`src/main/resources/config/advanced-tuning.properties` overlay dosyası. Bütün key'leri başlangıç
dosyasına kopyalamayın. Başlangıç dosyası küçük kalsın. Ortama uygun ayarları
`-Dreactor.config.file=...` veya `REACTOR_CONFIG_FILE` ile ayrıca yükleyin.

Kullanım sırası:

| Adım | Ne kullanılır? | Neden? |
|------|----------------|--------|
| Lokal smoke test | Sadece `rust-spring.properties` | Minimum güvenli değerlerle hızlı başlar. |
| Kubernetes / gerçek ortam | `config/production.properties` overlay | Ölçülmüş production limitlerini, provider adreslerini ve low-RSS gate'lerini ekler. |
| Ölçüm sonrası tuning | `config/advanced-tuning.properties` overlay | Route admission ve daha büyük/balanced Dubbo ayarlarını load testten sonra ekler. |

Server ve startup:

| Property | Default | Ne işe yarar / Ne zaman değiştirirsin? |
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

| Property | Default | Ne işe yarar / Ne zaman değiştirirsin? |
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

| Property | Default | Ne işe yarar / Ne zaman değiştirirsin? |
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

| Property | Default | Ne işe yarar / Ne zaman değiştirirsin? |
|----------|---------|----------------------------------|
| `reactor.rust.route-admission.enabled` | `true` | Route-level bounded overload kontrolünü açar. Dubbo-backed route'larda açık kalsın. |
| `reactor.rust.route-admission.default-max-concurrent` | `0` | Global default route limiti. `0` default cap yok demektir; sample explicit route key kullanır. |
| `reactor.rust.route-admission.default-queue-timeout-ms` | `0` | Global default queue wait. `0` default olarak queue yapılmaz demektir. |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent` | `16` | Read-heavy nested catalog cap. Provider hızlıysa artırın; provider CPU/RSS artıyorsa düşürün. |
| `reactor.rust.route-admission.get.api.v1.catalog.nested.queue-timeout-ms` | `100` | Catalog wait budget. Daha az 503 için artırın, daha sıkı p99 için düşürün. |
| `reactor.rust.route-admission.get.api.v1.catalog.title/count/info/items/attributes.*` | `16` / `100` | Typed catalog route cap'leri.<br>List/map allocation p99'u bozuyorsa düşürün. |
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

Command key admission:

| Property | Default | Ne işe yarar / Ne zaman değiştirirsin? |
|----------|---------|----------------------------------|
| `sample.command.customer-key-admission.enabled` | `true` | PATCH/DELETE gibi write route'larında customer id bazlı korumayı açar. DB row update için açık kalsın. |
| `sample.command.customer-key-admission.max-concurrent-per-key` | `1` | Tek customer id için aynı anda kaç command çalışabileceğini belirler. `1`, PostgreSQL row lock baskısını azaltır ve hot-row write'ı fail-fast yapar. |
| `sample.command.customer-key-admission.stripes` | `1024` | Per-key semaphore stripe sayısıdır. Çok yüksek unique-key concurrency varsa artırılabilir; yine de bounded kalır ve customer başına obje üretmez. |

Dubbo consumer:

| Property | Default | Ne işe yarar / Ne zaman değiştirirsin? |
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
| `reactor.dubbo.providers` | `127.0.0.1:20880` | Static provider listesi.<br>Docker/K8s'te Service DNS kullanın.<br>Örnek: `customer-provider:20880,order-provider:20880` |
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
| `reactor.dubbo.native-async-transport` | `blocking` | Native async transport seçimidir. `blocking` memory-first varsayılandır; `tokio-demux` yüksek concurrency için Rust async demux yoludur. |

<details>
<summary>Tüm sample property -> environment variable map'i</summary>

| Property | Environment variable |
|----------|----------------------|
| `server.port` | `SERVER_PORT` |
| `server.host` | `SERVER_HOST` |
| `reactor.runtime.profile` | `REACTOR_RUNTIME_PROFILE` |
| `reactor.dubbo.native-async-transport` | `REACTOR_DUBBO_NATIVE_ASYNC_TRANSPORT` |
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
| `sample.command.customer-key-admission.enabled` | `SAMPLE_COMMAND_CUSTOMER_KEY_ADMISSION_ENABLED` |
| `sample.command.customer-key-admission.max-concurrent-per-key` | `SAMPLE_COMMAND_CUSTOMER_KEY_ADMISSION_MAX_CONCURRENT_PER_KEY` |
| `sample.command.customer-key-admission.stripes` | `SAMPLE_COMMAND_CUSTOMER_KEY_ADMISSION_STRIPES` |
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

| Use case | Başlangıç ayarı | Neden |
|----------|----------------|-------|
| K8s Service DNS static | <small><code>SAMPLE_DUBBO_DISCOVERY=static</code><br><code>REACTOR_DUBBO_PROVIDERS=customer-provider:20880,order-provider:20880</code><br><code>REACTOR_RUNTIME_PROFILE=micro-dubbo</code><br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2</code><br><code>REACTOR_DUBBO_MAX_INFLIGHT=8-16</code></small> | ZooKeeper client/thread/class consumer'a girmez |
| ZooKeeper zorunlu düşük trafik | <small><code>SAMPLE_DUBBO_DISCOVERY=zookeeper</code><br><code>REACTOR_RUNTIME_PROFILE=micro-dubbo</code><br><code>REACTOR_RUST_JNI_WORKERS=1</code><br><code>REACTOR_DUBBO_MAX_INFLIGHT=8</code><br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=1</code></small> | REST process küçük kalır<br>overload anında kontrollü 503 |
| Read-heavy dashboard JSON | <small><code>REACTOR_DUBBO_MAX_INFLIGHT=16-32</code><br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2-4</code><br><code>REACTOR_RUST_ROUTE_ADMISSION_GET_API_V1_CATALOG_NESTED_MAX_CONCURRENT=16-64</code></small> | Hazır JSON `byte[]` ise 200 RPS artar |
| DB-backed query | <small><code>reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=4-8</code><br><code>REACTOR_DUBBO_TIMEOUT_MS=800-1500</code><br><code>reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=50-150</code></small> | Consumer provider DB pool saturation'ını büyütmez |
| POST/PATCH/DELETE command | <small><code>REACTOR_DUBBO_RETRIES=0</code><br><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent=4-8</code><br><code>reactor.rust.route-admission.post.api.v1.customers.queue-timeout-ms=100-200</code></small> | Duplicate write ve write pressure sınırlandırılır |
| Büyük JSON response | `REACTOR_DUBBO_MAX_RESPONSE_BYTES`<br>`REACTOR_RUST_HTTP_MAX_RESPONSE_BODY_BYTES`<br>`REACTOR_RUST_HTTP_MAX_INFLIGHT_RESPONSE_BYTES` | Dubbo, HTTP ve toplam in-flight limit birlikte açılır |
| Memory sınırlı yüksek concurrency | <small>Önce <code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT</code><br>sonra <code>REACTOR_DUBBO_MAX_INFLIGHT</code><br>en son <code>REACTOR_RUST_JNI_WORKERS</code></small> | Extra Java worker'dan önce connection reuse denenir |
| Rolling restart, K8s Service DNS | <small><code>SAMPLE_DUBBO_DISCOVERY=static</code><br>doğru readiness probe<br><code>REACTOR_DUBBO_NATIVE_CONNECTIONS_PER_ENDPOINT=2</code><br><code>REACTOR_DUBBO_TIMEOUT_MS=1000</code></small> | K8s sağlıksız pod'u endpoint listesinden çıkarır |
| Rolling restart, ZooKeeper | <small><code>SAMPLE_DUBBO_DISCOVERY=zookeeper</code><br><code>REACTOR_DUBBO_REGISTRY_CHECK=false</code><br><code>REACTOR_DUBBO_CHECK=false</code><br><code>REACTOR_DUBBO_TIMEOUT_MS=1000</code></small> | Discovery toparlanana kadar bounded failure döner |

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

### OpenJ9 21 jlink Minimum Image

Bu yolu sample'ı tam JRE image taşımadan, küçük ve non-root bir OpenJ9 container olarak çalıştırmak
istediğinizde kullanın. Eklenen `Dockerfile.jlink` dosyaları sadece consumer'ın ihtiyaç duyduğu Java
modülleriyle özel bir runtime üretir. Bu image/runtime yüzeyini küçültür; uygulama feature setini
tek başına değiştirmez. Feature set hâlâ Maven profile seçimiyle belirlenir.

İki build yolu var:

| Build yolu | Komut | Ne zaman kullanılır |
|------------|-------|---------------------|
| Workspace build | <small><code>docker build -f rest-sample-dubbo-consumer/docker/images/Dockerfile.jlink.workspace -t rest-sample-dubbo-consumer:jlink .</code></small> | `rust-spring-performance` root dizininden çalıştırılır. Local `rust-java-rest` ve `java-rust-dubbo` container içinde `mvn install` edilir; GitHub Packages token gerekmez. |
| Tek repo build | <small><code>docker build --secret id=maven_settings,src=$env:USERPROFILE\.m2\settings.xml -f docker/images/Dockerfile.jlink -t rest-sample-dubbo-consumer:jlink .</code></small> | Consumer sample tek başına clone edildiyse kullanılır. Maven settings içinde private GitHub Packages paketlerini indirebilen geçerli token olmalıdır. |

Tek Docker network üzerinde static provider smoke:

```powershell
docker network create reactor-jlink-smoke

docker run --rm --name rest-sample-dubbo-consumer `
  --network reactor-jlink-smoke `
  -p 8080:8080 `
  -e SAMPLE_DUBBO_DISCOVERY=static `
  -e REACTOR_DUBBO_PROVIDERS=rest-sample-dubbo-provider:20880 `
  -e REACTOR_RUNTIME_PROFILE=micro-dubbo `
  -e REACTOR_DUBBO_RUNTIME_PROFILE=micro-dubbo `
  rest-sample-dubbo-consumer:jlink
```

Kubernetes static Service DNS örneği:

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
  - name: JAVA_TOOL_OPTIONS
    value: "-Xms24m -Xmx96m -Xss192k -Xgc:threads=1 -XX:ActiveProcessorCount=1 -Xtune:virtualized"
```

Notlar:

- `JAVA_MODULES` içinden `java.desktop` modülünü çıkarmayın. Dubbo/Hessian typed DTO desteği Java Beans sınıflarını bu modülden kullanır.
- Image non-root `app` kullanıcısıyla çalışır ve Rust native `.so` extraction için `/app/.reactor/native` klasörünü yazılabilir bırakır.
- Bu workspace'teki lokal smoke testte static provider modunda consumer RSS yaklaşık `38.8 MiB` görüldü. Bunu kapasite garantisi değil, smoke referansı olarak düşünün; gerçek RSS endpoint'lere, Dubbo profile'ına ve trafiğe göre değişir.
- `zookeeper-discovery` moduna geçerseniz image'ı o Maven profile ile yeniden build edin; ZooKeeper class/thread maliyeti eklenecektir.

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
          image: registry.example.com/rest-sample-dubbo-consumer:0.1.1-zk
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
              path: /app/ready
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
- `/app/health` liveness için ucuz process health endpoint'idir. `/app/ready` gerçek Dubbo dependency kontrolü yapar; trafik provider hazır olana kadar beklesin istiyorsanız readiness probe olarak bunu kullanın.
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
    return catalogClient.nestedCatalogNativeJsonAsync()
            .thenApply(handle -> ResponseEntity.ok(RawResponse.nativeResponse(handle.nativeId())));
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

Bu akışta provider JSON body Java heap'e `byte[]` olarak alınmaz. Rust native tarafında response id
olarak tutulur ve HTTP response yine Rust tarafından yazılır. Java tarafında provider response'u
incelemeniz, dönüştürmeniz, validate etmeniz veya loglamanız gerekiyorsa `nestedCatalogJsonAsync()`
ve `RawResponse.json(bytes)` yolu hâlâ doğru seçimdir.

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

| Verb | Pattern | Kullanım | Overhead | Not |
|------|---------|---------|----------|-----|
| `GET` | No body + path/query<br>`RawResponse.json(bytes)` | Provider/cache hazır JSON | Çok düşük<br>parse/serialize yok | En ucuz read path |
| `GET` | No body + path/query<br>Java `record` | Küçük status/lookup | Düşük<br>sadece response serialize | High traffic'te p99 ölçün |
| `GET` | Query filtre<br>`List<record>` | Küçük sayfalı liste | Orta/yüksek<br>list + item allocation | Pagination ve limit şart |
| `POST` | `byte[]` -> `RawResponse.json(bytes)` | Command pass-through | Düşük<br>request DTO yok | Provider validation yapar |
| `POST` | `byte[]` -> `RawResponse.bytes(...)` | Binary upload/echo | Düşük<br>JSON yok | Büyük binary için stream/file |
| `POST` | `byte[]` -> Java `record` | Parse etmeden receipt/id | Düşük/orta | Response serialize edilir |
| `POST` | `record` -> `record` | Typed validation/business | Orta<br>parse + serialize | Hot path'te byte[] kadar ucuz değil |
| `POST` | `record` -> `List<record>` | Body filtreli search | Yüksek | Result limit zorunlu |
| `PUT` | `record` -> `record`/hata | Tam update/replace | Orta | Body limit + route admission |
| `PATCH` | `record` -> `record` | Typed partial update | Düşük/orta | Command concurrency düşük |
| `PATCH` | `byte[]` -> `RawResponse.json(bytes)` | Partial command pass-through | Düşük<br>parse yok | Validation provider'da |
| `DELETE` | No body -> `204` | Body gerekmeyen delete | En düşük | Audit için header/log/outbox |
| `DELETE` | Opsiyonel `byte[]` -> JSON/hata | Reason/requestId taşımak | Düşük | Audit JSON küçük kalmalı |

Notlar:

- Gerçek HTTP raw byte response için `ResponseEntity<byte[]>` yerine `RawResponse.bytes(...)` kullanın.
- Hazır JSON provider response'u Java'da incelemeyecekseniz `RawResponse.nativeResponse(handle.nativeId())` kullanın.
- Hazır JSON byte response'u Java'da incelemeniz veya dönüştürmeniz gerekiyorsa `RawResponse.json(bytes)` kullanın.
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

| Kullanıcı problemi | Başlangıç | Kritik ayar |
|--------------------|-----------|-------------|
| En küçük pod, seyrek trafik | `micro-dubbo` + static provider | <small><code>reactor.rust.jni.workers=1</code><br><code>reactor.dubbo.native-async-workers=1</code><br><code>reactor.rust.response-pool.small-capacity=8</code></small> |
| c256 altında daha çok write | `micro-dubbo` + route tuning | <small><code>reactor.rust.route-admission.post.api.v1.customers.max-concurrent</code><br>RSS/p99/503 ile ölçün</small> |
| Dynamic discovery lazım | `micro-dubbo` + `zookeeper-discovery` | `SAMPLE_DUBBO_DISCOVERY=zookeeper`<br>küçük RSS artışı normal |
| Provider DB yavaş | Consumer bounded kalsın | `sample.db.maximum-pool-size`<br>provider method limitleri<br>PostgreSQL latency |
| Best-practice şablon | Bu sample yapısı | `handler -> client -> interface -> provider -> repository` |

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

## Sözlük

| Terim | Anlamı |
|---|---|
| RSS | Kubernetes memory limitinin gördüğü process memory değeridir. |
| p99 | En yavaş yüzde 1 çağrının latency çizgisidir. p99 yüksekse tail latency kötüdür. |
| 503 | Kontrollü overload response kodudur. Limit dolunca pod'u korur. |
| Provider | Business method'u çalıştıran Dubbo server'dır. |
| Consumer | Bu REST servisidir. HTTP alır ve provider çağırır. |
| Static discovery | Consumer sabit adres veya Kubernetes Service DNS kullanır. |
| ZooKeeper discovery | Consumer provider adreslerini ZooKeeper'dan okur. |
| Route admission | Endpoint bazlı concurrency ve queue limitidir. |
| Bulkhead | Yoğun bir route veya RPC'nin diğer işleri bozmasını engelleyen limittir. |
| RawResponse | Hazır JSON byte verisini DTO kurmadan dönen response tipidir. |
| DTO graph | Request veya response için oluşturulan Java object yapısıdır. Heap ve GC maliyeti yaratır. |
| Native handle | Native taraftaki response referansıdır. JSON'u Java heap'e kopyalamayı azaltır. |
| Timeout | İsteğin başarısız sayılmadan önce bekleyebileceği üst süredir. |
| In-flight | Başlamış ama henüz bitmemiş iştir. |

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
