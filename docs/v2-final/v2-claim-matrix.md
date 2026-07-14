# V2 Claim Matrix

| Claim | Status |
| --- | --- |
| final V2 substantially reduces customers memory versus B0 under the frozen protocol | `ALLOWED_NARROW` |
| final V2 substantially reduces customers memory versus finalized V1 under the frozen protocol | `ALLOWED_NARROW` |
| V2 includes a byte-preserving raw dependency LVT/LVTT reducer | `ALLOWED` |
| raw reducer is runtime-confirmed on public visits | `ALLOWED_NARROW` |
| raw reducer is runtime-confirmed on private Doctor D2 | `ALLOWED_PRIVATE_NARROW` |
| V2 improves startup | `FORBIDDEN` |
| V2 improves every Spring Boot service/runtime mode | `FORBIDDEN` |
| V2 mutates generated/proxy classes | `FORBIDDEN` |

The word `substantial` is tied to the final acceptance rule: all three primary
medians improve by at least 1% and more than 3 MiB. It is not a universal claim.
