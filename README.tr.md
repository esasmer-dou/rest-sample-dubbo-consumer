# rest-sample-dubbo-consumer

[English](README.md) | [Türkçe](README.tr.md)

Dubbo provider'larını çağıran bir REST uygulamasıdır.

- HTTP isteklerini Rust Hyper karşılar.
- İş akışı Java handler'larında kalır.
- Hafif Dubbo client işlemlerini `java-rust-dubbo` yapar.
- Provider adresi static olarak veya ZooKeeper üzerinden bulunabilir.
- GET, POST, PATCH ve DELETE örnekleri vardır.

Kullanılan sürümler: `rust-java-rest:4.0.0`, `java-rust-dubbo:0.5.0`, `rest-sample-utility:0.3.0`, `rust-sample-model:0.3.0`.

## Buradan Başlayın

Herhangi bir property okumadan önce consumer tipini seçin.

| İhtiyaç | Seçim |
|---|---|
| Tek hazır JSON catalog çağrısı ve en az bağımlılık | Maven profile `native-static-consumer` |
| Catalog, müşteri okuma ve müşteri yazma işlemleri | Varsayılan profile `full-dubbo-consumer` |
| Provider adresleri ZooKeeper'dan gelecek | Maven profile `zookeeper-discovery` |

Birçok Kubernetes servisi static Service DNS ile başlayabilir. Tek bir Kubernetes Service provider replica'larını sunuyorsa ZooKeeper zorunlu değildir.

## Hızlı Başlangıç: Sabit Adresli Provider

Bu en basit lokal akıştır. ZooKeeper kullanılmaz.

### 1. Provider'ı başlatın

