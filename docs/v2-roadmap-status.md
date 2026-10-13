# Product capability and publication status

Updated 2026-10-13. This page describes delivered capabilities and evidence
boundaries; it does not publish internal implementation plans or future moves.

## Current releases

| Publication | State |
| --- | --- |
| Stable 2.1.0 | Plugin/runtime distribution; accepted PetClinic packaging-inclusive 14.88 MiB PSS / 16.24 MiB cgroup result with bounded CPU/startup costs |
| Provisional 2.15 | Patient Java 26/Leyden benchmark evidence only; 8.38 MiB median stopped PSS, conditional lower bound 8.19 MiB; failed original calibration admitted by exception; costs unqualified or failed; no new Maven binaries |

Patient startup/cache warmth and production/uploaded-binary parity remain
unqualified. See [current release boundaries](releases/current-state.md).

## Delivered tooling

| Capability | Public contract |
| --- | --- |
| Lambda/adapter rewriting | Profile and safety admission; explicit weaving plan; adapter-reference checks |
| Raw metadata reducer | Opt-in dependency LVT/LVTT only; non-target preservation audit; signed/MR/sealed exclusions |
| Classfile footprint | Report-only size/attribute/near-64KiB analysis |
| Generated-family discovery | Inventory, diagnostic relevance and safety reporting; no admitted general family mutation |
| Artifact materialization | Fat-JAR and exploded-Boot dependency replacement with hash proof |
| Runtime proof | Artifact, policy and archive/cache identity; separate perturbing diagnostics |
| Evidence and attribution | Validity, pairing, variance, smaps/NMT/heap/class reconciliation |
| Recommendation and preflight | Exact registered service/deployment/policy matching; report-only decisions |

## Non-delivered or unqualified surfaces

Generated/proxy/AOT bytecode consolidation, method splitting and broad
constant-pool surgery are not shipped. Startup benefit, universal RAM savings,
zero-input private benchmark reproduction and Patient uploaded-binary parity
are not established. A historical infrastructure closure or a completed
execution checklist does not upgrade those boundaries.

- [Architecture](architecture/system-overview.md)
- [Goal reference](reference/maven-plugin-goals.md)
- [Historical development index](history/v2-phase-index.md)
- [Claim register](v2-claim-register.md)
- [Known limitations](known-limitations.md)
