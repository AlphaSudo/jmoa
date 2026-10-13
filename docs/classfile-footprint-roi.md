# Classfile Footprint ROI

Stable 2.1 uses classfile footprint as an admission input, not a runtime RAM
estimate. Patient 2.15 static coverage is 788 sites (455 Tier 1, 333 Tier 2);
these are distinct sites, not invocation counts. Whole-artifact helper cost
and actual runtime activation still matter.

See [current release boundaries](releases/current-state.md).

The stable size profiler exposes classfile footprint features for conservative
admission and discovery rules.

Current ROI fields:

```text
classfileBytes
largestMethodCodeLength
totalMethodCodeBytes
constantPoolCount
totalAttributeBytes
debugAttributeBytes
generatedFamily
generatedRiskLevel
generatedLike
sizeRisk
candidatePriority
```

The report is emitted as:

```text
bytecode-roi-v2-report.json
```

Important boundary:

```text
bytecode-size ROI is not transformation eligibility.
```

A class can be large and still unsafe to rewrite. The profiler uses generated-family labels for reporting. Mutation belongs to
the separate raw reducer and lambda-weaving safety contracts; the profiler
does not enable it.
