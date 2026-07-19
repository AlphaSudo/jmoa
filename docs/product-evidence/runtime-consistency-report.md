# Runtime Consistency Report

Status: **RECONCILIATION_GATES_BLOCKED**
Aggregate claim state: `DIRECT_PRODUCT_MATRIX_UNDER_RECONCILIATION`

The investigation reached its predeclared stop conditions. Artifact lineage is
coherent for the accepted B0/V1/V2 triplets, but the historical replay and
same-artifact variance gates do not admit a new three-arm product campaign.
No invalid comparison was substituted for the blocked campaign.

## Gate Results

| Gate | PetClinic | Doctor | Patient |
|---|---|---|---|
| Strict B0 proof | Pass | Pass | Pass |
| Coherent source lineage | Pass | Pass | Pass |
| Historical/current replay | **Drift: 0/3, +8,647 KB PSS** | Accepted D2 to D2R replay; D2 is not B0 | Accepted artifacts present; direct screen used another V2 SHA |
| Same-artifact noise | Dedicated control not run after replay stop | Retrospective control fails 1 MiB gate | Retrospective control fails 1 MiB gate |
| Rotated B0/V1/V2 block | Not admitted | Not admitted | Not admitted |

## Concrete Findings

1. PetClinic Phase 33M was replayed with the original runner, frozen image IDs,
   artifact hashes, exploded-Boot launch mode, allocator policy, warmup, settle,
   and workload. The accepted `3/3`, `-4,758 KB` PSS result reversed to `0/3`,
   `+8,647 KB`. Loaded-class reduction remained, while page residency changed.
2. Doctor's clean B0, historical D2, and final V2 are now proved to come from
   one source universe. Its direct `3/3`, `-5,809 KB` result remains a confirmed
   measurement, but a retrospective B0-to-B0 comparison exceeded the frozen
   1 MiB qualification thresholds.
3. Patient's accepted corrected V2 is `4CFC40AD...`; the direct screens measured
   candidate `FB4E6295...`. Those screens cannot decide the accepted V2 product
   comparison. The retrospective B0 control also exceeded all noise thresholds.
4. The current host exposes JDK 17 through `JAVA_HOME` but JDK 26 through
   `PATH` (`java` and `javac`). Bare `mvn` is unavailable in this shell. This
   toolchain split is recorded as a reproducibility risk, not guessed away.

## Decision

The direct records are preserved, but the aggregate product verdict stays under
reconciliation. A new three-arm run is allowed only after the historical drift
is isolated and dedicated same-artifact controls pass the frozen thresholds.
The public workflow records exact argv, environment, hashes, and fingerprints
so that the next attempt cannot silently change the protocol.
