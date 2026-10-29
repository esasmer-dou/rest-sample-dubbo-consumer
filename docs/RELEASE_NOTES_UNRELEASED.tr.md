# UNRELEASED (Yayımlanmadı)

Bu checkout değişiklikleri yayımlanmış `0.8.0` sonrasına aittir; o yayının parçası değildir.
Yeni sürüm numarası, etiket veya native ABI tanımlanmaz.

## Readiness Ve Client Seçimi

- `sample.consumer.readiness.contracts=auto|full|catalog-only|query-only` zorunlu kontrolleri başlangıçta bir kez seçer. Varsayılan `auto`, mevcut full/catalog-only surface davranışını korur.
- Zorunlu client yoksa veya RPC başarısızsa `503` döner. Seçilmeyen kontroller provider çağrısı yapmadan `required=false` ve `SKIPPED` bildirir. Liveness yerel kalır.
- `sample.consumer.catalog.enabled=false`, generated katalog client ve rotalarını başlangıçta devre dışı bırakır. Reflection-free AOT factory derlemesi için generated client constructor tipleri tam paket adlarıyla yazılır.
- Bağımsız `config/query-only.properties` dosyası, normal full consumer ile `native-db-query-provider` eşleşmesini üretim kaynak sınırlarını koruyarak sağlar. Seçim saklanır; istekler ayarları yeniden ayrıştırmaz veya RPC cevabını bloklayarak beklemez.

## Yeniden Derleme Gerekli

Önceden indirilmiş `0.8.0` consumer jar bu ayarları ve yeni ayar dosyasını desteklemez. Oluşan jar dosya adı yine `0.8.0` içerse de bu checkout'u yeniden derleyin. Bkz. [çalıştırma tarifi](../README.tr.md#hazır-olma-sözleşmesi).

Query ve command rotaları aynı handler'ı paylaştığı için query-only dosyası `surface=full` kullanır. Command client ve rotaları API uyumu için kalır ancak query-only provider'da desteklenmez. Bu seçim salt-okunur erişim kontrolü değildir.

## Doğrulama

Regresyon kapsamı full/catalog/query-only readiness, zorunlu bağımlılık hataları, eşleşen ayar dosyaları ve tam paket adlı optional client aramaları dahil generated başlangıç factory'lerini içerir. EmaTech üzerinde AOT `test-compile` ve JUnit çalıştırması hâlâ gereklidir; bu not başarılı build veya runtime yeterlilik iddiası taşımaz.
