# V2 foundation closure and current publication state

## Current releases

Stable **2.1.0** ships the plugin/runtime distribution and the accepted
packaging-inclusive PetClinic result: 14.88 MiB lower process PSS and 16.24 MiB
lower target-cgroup memory. Frozen RAM and product-cost gates passed, including
CPU +14.71% and startup +1.652 s tradeoffs.

Provisional **2.15** publishes Patient Java 26/Leyden benchmark evidence only:
8.38 MiB lower median stopped-process PSS, conditional lower bound 8.19 MiB.
Original calibration failed and used an approved post-observation exception;
performance bounds remain unqualified or failed. Startup/cache warmth and
production/uploaded-binary parity are unqualified. No new Maven binaries ship.

## Historical foundation milestones

| Track | Delivered foundation | Boundary |
| --- | --- | --- |
| Generated inventory | Family census, attribution, safety taxonomy and ROI reports | No general generated/proxy mutation |
| Classfile footprint | Size, near-64KiB, attributes and runtime correlation | Report-only; no method splitting |
| Evidence validation | Identity, arithmetic, pairing, variance and replay | Validation capability is not a runtime win |
| Memory attribution | smaps/NMT/heap/class reconciliation | Hypotheses are not isolated causal effects |
| Dependency reducer | Opt-in LVT/LVTT mutation and preservation audits | No broad debug stripping; signed/MR/sealed exclusions |
| Runtime workflow | Materialization, origins, recommendation and preflight | Exact artifact and service-policy scope required |

The companion JSON retains original foundation milestone records under their
legacy schema fields and explicit historical context. Its `publicationContext`
holds the current release claims. Old artifact-only closures are not upgraded
because a different later artifact won.

## What closure means

Delivered tooling, completed execution and accepted performance are separate
states. Patient 2.15's completed measurement record does not erase its failed
original calibration or failed/unqualified costs. Neither release establishes
universal savings, arbitrary proxy rewriting, production parity for the private
Patient workload or zero-input independent reproduction.

- [Current release boundaries](releases/current-state.md)
- [Claim register](v2-claim-register.md)
- [Closure taxonomy](v2-phase-closure-taxonomy.md)
- [Known limitations](known-limitations.md)
