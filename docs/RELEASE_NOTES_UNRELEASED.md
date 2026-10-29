# UNRELEASED

These checkout changes follow published `0.8.0`; they are not part of that release.
No new release version, tag or native ABI is introduced.

## Readiness And Client Selection

- `sample.consumer.readiness.contracts=auto|full|catalog-only|query-only` selects required checks once at startup. The default `auto` preserves the existing full/catalog-only surface behavior.
- Missing required clients or failed RPCs return `503`; unselected checks report `SKIPPED`, `required=false`, without calling the provider. Liveness remains local.
- `sample.consumer.catalog.enabled=false` omits the generated catalog client and catalog routes at startup. Generated client constructor types are fully qualified for reflection-free AOT factory compilation.
- The standalone `config/query-only.properties` overlay pairs the normal full consumer with `native-db-query-provider`, retaining the production resource bounds. Selection is cached; requests do not reparse configuration or block waiting for RPC replies.

## Rebuild Required

An existing downloaded `0.8.0` consumer jar does not support these settings or the new overlay. Rebuild this checkout even though the resulting jar filename still contains `0.8.0`. See the [run recipe](../README.md#readiness-contract-selection).

The query-only overlay keeps `surface=full` because query and command routes share a handler. Command routes/client remain for API compatibility but are unsupported against the query-only provider. This is not a read-only authorization policy.

## Verification

Regression coverage includes full/catalog/query-only readiness, required failures, matching overlays and generated startup factories, including qualified optional client lookups. AOT `test-compile` and JUnit execution on EmaTech are still required; this note does not claim a passing build or runtime qualification.
