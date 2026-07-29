# JMOA Adoption And Evaluation Guide

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

The current reference outcomes are deliberately mixed: Doctor is a complete
product win. Corrected Patient and PetClinic both observe direct reductions,
but neither meets magnitude or uncertainty gates. The negative paths are part
of the product contract.
