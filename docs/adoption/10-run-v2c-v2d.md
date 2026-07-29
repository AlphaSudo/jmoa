# 10 Run V2-C And V2-D

Run V2-C and V2-D separately for all three legs.

V2-C answers whether evidence is claimable:

```text
artifact/runtime identity
workload validity
smaps arithmetic
runtime policy
diagnostic perturbation
paired confirmation
```

V2-D explains the movement:

```text
heap PSS versus heap used
anonymous writable mappings
NMT total and categories
Class/Metaspace and Code
class histogram bytes and counts
object-family deltas
mapped files and CDS
```

The completed `B0 -> V1` attribution found:

```text
Doctor: low-signal +654 KB PSS; no standalone V1 win
Patient: low-signal +910 KB after the capture-timing correction
PetClinic: +2,516 KB, heap page touch plus anonymous growth
```

Class counts decreased in all three cases. Doctor and Patient remain too
low-signal to call V1 wins, while PetClinic's class-count reduction is offset
by runtime page-touch overhead.

## Gate

Do not claim causality from one metric. A heap-PSS change with flat heap used
and flat histogram bytes is page-touch behavior, not retained-object growth.
