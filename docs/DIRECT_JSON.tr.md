# Provider Listesini Consumer DTO'su Oluşturmadan İletme

[English](DIRECT_JSON.md) | [Turkish](DIRECT_JSON.tr.md)

İsteğe bağlı `direct-json-consumer` kaynak profili şu akışı gösterir:

```text
GET /api/v1/catalog/rows?count=32
  -> Java handler -> üretilmiş tipli Dubbo client
  -> Rust TCP -> provider'daki Java servis
  -> native Hessian yanıtı -> üretilmiş JSON yazıcı -> Rust HTTP yanıtı
```

Consumer içinde Spring, Netty veya HessianLite yüklenmez. Mevcut profiller ve
müşteri/command endpoint'leri değişmez. Bu ayrı profil yalnız aşağıdaki katalog
örneklerini açar. Bu örnekler, bu `0.8.2` kaynak kodunda vardır. Eşleşen modülleri
ve native dosyaları kullanın; aşağıdaki yayımlanmış paket kontrolü, test edilen
Linux yapılandırmasının işlevsel kanıtını kaydeder.

## Build Ve Çalıştırma

Birbiriyle eşleşen Spring Dubbo `0.8.2` adapter, annotations, codegen, runtime ve
native dosyalarını kullanın. Profil adapter, annotations ve codegen sürümlerini
sabitler; adapter geçişli olarak runtime `0.8.2` getirir. Public Spring Dubbo
dağıtımındaki isteğe bağlı REST adapter belgesini kullanın. Bu profil güncellemesi
ortak model, utility ve REST/platform sürümlerini değiştirmez; utility için yeni
sürüm zinciri gerekmez.

JNI birincil köprü olarak kalır (K-001); Spring Dubbo ABI `3` değişmez. Eski bir
ABI-3 native dosyasında ek export'lar bulunmayabilir. Yalnız ABI numarası aynı diye
başka dosya seçmeyin; platformla eşleşen `0.8.2` native dosyasını kullanın.
Linux x64 doğruluk kabulü EmaTech üzerinde geçti. Windows x64 ve macOS ARM64 yalnız
derlendi; bu sürüm için runtime doğrulaması yapılmadı. Aşağıdaki sample kontrolü
işlevsel kanıttır; yeni bir performans kabulü değildir.

Native istek kodlaması, direct JSON yanıt dönüşümünden ayrıdır.
`reactor.dubbo.nativeArguments`, isteğe bağlı annotation-processor seçeneğidir;
varsayılan `false` kalır. Bu profil seçeneği vermez; üretilmiş Java istek kodlaması
korunur. Açmak için profilin compiler argümanlarına
`-Areactor.dubbo.nativeArguments=true` verip eşleşen dosyalarla temizden derleyin.
JVM `-D` runtime ayarı değildir. Kapalı olması tek başına ölçülen P05
yapılandırmasını yeniden oluşturmaz.

```sh
mvn -B -ntp -Pdirect-json-consumer clean package
mvn -B -ntp -Pdirect-json-consumer dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=lib
java -Drust.lib.path=/opt/native/librust_hyper.so \
  -Dreactor.dubbo.native.path=/opt/native/librust_dubbo.so \
  -Dsample.direct-json.providers=provider:20880 \
  -Dreactor.runtime.profile=micro-rest \
  -Dreactor.dubbo.direct-json.pool-slots=2 \
  -cp 'target-direct-json/rest-sample-dubbo-consumer-0.8.2.jar:lib/*' \
  com.reactor.sample.dubbo.consumer.directjson.DirectJsonConsumerApplication
```

`lib`, runtime bağımlılıklarını içermelidir. Annotation processor'ları eklemeyin.
Windows classpath ayıracı `;` olur. SO yerine eşleşen DLL'leri seçin.
Dubbo native kütüphanesi ile REST native kütüphanesi ayrıdır. Aynı ABI numarası
tek başına bütün ek yetenekleri kanıtlamaz. Eşleşen kaynak dağıtımını kullanın.

