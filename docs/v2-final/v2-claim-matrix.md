# V2 Claim Matrix

| Claim | Status |
| --- | --- |
| final V2 substantially reduces customers memory versus B0 under the frozen protocol | `HISTORICAL_ONLY_NOT_REPRODUCED` |
| final V2 reduces customers memory versus finalized V1 under the frozen protocol | `ALLOWED_NARROW_REPRODUCIBLE` |
| V2 includes a byte-preserving raw dependency LVT/LVTT reducer | `ALLOWED` |
| raw reducer is runtime-confirmed on public visits | `ALLOWED_NARROW` |
| raw reducer is runtime-confirmed on private Doctor D2 | `ALLOWED_PRIVATE_NARROW` |
| V2 improves startup | `FORBIDDEN` |
| V2 improves every Spring Boot service/runtime mode | `FORBIDDEN` |
| V2 mutates generated/proxy classes | `FORBIDDEN` |

The reproducible customers claim is limited to the freshly captured V1 -> V2
comparison. The historical B0 -> V2 result remains documented for provenance,
but the fresh five-pair frozen-image replication was mixed and prevents carrying
that result forward as an RC2 public claim.
