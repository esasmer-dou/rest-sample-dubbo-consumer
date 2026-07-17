# rest-sample-dubbo-consumer v0.3.2

[English](RELEASE_NOTES_v0.3.2.md) | [Turkish](RELEASE_NOTES_v0.3.2.tr.md)

This patch release aligns with `rust-java-rest:3.4.1`, `java-rust-dubbo:0.4.1`,
`rest-sample-utility:0.2.0`, and `rust-sample-model:0.2.0`.

## What Changed

- The `micro-2x2` profile now gives the raw and typed customer-create routes a `4` request limit
  and a `250 ms` queue wait each.
- Both routes share the same two native workers, provider permits, Hikari connections, and
  PostgreSQL commit path. The new limits prevent a hidden sixteen-request queue in front of that
  shared backend.
- A repeatable c16 write gate checks maximum p99, run-to-run p99 spread, and non-2xx rate instead of
  accepting one favorable run.
- English and Turkish configuration guides explain how route limits add up when endpoints share a
  provider and database pool.

## Measured Result

Under the same Docker Desktop `micro-2x2` limits, the final three-run c16 gate measured:

| Metric | Before | v0.3.2 |
|--------|-------:|-------:|
| Average RPS | `340.93` | `501.74` |
| Average p99 | `234.45 ms` | `132.91 ms` |
| Maximum p99 | `485.22 ms` | `143.83 ms` |
| p99 spread | `329.92 ms` | `25.36 ms` |
| Non-2xx rate | `0.020%` | `0.014%` |

These numbers are sample-environment evidence, not a universal production guarantee. Run the gate
with your own provider, PostgreSQL storage, CPU limits, and pod memory limits before changing a
production profile.

## Compatibility

Java REST annotations, handler signatures, Dubbo service contracts, JSON payloads, and native ABI
requirements are unchanged. Existing application code does not need to change.
