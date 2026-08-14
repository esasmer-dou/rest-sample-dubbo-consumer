# rest-sample-dubbo-consumer 0.6.4

This patch moves the runnable consumer to the `rust-java-rest:4.5.0` platform while retaining
`java-rust-dubbo:0.7.2`. HTTP now uses the clean REST ABI `29` and Glowroot ABI `3` runtime.

REST routes, Dubbo contracts, discovery modes, payloads, and Java business logic are unchanged.