```sh
curl 'http://localhost:8080/api/v1/catalog/rows?count=32'
curl 'http://localhost:8080/api/v1/catalog/rows?count=512'
curl 'http://localhost:8080/api/v1/catalog/literal'
curl 'http://localhost:8080/api/v1/catalog/health'
```

Satırlarda nested record, Türkçe metin, emoji, nullable alan ve sonlu kayan
noktalı sayılar bulunur. Payload/response byte sınırı, satır sayısı sınırına
ulaşmadan önce dolabilir. Böyle bir durumda hata döner; veri kesilmez.

### Yayımlanmış Paketle İşlevsel Kontrol

7 Kasım 2026'da Linux x64 EmaTech smoke kontrolü, yeniden indirilen public `0.8.2`
paketleri ve eşleşen yeni native kütüphaneyle geçti. Varsayılan Java istek kodlaması
ve isteğe bağlı `native-arguments` kolları PASS verdi; `run.exit` değeri `0` oldu.
Gerçek sample direct-JSON sınıfları ve üretilmiş indeksler kullanıldı. 16 ve 100
satırlık yanıtların uzunlukları ve SHA-256 hash'leri provider oracle'larıyla
eşleşti; row ve detail kurucu sayaçları sıfır kaldı. Üretilmiş `@PreDestroy`
temizliği client'ı kapattı; kapatma sonrasında native client, bağlantı, yanıt handle
ve byte sayaçları sıfırlandı.

Sample paketine dahil olmayan workspace kanıtı:
`.tmp/release-20261107/execution-release-sample-20261107/sample-evidence/`
(`results.txt`, `run.exit` ve kollara ait oracle, tanılama ve kapatma kayıtları).
Bu işlevsel kontrol yeni benchmark iddiası eklemez, P05 kabulünü genişletmez ve
aşağıda açıklanan küçük Java HTTP riskini kapatmaz.

### `count` Nasıl Bağlanır?

Handler, mevcut legacy `@DirectQueryInt` bağlayıcısını kullanır: varsayılan `32`,
aralık `0..10000` değeridir. Girdi normalleştirilir; bu, katı bir HTTP 400 doğrulayıcısı değildir.

| Query değeri | Handler'a aktarılan değer |
| --- | --- |
| Eksik, boş veya `abc` | `32` |
| `-1` | `0` |
| `10001` | `10000` |
| `12oops` | `32` |
| `10001oops` | `10000`: legacy ayrıştırıcı, son eki okumadan sınıra ulaştığında döner. |

Route kaydında ters verilmiş sınırlar sıralanır; ayarlanan varsayılan değer de bu
aralığa çekilir. Bozuk veya aralık dışı girdilerin reddedilmesi gerekiyorsa yalnız
bu anotasyona güvenmeyin; açık doğrulama yapan bir parametre sözleşmesi kullanın.

## Hangi Ayar Ne İşe Yarar?

| Property | Varsayılan | Ne İşe Yarar? |
| --- | --- | --- |
| `sample.direct-json.providers` | `127.0.0.1:20880` | Static provider adresidir. Kubernetes'te Service DNS yazın. |
| `sample.direct-json.connections` | `2` | Bu endpoint'e açılacak kalıcı Dubbo bağlantısı sınırıdır. |
| `sample.direct-json.timeout-ms` | `3000` | RPC zaman sınırıdır. Süre dolunca iş çağrısı tekrarlanmaz. |
| `sample.direct-json.enabled` | `true` | Doğrudan dönüşümü seçer. `false`, normal DTO listesini oluşturan karşılaştırma yoludur. |
| `reactor.dubbo.direct-json.pool-slots` | runtime varsayılanı `16` | Aynı anda çalışan JSON yazıcılarını sınırlar. İki JNI worker için `2` ile başlayın. |
| `reactor.json.swar` | build sırasında `false` | `/literal` için sabit alan adlarını word olarak yazan kodu üretir. `-Dreactor.json.swar=true` ile temiz build alın. |

`sample.direct-json.timeout-ms`, açıkça oluşturulan client'a verilir; tek bir
anotasyonlu metoda değil, bu client'ın çağrılarına uygulanır. Handler'ın `read()`
yolu senkrondur. Doğrudan dönüşüm satır DTO'larını kaldırır; RPC'yi async yapmaz,
`tokio-demux` seçmez. REST async cevap bütçesi bu seçenekten ayrıdır.

