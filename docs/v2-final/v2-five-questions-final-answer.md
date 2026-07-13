# V2 Five Questions Final Answer

## 1. Can JMOA extend beyond lambdas to generated/synthetic classes?

Answer: yes for visibility and safety analysis; no V2 mutation was admitted.

Implemented: taxonomy, static inventory, runtime attribution, matched lifecycle
captures, ROI ranking, prototype admission gates, and 3/3 matched diagnostic
bundles in V2-W.

Not implemented: generated-family bytecode mutation.

Publication blocker: no, as long as the V2 release says generated mutation is
deferred.

## 2. Can JMOA reduce 64KB/classfile/bytecode bloat surgically?

Answer: partially.

Implemented: classfile footprint profiler, near-64KB reports, attribute reports,
raw LVT/LVTT dependency reducer, byte-preservation auditing, and runtime
confirmations for the raw reducer in exact scopes.

Not implemented: large-method splitting, constant-pool reduction, and
`BootstrapMethods` rewriting.

Publication blocker: no, if V2 claims only the raw LVT/LVTT reducer.

## 3. Is the three-run variance problem solved?

Answer: substantially yes.

Implemented: V2-C validation, paired confirmations, invalid-run rejection,
variance classification, perturbation warnings, historical replay, and workflow
guards that block claims after failed confirmation.

Publication blocker: no.

## 4. Do we understand where memory savings come from?

Answer: mostly yes.

Implemented: V2-D category deltas, smaps/NMT reconciliation, heap/object
classification, class/metaspace attribution, anonymous mapping attribution, and
historical explanations.

Not implemented: full productized JFR/async-profiler/JOL attribution pipeline.

Publication blocker: no.

## 5. Did advanced runtime policy get implemented?

Answer: mostly for the confirmed V2 protocols.

Implemented: no-CDS low-dirty protocol, CDS protocol with variant-specific
archive enforcement, stale archive rejection, runtime policy recommendation,
preflight, materialization proof, semantic smoke, and confirmation workflow.

Not implemented: broad AppCDS/Leyden/Kubernetes/FAST_STARTUP automation.

Publication blocker: no.

## Final Performance Acceptance

A fresh direct final public `B0 -> V2` customers run on July 13, 2026 did not
confirm. That is a P0 release blocker for any public `v2.0.0` headline claiming
that the final V2 product substantially beats the unoptimized public baseline.

See [V2 Final Performance Acceptance](v2-final-performance-acceptance.md).

