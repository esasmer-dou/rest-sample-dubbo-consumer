# 0.8.1

[English](RELEASE_NOTES_v0.8.1.md) | [Türkçe](RELEASE_NOTES_v0.8.1.tr.md)

Release date: October 30, 2026.

## What Changed?

Align REST/platform `4.8.1`, plain Dubbo `0.9.1`, shared model `0.5.1` and the `direct-json-consumer` Spring Dubbo dependencies/processor `0.8.1`. Include selected-contract readiness/recovery coverage and the query-only configuration overlay.

## Aligned Versions And Upgrade

REST/platform `4.8.1`; cache `0.9.1`; plain Dubbo `0.9.1`; Spring Dubbo `0.8.1`;
model/utility `0.5.1`; four application samples `0.8.1`. Agent `0.7.1` is unchanged.
Align producer and consumer dependencies together and rebuild.
Do not copy older DLL/SO files into new packages. Earlier release notes remain historical records.
Required readiness failures return `503`; unselected checks remain `SKIPPED`. Query-only selection is not access control. Existing defaults are unchanged.

## Qualification Scope

No ARM hardware qualification, new performance guarantee or F18 closure is claimed.
Validation evidence is recorded with the corresponding change/release.
