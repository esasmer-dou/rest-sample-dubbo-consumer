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
