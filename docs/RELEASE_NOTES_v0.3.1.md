# rest-sample-dubbo-consumer v0.3.1

This sample aligns with `rust-java-rest:3.4.1` and `java-rust-dubbo:0.4.1`.

- Uses the ABI `24/7/6` native runtime line.
- Adds the native idle-connection TTL to the copy-paste configuration.
- Keeps the memory-first Dubbo profile bounded at two workers and two endpoint connections.
- Keeps route admission explicit so overload returns controlled `503` responses instead of growing
  an unbounded queue.
- Updates Docker/Jlink workspace builds and runtime configuration tests.
- Preserves all Java REST annotations, handlers, Dubbo interfaces, and response payloads.
