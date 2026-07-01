# Classfile Footprint ROI

V2-B adds bytecode-size features that can be used by future JMOA admission
rules.

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

A class can be large and still unsafe to rewrite. V2-B relies on V2-A generated
family labels and future reducer-specific safety taxonomy before enabling any
mutation.
