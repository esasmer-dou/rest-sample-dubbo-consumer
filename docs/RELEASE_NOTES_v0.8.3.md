# rest-sample-dubbo-consumer 0.8.3

[English](RELEASE_NOTES_v0.8.3.md) | [Turkish](RELEASE_NOTES_v0.8.3.tr.md)

The default consumer uses REST `4.9.0` and core Dubbo `0.9.2`. The optional `direct-json-consumer` profile uses Spring Dubbo adapter, annotations and code generator `0.8.3` without starting Spring.

Build `-Pdirect-json-consumer` and call `/api/v1/catalog/rows?count=32` against provider `0.8.3` running the `stream-catalog-provider` profile. Use the [profile guide](DIRECT_JSON.md).

For migration and setup, see [README](../README.md). JNI contracts and runtime defaults are unchanged. This update does not claim a new performance gain.
