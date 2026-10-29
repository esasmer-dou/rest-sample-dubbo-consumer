# Açık Hata Ve Tekrar Sözleşmesi

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Bu isteğe bağlı örnekler, bu `0.8.0` kaynak kodunda vardır. Mevcut varsayılanlar
değişmez; [sürüm notlarındaki](RELEASE_NOTES_v0.8.0.md) eşleşen bağımlılıkları kullanın.
Bu kaynak rehberi yayımlanmış paketin bulunabilirliğini doğrulamaz.

Mevcut hata JSON'u varsayılan olarak kalır. Yeni biçimi şöyle açın:

```properties
sample.errors.common-contract=true
reactor.rust.errors.format=problem-details
```

Senkron ve asenkron hatalar aynı eşlemeyi kullanır.
İç exception mesajları kullanıcıya gönderilmez.
Bağımlılık hataları 503 döner.
Yeni hata biçimini açmadan önce çağıran uygulamaları güncelleyin.

## Durum Güncellemesini Tekrarlayın

Önce provider rehberindeki güvenli tekrar ayarını açın:

```powershell
curl.exe -X PATCH "http://localhost:8080/api/v1/customers/1/status/typed?status=passive&requestId=demo-status-001"
```

Yanıt kaybolursa aynı isteği tekrar gönderin.
Provider kayıtlı sonucu döner.
Aynı anahtarla farklı veri gönderirseniz HTTP 409 alırsınız.
Gerçekten yeni bir işlem için yeni anahtar kullanın.
Sample yazma işlemini otomatik tekrarlamaz.
Diğer yazma endpoint'lerinde bu koruma açık değildir.

HTTP 503 veya bağlantı timeout'u, DB işleminin geri alındığını kanıtlamaz.
Provider commit etmiş, yanıt size ulaşmamış olabilir.
Koruma açık olan bu metotta aynı `requestId` ile tekrar edin.
Koruması olmayan başka bir yazma metodunu bu örneğe bakarak otomatik tekrarlamayın.
`reactor.dubbo.timeout-ms` süresini provider'ın DB bekleme ve SQL süreleriyle birlikte belirleyin.
İlk soğuk çağrıyı da ölçün; yalnız ısınmış uygulamanın sürelerine bakmayın.

`sample.command.customer-key-admission.max-concurrent-per-key`, yalnız bir consumer
JVM'ini sınırlar. Replikaların admission durumu bağımsızdır. Provider metot limitleri
de süreç yerelidir; farklı metotları koordine etmez. Çakışan DB write'larını
PostgreSQL kilitleri ve transaction'lar korur. Yalnız isteğe bağlı typed-status
politikası, ortak DB kullanan provider replikaları arasında işlem/müşteri anahtar
kapsamı ve saklama süresi içinde tekrarları ayıklar. Yerel admission bunun yerine geçmez.

## Native Cevapların Maliyeti

Dubbo async cevabı artık her çağrıda yeni bir JVM thread bağlantısı oluşturmaz.
Mevcut native worker'ın bağlantısını kullanır. Geçici JNI referansları her cevapta temizlenir.
Handler, service ve `CompletableFuture` kullanımı değişmez.
Bu iyileştirmeyi kullanmak için worker, queue veya timeout artırmanız gerekmez.
Route admission sınırı hâlâ geçerlidir. Provider kapasitesini aşan yükte kontrollü 503 alınabilir.
