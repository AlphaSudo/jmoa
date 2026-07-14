# V2 Final Performance Verdict

Status: `INCREMENTAL_V1_TO_V2_GATE_PASSED_B0_TO_V2_REPRODUCTION_MIXED`

The reproducible final public customers claim is intentionally narrow:

- finalized V1 -> final V2: `CONFIRMED_WIN`, 2/3 pairs, median PSS -6,012 KB,
  Private_Dirty -5,708 KB, and memory.current -8,081,408 B.

The original corrected B0 -> V2 result remains retained as historical audited
evidence, but a new five-pair exact-image replication was mixed: 2/5 paired
wins, median PSS +585 KB and Private_Dirty +844 KB. V2-C classified the fresh
replication as `MIXED_METRICS_NEEDS_RERUN`; V2-D found a high-confidence
heap-page-touch-growth signal. It must not be presented as an RC2 reproducible
performance claim.

All fresh samples used the frozen no-CDS exploded-Boot protocol, explicit page
cache dropping, alternating pair order, fresh service stacks, and zero workload
errors. This verdict does not establish a startup win or a universal Spring Boot
result.
