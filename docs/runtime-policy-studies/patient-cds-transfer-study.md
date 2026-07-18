# Patient CDS Transfer Study

This is bounded runtime-policy research. It is not a V2 phase and does not block V2 publication.

## Frozen release truth

- Patient `NO_CDS_LOW_DIRTY`: confirmed, median PSS `-8,903 KB`.
- Patient application CDS: runtime promotion blocked, median PSS `+668 KB`.
- Patient `JDK_BASE_CDS_LOW_DIRTY`: later confirmed, median PSS `-8,279 KB`.
- Three-service release matrix: ready using service-specific confirmed policies.

## Question

Why did the historical Patient optimized candidate win while using per-variant CDS archives, while the corrected V2 artifact wins reliably with CDS disabled but is mixed under CDS?

The historical result is not assumed to prove that CDS itself helped V1. The first runtime gate holds the artifact fixed and compares V1 no-CDS with V1 CDS. The second repeats that policy comparison for V2.

## Controls

- One artifact per policy comparison.
- Variant-specific archive with SHA-256 recorded.
- Identical JDK, allocator, workload, cache policy, support stack, and settle point.
- Diagnostic policy screens are not V1-to-V2 performance claims.
- NMT detail and class-load logging are diagnostic-only and excluded from memory medians.
- A/B archives are both screened; a favorable archive is never cherry-picked.

Doctor is the positive control, not a universal-policy assumption. Its confirmed CDS result demonstrates the required artifact/archive pairing and runtime-proof discipline.

## Stop rule

If V1 CDS does not reproduce, archive training is structurally or behaviorally unstable, or equivalent archives change the sign of the policy effect, Patient CDS remains blocked and the study stops without tuning toward a favorable archive.
