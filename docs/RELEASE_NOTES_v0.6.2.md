# rest-sample-dubbo-consumer 0.6.2

`0.6.2` aligns the runnable REST-to-Dubbo consumer with `rust-java-rest:4.4.0`,
`java-rust-dubbo:0.7.2`, `rest-sample-utility:0.4.1`, and `rust-sample-model:0.4.1`.

- REST endpoints, Dubbo contracts, static/ZooKeeper discovery, and Java business logic are unchanged.
- The shared runtime uses REST ABI `28`, Dubbo ABI `7`, and Glowroot ABI `1`.
- Bounded HTTP and native Dubbo telemetry is available but remains disabled by default.

