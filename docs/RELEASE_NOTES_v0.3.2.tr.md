# rest-sample-dubbo-consumer v0.3.2

[English](RELEASE_NOTES_v0.3.2.md) | [Türkçe](RELEASE_NOTES_v0.3.2.tr.md)

Bu patch sürümü `rust-java-rest:3.4.1`, `java-rust-dubbo:0.4.1`,
`rest-sample-utility:0.2.0` ve `rust-sample-model:0.2.0` ile uyumludur.

## Neler Değişti?

- `micro-2x2` profili, raw ve typed müşteri oluşturma endpoint'lerine ayrı ayrı `4` istek sınırı
  ve `250 ms` kuyruk bekleme süresi verir.
- İki endpoint aynı iki native worker'ı, provider iznini, Hikari bağlantısını ve PostgreSQL commit
  yolunu kullanır. Yeni sınırlar ortak backend önünde gizli bir 16 istek kuyruğu oluşmasını engeller.
- Yeni c16 write gate, tek bir iyi sonucu yeterli kabul etmez. En yüksek p99 değerini, koşular
  arasındaki p99 farkını ve non-2xx oranını birlikte kontrol eder.
- İngilizce ve Türkçe konfigürasyon rehberleri, aynı provider ve database pool'u kullanan endpoint
  limitlerinin neden birlikte hesaplanması gerektiğini açıklar.

## Ölçülen Sonuç

Aynı Docker Desktop `micro-2x2` kaynak sınırlarında son üç c16 koşusu şu sonuçları verdi:

| Metrik | Önce | v0.3.2 |
|--------|------:|-------:|
| Ortalama RPS | `340,93` | `501,74` |
| Ortalama p99 | `234,45 ms` | `132,91 ms` |
| En yüksek p99 | `485,22 ms` | `143,83 ms` |
| p99 yayılımı | `329,92 ms` | `25,36 ms` |
| Non-2xx oranı | `%0,020` | `%0,014` |

Bu değerler sample test ortamına aittir. Evrensel production garantisi değildir. Production profilini
değiştirmeden önce gate'i kendi provider, PostgreSQL disk, CPU ve pod memory sınırlarınızla çalıştırın.

## Uyumluluk

Java REST annotation'ları, handler imzaları, Dubbo service sözleşmeleri, JSON payload'ları ve native
ABI gereksinimleri değişmedi. Mevcut uygulama kodunu değiştirmeniz gerekmez.
