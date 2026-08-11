# Consumer Yardımcı Dosyaları

[English](README.md) | [Türkçe](README.tr.md)

Bu dizinde geliştirme araçlarına import edilebilen dosyalar bulunur. Runtime dependency bulunmaz.

## Postman Collection

Dosya: `postman/rest-sample-dubbo-consumer.postman_collection.json`

1. `rest-sample-dubbo-provider` uygulamasını başlatın.
2. Consumer uygulamasını `8080` portunda başlatın.
3. Collection dosyasını Postman'e import edin.
4. `baseUrl=http://localhost:8080` değerini koruyun veya consumer adresinizle değiştirin.
5. Business request'lerden önce health ve readiness request'lerini çalıştırın.

Collection consumer REST API'yi çağırır. Dubbo provider portunu doğrudan çağırmaz.

Beklenen sonuç:

- `/app/health`, HTTP sürecinin çalıştığını gösterir.
- `/app/ready`, gerekli Dubbo provider çağrılarının kullanılabilir olduğunu gösterir.
- Business request'leri yalnız readiness çağrısı `200` döndükten sonra çalıştırın.

Collection içine parola, erişim anahtarı veya production sunucu adresi yazmayın. Bunları yerel bir
Postman environment dosyasında tutun.

Discovery ve runtime profile ayarları için [consumer rehberine](../README.tr.md) dönün.