[`rest-sample-dubbo-provider`](https://github.com/esasmer-dou/rest-sample-dubbo-provider) projesindeki hızlı başlangıç adımlarını uygulayın.

Provider `127.0.0.1:20880` adresinde dinlemelidir.

### 2. Consumer'ı başlatın

Bu repo dizininde çalıştırın:

```powershell
$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"

mvn -q `
  "-Dserver.port=8080" `
  "-Dsample.dubbo.discovery=static" `
  "-Dreactor.dubbo.providers=127.0.0.1:20880" `
  "-Dreactor.runtime.profile=micro-dubbo" `
  "-Dsample.dubbo.capacity-profile=micro-2x2" `
  clean compile exec:java
```

### 3. API'yi çağırın

```powershell
curl.exe http://127.0.0.1:8080/app/health
curl.exe http://127.0.0.1:8080/app/ready
curl.exe http://127.0.0.1:8080/api/v1/catalog/nested
curl.exe http://127.0.0.1:8080/api/v1/catalog/items?limit=3
curl.exe http://127.0.0.1:8080/api/v1/customers/db/1
```

Müşteri oluşturun:

```powershell
curl.exe -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  --data '{"requestId":"req-1001","customerNo":"CUST-9001","fullName":"Ayşe Yılmaz","segment":"pilot","email":"ayse@example.com"}'
```

Müşteri durumunu değiştirin:

```powershell
curl.exe -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  --data '{"requestId":"req-1002","status":"active"}'
```

Müşteriyi silin:

```powershell
curl.exe -X DELETE http://127.0.0.1:8080/api/v1/customers/1 `
  -H "Content-Type: application/json" `
  --data '{"requestId":"req-1003","reason":"örnek temizlik"}'
```

Hazır Postman collection şu dizindedir:
[`artifacts/postman`](artifacts/postman/rest-sample-dubbo-consumer.postman_collection.json).

## Temel Endpoint'ler

| Endpoint | Amacı |
|---|---|
| `GET /app/health` | Uygulama çalışıyor mu? Dubbo çağrısı yapmaz |
| `GET /app/ready` | Gerekli provider'ları kontrol eder |
| `GET /api/v1/catalog/nested` | Hazır JSON catalog response'u |
| `GET /api/v1/catalog/info` | Typed catalog response'u |
| `GET /api/v1/catalog/items?limit=3` | Typed liste response'u |
| `GET /api/v1/customers/db/{id}` | DB kullanan provider'dan tek müşteri |
| `GET /api/v1/customers/db/by-segment?...` | Filtrelenmiş müşteri listesi |
| `POST /api/v1/customers` | Düşük maliyetli JSON command |
| `POST /api/v1/customers/typed` | Typed record command |
| `PATCH /api/v1/customers/{id}/segment` | Müşteri segmentini değiştirir |
| `PATCH /api/v1/customers/{id}/status` | Müşteri durumunu değiştirir |
| `DELETE /api/v1/customers/{id}` | Müşteriyi siler |

## Sabit Adres mi, ZooKeeper mı?

### Sabit adres

Kubernetes Service DNS tek bir sabit provider adresi veriyorsa bunu kullanın.

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=rest-sample-dubbo-provider:20880
```

Kubernetes, Service arkasındaki provider pod'larına TCP bağlantılarını dağıtır. Bu yapıda consumer'ın ZooKeeper kullanması gerekmez.

Dağıtım, TCP bağlantısı açılırken yapılır. Açılmış bir Dubbo bağlantısındaki istekler aynı provider pod'unu kullanmaya devam eder.

### ZooKeeper

Provider'lar dinamik kayıt oluyorsa, farklı registry'ler kullanılıyorsa veya Dubbo discovery davranışı gerekiyorsa ZooKeeper kullanın.

Build alın:

```powershell
mvn -q -Pzookeeper-discovery clean package
```

Şu ayarlarla çalıştırın:

```properties
sample.dubbo.discovery=zookeeper
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

ZooKeeper ek sınıf, thread ve memory kullanır. Yalnızca gerçek bir discovery ihtiyacı varsa açın.

## Çalışma Kapasitesini Seçin

| Trafik tipi | Ayar | Anlamı |
|---|---|---|
| Çok küçük servis | `sample.dubbo.capacity-profile=micro-1x1` | En az bağlantı ve worker; kapasite aşılırsa hızlı hata döner |
| Küçük production servisi | `sample.dubbo.capacity-profile=micro-2x2` | İki bağlantı ve iki native worker; sample varsayılanı |
| Ölçülmüş yüksek trafik | `reactor.runtime.profile=balanced-dubbo` | Daha fazla worker, kuyruk ve bağlantı; daha yüksek uygulama belleği |

`micro-2x2` ile başlayın. Provider ve database kapasitesi yük testiyle doğrulanırsa `balanced-dubbo` kullanın.

Gecikme sorununu bütün queue değerlerini artırarak çözmeyin. Büyük queue daha fazla memory kullanır ve en yavaş istekleri daha da geciktirebilir.

## JSON ve DTO Seçimi

| İhtiyaç | Seçim | Maliyeti |
|---|---|---|
| Provider JSON'unu HTTP'ye doğrudan iletmek | `byte[]` ve Rust response handle | Body yeniden Java'ya kopyalanmaz |
| Request alanlarını Java'da doğrulamak | Java `record` request | Açık sözleşme; normal parse maliyeti |
| Provider alanlarıyla iş kararı vermek | Typed `record` sonuç | Hessian decode ve Java nesnesi oluşturma maliyeti |
| Büyük listeyi incelemeden döndürmek | Hazır JSON byte'ları | Büyük Java object graph oluşturmaz |

İş mantığı için typed record kullanın. Ölçülmüş pass-through endpoint'lerde hazır JSON byte'larını kullanın.

## Konfigürasyon

Uygulama ayarları şu sırayla okur:

1. `src/main/resources/rust-spring.properties`
2. `reactor.config.file` veya `REACTOR_CONFIG_FILE` ile verilen dosyalar
3. JVM `-D...` değerleri ve desteklenen environment variable'lar

| Dosya | Amacı |
|---|---|
| `rust-spring.properties` | Küçük lokal varsayılanlar |
| `config/production.properties` | Production timeout, pool ve bağlantı limitleri |
| `config/advanced-tuning.properties` | Route bütçeleri ve düşük seviye memory ayarları |

Önemli başlangıç property'leri:

| Property | Amacı |
|---|---|
| `sample.dubbo.discovery` | `static` veya `zookeeper` seçer |
| `reactor.dubbo.providers` | Static provider adreslerini verir |
| `reactor.dubbo.timeout-ms` | En uzun RPC bekleme süresidir |
| `reactor.dubbo.max-inflight` | Toplam eş zamanlı RPC limitidir |
| `reactor.dubbo.native-connections-per-endpoint` | Her provider adresi için TCP bağlantı sayısıdır |
| `sample.command.customer-key-admission.max-concurrent-per-key` | Aynı müşteriye eş zamanlı update yapılmasını engeller |

## Kubernetes Örneği

Static Service DNS:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "static"
  - name: REACTOR_DUBBO_PROVIDERS
    value: "rest-sample-dubbo-provider:20880"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
  - name: SAMPLE_DUBBO_CAPACITY_PROFILE
    value: "micro-2x2"
```

ZooKeeper discovery:

```yaml
env:
  - name: SAMPLE_DUBBO_DISCOVERY
    value: "zookeeper"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-dubbo"
```

## Kod Haritası

| Dosya | Görevi |
|---|---|
| `RestSampleDubboConsumerApplication.java` | Full consumer'ı başlatır |
| `DubboConsumerModule.java` | Client ve handler'ları kurar |
| `CatalogHandler.java` | Catalog GET örneklerini içerir |
| `CustomerHandler.java` | GET, POST, PATCH ve DELETE örneklerini içerir |
| `*ClientDefinition.java` | Deklaratif Dubbo client sözleşmeleridir |
| `ConsumerRuntimePlans.java` | İsimlendirilmiş kapasite planlarını taşır |
| `rust-spring.properties` | Lokal ayarları taşır |

## Maven Package Erişimi

GitHub Packages için `read:packages` yetkili token gerekir. Token'ın private ortak sample repolarına da erişimi olmalıdır.

`~/.m2/settings.xml` içindeki server kimlikleri POM ile aynı olmalıdır:

```xml
<servers>
  <server>
    <id>github-rust-java-rest</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-java-rust-dubbo</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-rest-sample-utility</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-rust-sample-model</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

## Sık Karşılaşılan Sorunlar

| Belirti | Kontrol edin |
|---|---|
| Maven `401` dönüyor | Token, private repo erişimi ve dört server kimliği |
| `/app/health` `UP`, `/app/ready` `DOWN` | Provider adresi, provider process, registry ve network |
| Connection refused | Provider host, `20880` portu ve container/Kubernetes DNS |
| Typed DTO class bilinmiyor | Ortak model sürümü ve Hessian allowlist |
| İstekler kontrollü `503` dönüyor | Route veya RPC limiti pod'u koruyor; artırmadan önce provider ve DB kapasitesine bakın |
| Türkçe karakter bozuk | UTF-8 ve `application/json; charset=utf-8` kullanın |

## Ayrıntılı Bilgi

- [Türkçe kullanıcı rehberi](docs/USER_GUIDE.tr.md)
- [Türkçe PDF rehberi](docs/rest-sample-dubbo-consumer-user-guide.tr.pdf)
- [Docker image rehberi](docker/images/README.md)
- [Production ayarları](src/main/resources/config/production.properties)
- [Advanced tuning ayarları](src/main/resources/config/advanced-tuning.properties)
- [v0.4.0 release notları](docs/RELEASE_NOTES_v0.4.0.md)
