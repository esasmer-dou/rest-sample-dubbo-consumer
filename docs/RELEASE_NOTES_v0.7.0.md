# rest-sample-dubbo-consumer 0.7.0

Uses platform 4.7.0, cache/Dubbo libraries 0.8.0 and shared sample contracts 0.4.4 where applicable.
Java application responsibilities remain unchanged. Existing endpoint and provider contracts remain available.

See the [usage examples](functional-operations.md) for the opt-in freshness, error, transaction or command behavior relevant to this process. Keep features you do not need disabled. No larger heap, worker count, queue or timeout is required for this upgrade.

Use the matching packaged native runtime. Do not copy an older DLL/SO over it. Production data-source, discovery and memory limits still belong to your deployment configuration.

## Qualification Note

The coordinated Linux comparison had no request errors. Consumer DB c64 RPS decreased 1.814% and p99 increased 8.52%; mean consumer peak RSS increased 0.083 MiB. These deviations are accepted for stable publication. They meet the existing performance band but not strict zero-regression acceptance. They are not a new provider-only or failover qualification. See the [full comparison and writer idle-memory exception](https://github.com/esasmer-dou/rust-java-rest/blob/v4.7.0/docs/release-notes/v4.7.0.md#measured-scope-and-accepted-deviations).

[Quick start](../README.md) | [Türkçe](RELEASE_NOTES_v0.7.0.tr.md)
