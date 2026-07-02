# JMOA Evidence Schema

The V2-C schema is represented in code by `EvidenceModels`.

Core records:

```text
RunManifest
EvidenceCapture
MemoryMetrics
SmapsRegionSummary
NmtSummary
HeapInfo
ClassHistogramSummary
WorkloadResult
RuntimeVerificationGate
PerturbationReport
RunEvidence
PairResult
ConfirmationReport
VarianceClassification
EvidenceValidationReport
EvidenceAnalysisReport
```

The schema supports:

- clean 3-pair confirmations,
- valid regressions,
- diagnostic-only runs,
- launch-mode/runtime-policy verification,
- perturbation warnings,
- variance classification.