Yetki ve iş kararları yine handler'dadır. Java her satırı inceleyecek,
doğrulayacak veya değiştirecekse normal DTO yolunu seçin. Doğrudan dönüşüm,
consumer record kurucularını çalıştırmaz; kuruculardaki doğrulama ve normalleştirme
kuralları uygulanmaz. Bu kurallar gerekiyorsa normal DTO yolunu kullanın.
Doğrudan dönüşüm provider yanıtını da cache etmez; her `/rows` çağrısı provider'a gider.

## Tek Geçişli Sahiplik

`DubboJsonResponses.body(rows)`, direct sonucun sahipliğini tek geçişli bir
`ManagedJsonBodyProducer` nesnesine aktarır. REST'e döndürünce temizleme sorumluluğu
da aktarılır. Sonuç üzerinde dolaşmayın; body'yi iki kez göndermeyin veya başka
istek için saklamayın. Hiç gönderilmeyen body'yi kapatın. REST, teslim aldığı managed
body'yi tamamlanma veya iptalde kapatır. Teslimden önce iptalde kapatma çağıranın
sorumluluğudur. Sonuç body'ye sarılmadan bırakılıyorsa sonucu kendiniz kapatın.

Tek kapasite tekrarı, bellekte tutulan aynı yanıtı kullanır; ikinci RPC değildir.
Başarılı yazım veya kapatma sonrası body tekrar kullanılamaz. Java iş mantığı ve
tanımlı istek guard'ları korunur; native iş mantığı bypass eklenmez.

## Bellek Ve Biçimlendirme Sınırları

Satır DTO'larının oluşturulmaması, yanıt verisinin bellekte tutulmadığı anlamına
gelmez. Boyutu sınırlandırılmış tek bir tam Hessian yanıtı, dönüşüm boyunca native
bellekte kalır. Adapter, JSON'u REST'in sınırlı yanıt buffer'ına yazar ve kapasite
yetersizliğinde yeniden yazma protokolünü destekler. Bu yol sınırsız bir HTTP chunk
akışı veya ağdan satır satır okuyan bir cursor değildir. Payload, JSON çıktısı ve
toplam in-flight bellek sınırlarını koruyun; büyük export için ayrı file/stream
tasarımı kullanın.

Eşleşen native yanıt tablosu toplam 256 handle ve 32 MiB ile sınırlıdır. Bu,
peşinen ayrılmış bir slab veya toplam RSS sınırı değildir. Kapasite dolarsa RPC
tekrarlanmaz; sonuç hata verir. Yeni doğrudan yol ABI `3` yanında native yanıt
politikası `1` ister. Eşleşen SO ve JAR dosyalarını birlikte kullanın.

Bazı yaygın sonlu double değerleri primitive işlemlerle biçimlendirilir. Diğer
sonlu değerlerde JDK'nın `Double.toString` biçimlendiricisine dönülebilir ve nesne
oluşturulabilir. Satır DTO grafının kaldırılması, her değer ve yanıt için sıfır
tahsisat garantisi değildir.

EmaTech'teki odak kontrolünde 1 CPU/128 MiB altında dört yeni süreç çifti
kullanıldı. Kontrol/32 satır/512 satır için dokuz RPS/p99/bellek hücresi de geçti.
512 satırda doğrudan dönüşüm, aynı satırlardan DTO listesi oluşturmaya göre
consumer RSS'ini yaklaşık 7,5–8,5 MiB azalttı; başarılı RPS'i yaklaşık %26–30
artırdı. Her çağrı yine provider'a gitti. Bunlar ölçülen örnek sonuçlarıdır;
her şema ve ortam için garanti değildir. Daha büyük export için ayrı streaming
tasarımı gerekir.

### P05 Kabul Kapsamı

