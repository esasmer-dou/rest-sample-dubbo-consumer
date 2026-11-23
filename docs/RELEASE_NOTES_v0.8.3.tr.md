# rest-sample-dubbo-consumer 0.8.3

[English](RELEASE_NOTES_v0.8.3.md) | [Turkish](RELEASE_NOTES_v0.8.3.tr.md)

Varsayılan consumer REST `4.9.0` ve temel Dubbo `0.9.2` kullanır. İsteğe bağlı `direct-json-consumer` profili Spring başlatmadan Spring Dubbo adapter, annotation ve kod üreticisi `0.8.3` kullanır.

Consumer'ı `-Pdirect-json-consumer` ile derleyin. Provider `0.8.3` için `stream-catalog-provider` profilini açın ve `/api/v1/catalog/rows?count=32` adresini çağırın. [Profil rehberine](DIRECT_JSON.tr.md) bakın.

Geçiş için [README](../README.tr.md). JNI sözleşmesi ve varsayılan çalışma ayarları değişmedi. Bu yayın tek başına yeni bir performans kazanımı iddia etmez.
