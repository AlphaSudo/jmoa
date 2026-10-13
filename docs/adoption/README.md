# JMOA Adoption And Evaluation Guide

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

This guide evaluates three artifacts built from one source universe:

```text
B0 = clean no-JMOA baseline
V1 = JMOA lambda/adapter optimizer
V2 = V1 plus the opt-in LVT/LVTT metadata reducer
```

The primary product result is measured directly as `B0 -> V2`. Historical
results are references, never arithmetic inputs.

Follow the modules in order:

1. [Choose service and policy](01-choose-service-and-policy.md)
2. [Build clean B0](02-build-clean-b0.md)
3. [Build V1](03-build-v1.md)
4. [Build V2](04-build-v2.md)
5. [Prove lineage](05-prove-lineage.md)
6. [Prove runtime origin](06-prove-runtime-origin.md)
7. [Train or select CDS](07-train-or-select-cds.md)
8. [Run qualification](08-run-qualification.md)
9. [Run six balanced blocks](09-run-six-balanced-blocks.md)
10. [Run V2-C and V2-D](10-run-v2c-v2d.md)
11. [Interpret B0/V1/V2](11-interpret-b0-v1-v2.md)
12. [Troubleshoot a missing product effect](12-troubleshoot-missing-product-effect.md)
13. [Read command ledgers](13-read-command-ledgers.md)
14. [Choose full optimization or reducer-only mode](14-choose-jmoa-mode.md)
15. [Migrate a historical campaign](15-migrate-historical-campaign.md)

The historical three-artifact reference outcomes were mixed. The current stable
PetClinic claim uses a later 12-block four-arm protocol; the Patient publication
uses a separate 12-pair Leyden protocol with the explicit qualifications above.
Choose the exact experiment contract before adopting a runner or gate.
