# JMOA Evidence Engine

JMOA V2-C makes measurement validation a first-class product layer.

The evidence engine is report-only. It does not change optimizer behavior and it
does not run containers. It analyzes already-captured evidence folders and emits
validation, pairing, variance, perturbation, and confirmation reports.

## Maven Goal

```powershell
mvn jmoa:evidence `
  -Djmoa.evidence.enabled=true `
  -Djmoa.evidence.inputDir=<evidence-dir> `
  -Djmoa.evidence.expectedPolicy=NO_CDS_LOW_DIRTY
```

Optional:

```text
-Djmoa.evidence.outputDir=<output-dir>
-Djmoa.evidence.mode=analyze
-Djmoa.evidence.requireArtifactHashes=true
-Djmoa.evidence.requireWorkloadZeroErrors=true
-Djmoa.evidence.requireSmapsArithmetic=true
-Djmoa.evidence.failOnInvalidRun=true
-Djmoa.evidence.markPerturbingDiagnostics=true
```

Historical replay mode checks archived evidence folders against known audited
outcomes:

```powershell
mvn jmoa:evidence `
  -Djmoa.evidence.enabled=true `
  -Djmoa.evidence.mode=replay `
  -Djmoa.evidence.inputDir=<historical-evidence-root> `
  -Djmoa.evidence.replaySuite=docs/v2-c/historical-replay-suite.example.json
```

## Expected Run Layout

Each run can be a subdirectory under the input directory:

```text
evidence/
  b1/
    run-manifest.json
    smaps_rollup
    smaps
    memory.current
    workload.json
  c1/
    run-manifest.json
    smaps_rollup
    smaps
    memory.current
    workload.json
```

The engine is tolerant of older phase folders. If `run-manifest.json` is absent,
it infers baseline/candidate and pair index from folder names such as `b1`,
`baseline-1`, `candidate-1`, or `p2-1`.

## Outputs

```text
jmoa-parsed-evidence.json
jmoa-evidence-analysis.json
jmoa-evidence-validation.json
jmoa-evidence-validation.md
jmoa-paired-confirmation.json
jmoa-paired-confirmation.md
jmoa-variance-classification.json
jmoa-variance-classification.md
jmoa-perturbation-report.json
jmoa-perturbation-report.md
jmoa-confirmation-summary.md
```

Replay mode writes:

```text
jmoa-evidence-replay-report.json
jmoa-evidence-replay-report.md
```

## Validation Boundary

Hard invalid examples:

- workload errors,
- health not UP,
- missing post `smaps_rollup`,
- missing `memory.current`,
- artifact hash mismatch when expected hash is provided,
- wrong CDS/no-CDS policy,
- javaagent present when forbidden,
- `PSS > RSS`,
- RSS arithmetic mismatch,
- missing adapter references,
- original jar shadowing optimized jar.

Soft warnings:

- class-load logging during memory confirmation,
- JFR enabled,
- NMT detail,
- `GC.run` before official capture,
- missing dynamic runtime-origin proof.

## Verdict Boundary

Single pair is screen-only. Three clean pairs are the minimum confirmation. Five
pairs are recommended for marginal or conflicting metrics.

Primary metrics:

```text
PSS
Private_Dirty
memory.current
```

The evidence engine can say a result is valid, invalid, confirmed, regressed, or
needs rerun. It does not prove causality without variance and runtime evidence.

## Historical Replay

V2-C includes a replay runner because the best regression tests for this project
are the audited phase outcomes:

- Phase 33M should replay as `CONFIRMED_WIN`.
- Phase 33K.7b should replay as `CONFIRMED_REGRESSION` with heap-page-touch
  evidence when the archived metrics are present.
- Phase 32L should replay as the corrected Doctor-service win.
- Phase 32I should replay as invalid evidence.

The public source repo ships only the replay-suite contract, not private raw
phase logs. Point replay mode at your local archived evidence root to enforce
the contract.
