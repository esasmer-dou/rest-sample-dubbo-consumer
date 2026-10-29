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
örneklerini açar. Yeni modüller aynı kaynak setinden build edilmelidir. Henüz
yeni Maven paketi olarak yayımlanmadılar.

## Build Ve Çalıştırma

Önce ortak model, utility, runtime ve isteğe bağlı REST adapter modülünü yerel
depoya kurun. Adapter rehberi şu dizindedir:
`java-rust-dubbo-spring-boot/java-rust-dubbo-rest-adapter`.

```sh
mvn -B -ntp -Pdirect-json-consumer clean package
mvn -B -ntp -Pdirect-json-consumer dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=lib
java -Drust.lib.path=/opt/native/librust_hyper.so \
  -Dreactor.dubbo.native.path=/opt/native/librust_dubbo.so \
  -Dsample.direct-json.providers=provider:20880 \
  -Dreactor.runtime.profile=micro-rest \
  -Dreactor.dubbo.direct-json.pool-slots=2 \
  -cp 'target-direct-json/rest-sample-dubbo-consumer-0.8.0.jar:lib/*' \
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

Yetki ve iş kararları yine handler'dadır. Java her satırı inceleyecek,
doğrulayacak veya değiştirecekse normal DTO yolunu seçin. Doğrudan dönüşüm,
consumer record kurucularını çalıştırmaz; kuruculardaki doğrulama ve normalleştirme
kuralları uygulanmaz. Bu kurallar gerekiyorsa normal DTO yolunu kullanın.
Doğrudan dönüşüm provider yanıtını da cache etmez; her `/rows` çağrısı provider'a gider.

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

Bu eklemeler yalnız kaynak kodda bulunur; henüz yayımlanmamıştır. EmaTech'te dört
yeni süreç çiftiyle, 1 CPU/128 MiB altında altı kontrol/literal hücresi de
RPS/p99/+3 MiB sınırlarını geçti. Literal RPS değişimi -%0,48..+%1,59 oldu;
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
