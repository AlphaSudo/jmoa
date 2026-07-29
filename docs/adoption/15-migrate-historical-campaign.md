# Migrate a Historical Campaign

Do not compare a historical result with a current JMOA artifact until the
historical and current comparator identities have been reconciled.

## Mandatory Identity Freeze

Record five distinct Java identities:

1. Build JDK.
2. Target-service runtime JDK.
3. CDS-training JDK.
4. Maven/plugin-analysis JDK.
5. V2-C/V2-D analysis JDK.

Do not collapse these into one `java -version` field. Record vendor, complete
version/build, image digest where applicable, and a stable fingerprint for each.

## Comparator Checklist

- B0, V1, and V2 source revisions and source-tree fingerprints.
- Effective POM, dependency coordinates, and dependency JAR hashes.
- Generated-source fingerprint.
- Spring Boot and Spring Framework versions.
- Deployment shape and materialization manifest.
- JVM flags, GC, heap, stack, code-cache, and allocator policy.
- CDS policy and exact archive hash.
- Support images, configuration hash, and database-seed identity.
- Workload identity, request count, warmup, settle, and capture order.
- Capture age and evidence-tool ordering.
- Artifact, image, runtime-origin, and adapter-reference proof.
- One complete command ledger per scenario, including command output.

## Historical Recovery States

Use exactly one:

- `HISTORICAL_ABSOLUTE_RUNS_RECOVERED`
- `HISTORICAL_MEDIANS_ONLY`
- `HISTORICAL_DELTAS_ONLY`
- `HISTORICAL_RUNTIME_INCOMPLETE`
- `HISTORICAL_EVIDENCE_NOT_RECOVERABLE`

Missing values remain missing. Never reconstruct absolute observations from a
published delta.

## Decision Rule

If normalized source/dependency content, runtime policy, or workload identity is
different, historical and current B0 artifacts are not causal comparators.
Authorize one corrected campaign only after the mismatch is concrete and a new
freeze records every identity above.

A mechanism-activation study is separate diagnostic evidence. Counter-enabled
runs cannot update performance claims.
