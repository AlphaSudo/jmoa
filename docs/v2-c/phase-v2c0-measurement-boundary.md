# Phase V2-C0 Measurement Boundary

Status: implemented as report-only evidence engine.

V2-C treats measurement validation as a product subsystem:

```text
optimizer output -> runtime artifact -> captured evidence -> validation -> pairing -> verdict
```

No optimizer behavior changes were introduced.

## Feature Flags

```text
jmoa.evidence.enabled=false
jmoa.evidence.mode=analyze
jmoa.evidence.inputDir=<path>
jmoa.evidence.outputDir=<path>
jmoa.evidence.expectedPolicy=<NO_CDS|CDS|NO_CDS_LOW_DIRTY|DIAGNOSTIC>
jmoa.evidence.requireArtifactHashes=true
jmoa.evidence.requireWorkloadZeroErrors=true
jmoa.evidence.requireSmapsArithmetic=true
jmoa.evidence.failOnInvalidRun=true
jmoa.evidence.markPerturbingDiagnostics=true
```

## Claim Boundary

V2-C can validate and summarize existing evidence. It does not generate new
measurements and does not make V2-A or V2-B mutation safe by itself.
