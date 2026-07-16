# rest-sample-dubbo-consumer v0.3.0

This sample aligns with `rust-java-rest:3.4.0` and `java-rust-dubbo:0.4.0`.

- Keeps Java handlers and REST annotations unchanged.
- Uses the ABI `24/6/6` native runtime line.
- Documents and configures the bounded native Dubbo thread stack.
- Keeps `blocking` and `tokio-demux` transport resource planes mutually exclusive.
- Updates native-static health diagnostics to report the selected transport and pool counts.
- Refreshes the Jlink workspace images with the current low-memory OpenJ9 options.
