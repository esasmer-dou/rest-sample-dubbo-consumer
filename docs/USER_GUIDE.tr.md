# rest-sample-dubbo-consumer Kullanıcı Rehberi

Bu rehber ilk kullanım içindir.

Amaç kısa ve nettir: Dubbo provider'daki veriyi Rust-Java REST API olarak dışarı açmak.

## İçindekiler

1. [Bu Proje Ne İşe Yarar?](#bu-proje-ne-işe-yarar)
2. [Akış Nasıl Çalışır?](#akış-nasıl-çalışır)
3. [Ne Zaman Kullanılır?](#ne-zaman-kullanılır)
4. [Gerçek Hayat Senaryoları](#gerçek-hayat-senaryoları)
5. [Hızlı Başlangıç](#hızlı-başlangıç)
6. [Kopyala-Yapıştır API Örnekleri](#kopyala-yapıştır-api-örnekleri)
7. [Endpoint'ler](#endpointler)
8. [Profile Seçimi](#profile-seçimi)
9. [DB Pool Ve c64/c256 Ne Demek?](#db-pool-ve-c64c256-ne-demek)
10. [ZooKeeper Mi Static Service DNS Mi?](#zookeeper-mi-static-service-dns-mi)
11. [Sık Hatalar](#sık-hatalar)

## Bu Proje Ne İşe Yarar?

`rest-sample-dubbo-consumer`, REST API açan sample uygulamadır.

REST handler Java tarafındadır. HTTP I/O Rust tarafındadır. Dubbo data path de minimum overhead için Rust tarafına yaklaştırılmıştır.

Consumer DB'ye doğrudan bağlanmaz. DB işi provider tarafındadır.

## Akış Nasıl Çalışır?

```mermaid
flowchart LR
    A["Client"] --> B["Rust-Java REST Consumer"]
    B --> C["Java Handler"]
    C --> D["java-rust-dubbo"]
    D --> E["Dubbo Provider"]
    E --> F["PostgreSQL veya hazır JSON"]
    F --> E --> D --> C --> B --> A
```

Consumer tarafı ince kalmalıdır. Ağır DB ve mutation işleri provider sorumluluğudur.

## Ne Zaman Kullanılır?

| Senaryo | Bu proje uygun mu? | Neden |
|---------|--------------------|-------|
| REST API ile Dubbo provider dışarı açılacak | Evet | Ana kullanım budur. |
| Consumer DB'ye bağlanacak | Hayır | DB connection provider'da kalmalıdır. |
| En düşük memory isteniyor | Evet | `micro-dubbo` ve static provider DNS ile kullanın. |
| ZooKeeper zorunlu | Evet | `zookeeper-discovery` profile ile çalışır. |
| Provider hazır JSON dönüyor | Evet | En verimli kullanım budur. |

## Gerçek Hayat Senaryoları

| Senaryo | Akış | Önerilen seçim |
|---------|------|----------------|
| Katalog read API | Consumer provider'dan hazır JSON alır ve döner. | `native-static-consumer` + `micro-dubbo` |
| Müşteri listeleme | Consumer provider'a DB-backed read çağrısı yapar. | `micro-dubbo`, küçük route budget |
| Customer create | Request body provider command method'una gider. | `byte[]` command, idempotent `requestId` |
| Segment patch | Aynı customer için paralel update sınırlandırılır. | `customer-key-admission=1` |
| K8s ortamı | Provider adresi Service DNS ile bilinir. | Static provider DNS |
| ZooKeeper zorunlu kurum | Provider registry üzerinden bulunur. | `zookeeper-discovery` profile |

Bu uygulamada consumer ince kalmalıdır.

DB connection, transaction ve mutation kuralı provider tarafında kalır.

## Hızlı Başlangıç

Önce provider'ı başlatın.

Sonra consumer'ı çalıştırın:

```powershell
mvn -q package
java -jar target/rest-sample-dubbo-consumer-0.2.0.jar
```

Health:

```powershell
curl http://127.0.0.1:8080/app/health
```

DB read örneği:

```powershell
curl http://127.0.0.1:8080/api/v1/customers/db
```

Customer create örneği:

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1\",\"customerNo\":\"CUST-1001\",\"fullName\":\"Ayşe Demir\",\"segment\":\"standard\",\"email\":\"ayse@example.com\"}"
```

## Kopyala-Yapıştır API Örnekleri

Hazır catalog JSON oku:

```powershell
curl http://127.0.0.1:8080/api/v1/catalog/nested
```

DB-backed customer listesi oku:

```powershell
curl http://127.0.0.1:8080/api/v1/customers/db
```

Tek customer oku:

```powershell
curl http://127.0.0.1:8080/api/v1/customers/db/1
```

Segment bazlı customer ara:

```powershell
curl "http://127.0.0.1:8080/api/v1/customers/db/by-segment?segment=standard&limit=10"
```

Customer oluştur:

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-1001\",\"fullName\":\"Ayşe Demir\",\"segment\":\"standard\",\"email\":\"ayse.demir@example.com\"}"
```

Segment güncelle:

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
```

Status güncelle:

```powershell
curl -X PATCH "http://127.0.0.1:8080/api/v1/customers/1/status/typed?status=passive&requestId=req-1003"
```

Customer sil:

```powershell
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/1 `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

## Endpoint'ler

| Endpoint | Amaç | Maliyet |
|----------|------|---------|
| `GET /api/v1/catalog/nested` | Provider hazır JSON döner. | En ucuz Dubbo read path. |
| `GET /api/v1/customers/db` | Provider DB'den liste okur ve JSON döner. | DB ve provider kapasitesine bağlıdır. |
| `GET /api/v1/customers/db/{id}` | Tek customer okur. | Küçük read. |
| `POST /api/v1/customers` | Customer oluşturur. | Write path, idempotency gerekir. |
| `PATCH /api/v1/customers/{id}/segment` | Segment günceller. | Aynı customer için key admission vardır. |
| `DELETE /api/v1/customers/{id}` | Customer siler. | Gerçek sistemde soft delete tercih edilebilir. |

## Profile Seçimi

| Seçim | Ne zaman? | Ayarlar |
|-------|-----------|---------|
| `micro-dubbo` | Düşük RSS, kontrollü `503` kabul. | Küçük worker, queue ve connection. |
| `micro-1x1` reçetesi | En küçük pod. | `native-connections-per-endpoint=1`, `native-async-workers=1`. |
| `micro-2x2` reçetesi | Provider boşta, p99 yüksek. | Connection ve worker `2`. |
| `balanced-stable-4x4` | Daha çok başarılı read RPS. | Connection ve worker `4`, route budget kontrollü. |

DB-backed endpoint için consumer ayarını client concurrency ile değil, provider Hikari kapasitesiyle başlatın.

## DB Pool Ve c64/c256 Ne Demek?

`c64` veya `c256`, load testte aynı anda kaç client request geldiğini gösterir.

Bu değer Hikari connection sayısı değildir.

| Alan | Anlamı |
|------|--------|
| `c64` | Aynı anda 64 client request baskısı. |
| `reactor.dubbo.max-inflight` | Consumer'ın aynı anda kaç RPC çağrısını uçuşta tutacağı. |
| `route-admission.max-concurrent` | Bir endpoint'in aynı anda kaç işi kabul edeceği. |
| `sample.db.maximum-pool-size` | Provider tarafındaki gerçek DB connection üst limiti. |

Örnek:

```text
c64 trafik gelir.
Provider Hikari pool 2 ise aynı anda sadece 2 DB işi çalışır.
Diğer işler kısa süre bekler veya kontrollü 503 alır.
```

DB-backed endpoint için doğru başlangıç:

```properties
reactor.dubbo.max-inflight=8
reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=2
reactor.rust.route-admission.get.api.v1.customers.db.queue-timeout-ms=75
```

Queue'yu büyütmek `503` oranını azaltabilir. Fakat RSS ve p99 değerini artırır.

## ZooKeeper Mi Static Service DNS Mi?

| Seçim | Ne sağlar? | Ne zaman kullanılır? |
|-------|------------|----------------------|
| Static Service DNS | En düşük consumer RSS. ZooKeeper client yoktur. | K8s Service zaten provider pod'larını load balance ediyorsa. |
| ZooKeeper | Provider register/re-register bilgisini takip eder. | Kurum standardı ZooKeeper ise veya registry zorunluysa. |

Static örnek:

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=rest-sample-dubbo-provider:20880
```

ZooKeeper örnek:

```properties
sample.dubbo.discovery=zookeeper
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
```

## Sık Hatalar

| Belirti | Muhtemel neden | Çözüm |
|---------|----------------|-------|
| `503` artıyor | Route budget veya provider kapasitesi doldu. | Provider CPU, Hikari ve route metrics birlikte kontrol edilir. |
| p99 yükseliyor | Queue fazla büyüdü veya DB yavaşladı. | Queue timeout düşürün, provider DB wait ölçün. |
| Provider bulunamıyor | Static adres veya ZooKeeper config yanlış. | `reactor.dubbo.providers` veya registry adresini kontrol edin. |
| RSS yükseliyor | Full surface, typed DTO veya büyük queue kullanılıyor. | `native-static-consumer`, `micro-dubbo` ve daha küçük queue deneyin. |
