# 0.8.1

[English](RELEASE_NOTES_v0.8.1.md) | [Türkçe](RELEASE_NOTES_v0.8.1.tr.md)

Yayım tarihi: 30 Ekim 2026.

## Neler Değişti?

REST/platform `4.8.1`, sade Dubbo `0.9.1`, ortak model `0.5.1` ve `direct-json-consumer` Spring Dubbo bağımlılıkları/processor `0.8.1` ile hizalanır. Seçilen sözleşmeye göre readiness/toparlanma kapsamını ve query-only ayar dosyasını içerir.

## Sürüm Eşleşmesi Ve Yükseltme

REST/platform `4.8.1`; cache `0.9.1`; sade Dubbo `0.9.1`; Spring Dubbo `0.8.1`;
model/utility `0.5.1`; dört uygulama sample'ı `0.8.1`. Agent `0.7.1` değişmez.
Producer ve consumer bağımlılıklarını birlikte hizalayıp yeniden derleyin.
Eski DLL/SO dosyalarını yeni paketlere kopyalamayın. Önceki sürüm notları tarihsel kayıttır.
Zorunlu readiness hatası `503` döndürür; seçilmeyen kontrol `SKIPPED` kalır. Query-only seçimi erişim kontrolü değildir. Mevcut varsayılanlar değişmez.

## Kabul Sınırı

ARM donanım doğrulaması, yeni performans garantisi veya F18 kapanışı iddia edilmez.
Doğrulama kanıtı ilgili değişiklik/yayım kaydıyla tutulur.
