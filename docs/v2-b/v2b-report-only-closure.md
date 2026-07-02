# V2-B Report-Only Closure

Status: closed as bytecode/classfile footprint and runtime-correlation
infrastructure.

V2-B is not closed as a bytecode reducer. No classfile mutation is enabled in
this milestone.

## Scope

```text
feature area: bytecode and classfile footprint visibility
mutation enabled: false
optimizer mode: report-only
closed milestone: profiler, near-64KB risk, attribute/constant-pool reports, runtime correlation
```

## Service Smoke Summary

| Service | Deployment shape | Classes scanned | Classfile bytes | Largest method |
| --- | --- | ---: | ---: | ---: |
| Spring PetClinic customers-service | EXPLODED_BOOT_APP | 54,326 | 200,152,982 | 57,198 |
| Doctor-service | corrected Spring Boot fat JAR | 59,424 | 219,457,652 | 57,198 |

The shared near-64KB method-risk surface is dependency-owned, not a generated
Spring/JMOA class:

| Class | Artifact | Method | Code bytes | Runtime status in PetClinic |
| --- | --- | --- | ---: | --- |
| org.bouncycastle.pqc.crypto.hqc.ReedSolomon | bcprov-jdk18on-1.81.jar | `<clinit>()V` | 57,198 | STATIC_ONLY_RISK |
| com.google.common.net.TldPatterns | guava-14.0.1.jar | `<clinit>()V` | 49,478 | STATIC_ONLY_RISK |

## Runtime Correlation

PetClinic V2-B2 runtime correlation was run with:

```text
class-load evidence: Phase 33L exploded Boot full-P2 origin proof
class histogram: Phase 33M full-P2 post-workload histogram
mutation enabled: false
```

Summary:

```text
profile classes: 54,672
runtime loaded classes observed: 22,538
profile classes observed loaded: 16,737
profile classes with histogram instances: 5,298
near-64KB methods runtime-loaded: 0
```

The largest static methods remain real classfile risk but are not runtime-loaded
in the available PetClinic workload evidence.

## Closure Boundary

V2-B is closed only as:

```text
classfile size profiler
method Code length and near-64KB report
constant-pool bloat report
attribute footprint report
V2-A generated-family integration
runtime correlation infrastructure and PetClinic smoke
```

The following remain blocked:

```text
bytecode mutation
debug attribute stripping
LocalVariableTable or LocalVariableTypeTable reducer
large-method splitting
constant-pool reducer
BootstrapMethods reducer
bytecode-size memory or startup win claim
```

Future V2-B reducers must be explicitly enabled, validated as release-policy
artifacts, and confirmed through V2-C evidence gates.