Yukarıdaki geçmiş odak sample sonucu, P05 tam HTTP kabulü değildir. P05 yalnız
ölçülen Dubbo iş yükleri için kabul edilmiştir. Tam HTTP matrisi **13/15; PASS
değildir**. **R-P05-HTTP-001**, küçük Java HTTP kontrol rotasında RPS/p99
gerilemeleri için açıktır; ürün kök nedeni kanıtlanmamıştır. Tüm plan,
varsayılanlar, endpoint'ler veya platformlar için performans iddiası yoktur.
Bu bağımlılık güncellemesi açık riski kapatmaz.

## İsteğe Bağlı SWAR Sabit Yazımı

`/api/v1/catalog/literal`, işaretlenmiş `SwarPayload` record'unu döndürür; `/rows`
yolundaki Hessian dönüşümünü yapmaz. SWAR varsayılan olarak kapalıdır. Açmak için
bu profili `mvn -B -ntp -Pdirect-json-consumer -Dreactor.json.swar=true clean package`
ile temizden derleyin. Byte tablosu yoluna dönmek için `-Dreactor.json.swar=false`
ile yine temizden derleyin. Uygulama başlarken verilen JVM özelliği önceden üretilmiş writer'ı değiştirmez.

Açık kolda 8..16 byte'lık UTF-8 alan önekleri için tek
`writePrebakedLiteral(first, last, length)` çağrısı üretilir; kalan byte tutulmaz.
Daha uzun öneklerde tam word ve 0..7 byte'lık kalan parça kullanılır. Daha kısa
öneklerde byte tablosu korunur. `JsonBufferWriter`, tam word'leri mutlak
`ByteBuffer.putLong(index, word)` ile yazar; big-endian buffer'da
`Long.reverseBytes` ile word'ün byte sırasını ters çevirir. VarHandle ByteBuffer view
kullanmaz; position, limit ve byte order değerlerini korur. Küçük çıktı buffer'ında
sınırlı yeniden yazma için tam gerekli uzunluk yine negatif döner.

Kaydedilmiş EmaTech karşılaştırmasında dört yeni süreç çiftiyle, 1 CPU/128 MiB altında
altı kontrol/literal hücresi de RPS/p99/+3 MiB sınırlarını geçti. Bu geçmiş sonuçlar
güncel kurulumu veya platform paketlemesini doğrulamaz. Literal RPS değişimi -%0,48..+%1,59 oldu;
kararlı RSS 0,54..1,38 MiB arttı. Bu sonuç bellek azalması veya her endpoint'te
SWAR kullanma önerisi değildir.

`/api/v1/catalog/diagnostics`, kontrollü tanılama içindir. Heap/native sayaçlarını
gösterir. Servisi dışarı açmadan önce bu endpoint'i kısıtlayın. Kurucu sayaçları
normalde kapalıdır. Yalnız işlevsel kontrol için
`-Dsample.stream-catalog.count-constructors=true` verin. RPS ölçümünde kullanmayın.

Sahiplik, buffer tekrarı ve boyut sınırları için adapter rehberini okuyun.

## Hangi Yanıt Yolunu Seçmeliyim?

Üç `reactor.dubbo.direct-json.*` yazıcı ayarı, JVM `-D` parametresi veya büyük
harfli ortam değişkeniyle verilir. `rust-spring.properties` dosyasından otomatik
okunmaz. Yukarıdaki çalıştırma örneğini veya adapter rehberini kullanın.

| İhtiyacınız | Seçim | Consumer'daki İş |
| --- | --- | --- |
| Provider zaten JSON byte döndürüyor | Mevcut native response handle | DTO çözümleme ve JSON'u yeniden yazma yapılmaz. |
| Tipli satırlar değişmeden iletilecek | Doğrudan JSON dönüşümü | Hessian okunur, JSON yazılır. Satır DTO grafı kurulmaz. |
| Kurucu doğrulaması, alan değişimi, sıralama veya birleştirme gerekiyor | Normal tipli DTO/liste | Değerler Java'da açıkça oluşturulur ve işlenir. |
| Çok büyük dosya veya export hazırlanıyor | Ayrı file/stream tasarımı | Buffer sınırını toplam in-flight bellek bütçesi olmadan büyütmeyin. |
