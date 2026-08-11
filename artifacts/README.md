# Consumer Helper Artifacts

[English](README.md) | [Türkçe](README.tr.md)

This directory contains files that can be imported into development tools. It does not contain
runtime dependencies.

## Postman Collection

File: `postman/rest-sample-dubbo-consumer.postman_collection.json`

1. Start `rest-sample-dubbo-provider`.
2. Start this consumer on port `8080`.
3. Import the collection into Postman.
4. Keep `baseUrl=http://localhost:8080`, or change it to your consumer address.
5. Run health and readiness requests before the business requests.

The collection calls the consumer REST API. It does not call the Dubbo provider port directly.

Expected result:

- `/app/health` proves that the HTTP process is running.
- `/app/ready` proves that the required Dubbo provider calls are available.
- Run business requests only after readiness returns `200`.

Do not store credentials, access tokens, or production host names in the collection. Use a local
Postman environment instead.

Return to the [consumer guide](../README.md) for discovery and runtime profile settings.
