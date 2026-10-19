# Explicit Error And Retry Contracts

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Development checkout. No published version changes in this work.

Existing error JSON remains the default. Opt in with:

```properties
sample.errors.common-contract=true
reactor.rust.errors.format=problem-details
```

Sync and async failures use the same mapper. Internal exception messages stay out of responses.
Dependency failures return 503. Validation still uses its existing status.
Do not enable a new error format without updating callers.

## Retry A Status Command

Enable the provider policy described in its guide. Then call:

```powershell
curl.exe -X PATCH "http://localhost:8080/api/v1/customers/1/status/typed?status=passive&requestId=demo-status-001"
```

Repeat exactly the same request after a lost response.
The provider returns the original result. Changed input with the same key returns HTTP 409.
Use a new key for a genuinely new command.
This sample does not retry writes automatically. Other command routes are not deduplicated.

HTTP 503 or a connection timeout does not prove the database rolled back.
The provider may have committed before the response was lost.
Replay the same `requestId` for this explicitly protected method.
Do not apply automatic retries to other, unprotected write methods.
Choose `reactor.dubbo.timeout-ms` together with provider connection-wait and SQL budgets.
Measure the first cold call too, not only a warmed application.

## Native Completion Cost

Dubbo async responses no longer create a new JVM thread attachment for each call.
They reuse the existing native worker's attachment. Temporary JNI references are cleared after each response.
Handlers, services and `CompletableFuture` usage stay the same.
You do not need more workers, a larger queue or a longer timeout for this change.
Route admission still applies. Load beyond provider capacity can still receive a controlled 503.
