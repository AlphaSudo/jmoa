# Same-Artifact Noise Qualification

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

Status: **NOT_QUALIFIED**

Retrospective repeated-run controls were evaluated before any new three-arm
campaign. They are diagnostic because their order was not originally declared
as a dedicated noise experiment.

| Service / artifact | Absolute PSS drift | Private_Dirty drift | memory.current drift | 1 MiB gate |
|---|---:|---:|---:|---|
| Doctor strict B0 | 2,343 KB | 2,556 KB | 2,600,960 B | failed |
| Patient strict B0 | 6,414 KB | 6,204 KB | 6,311,936 B | failed |

These values are too large to treat a new small delta as causal. A dedicated,
predeclared same-artifact campaign is required after runtime drift and artifact
identity are fixed. No three-arm block was launched from these failed gates.
