# Doctor Restored B0/V2 Balanced Screen

## Decision

**BALANCED_SCREEN_NO_STABLE_V2_WIN**

The current restored Doctor environment does not reproduce a stable V2 memory
win. This does not erase the frozen Phase 32K historical result; it means that
result cannot be transferred to the current reconstructed runtime without a
new confirmation.

## Frozen Comparison

The campaign held these inputs fixed:

- exact Phase 32D B0 JAR;
- corrected Phase 32K D2-fixed V2 JAR;
- Temurin/OpenJDK 26+35 custom `jlink` runtime;
- compact-header base CDS, module image, distroless userspace, and capture
  helper;
- fat-JAR launch mode and JVM flags;
- support images, config tree, database initialization, workload, and capture
  scripts;
- 80-request Doctor workload with zero errors.

The product-tuple comparison used the exact preserved archive for each
artifact:

- B0 with `baseline-doctor-management.jsa`;
- V2 with `d2-fixed-doctor-management.jsa`.

Both dynamic archives mapped successfully in every counted arm. Runtime
artifact hashes, image IDs, Java fingerprints, archive hashes, policy proofs,
workloads, linkage checks, and command ledgers passed.

## Why Two Screens Were Needed

The first B0-first/V2-second product screen regressed, while the first reversed
screen won. That made order a plausible confounder. The campaign therefore
stopped feature work and ran a bounded balanced-order diagnostic.

One later candidate-first pair was rejected because its B0 arm missed the
frozen baseline envelope. It was preserved and replaced once. It is not
included below.

## Valid Balanced Results

| Pair | Order | V2 - B0 PSS | V2 - B0 Private_Dirty | V2 - B0 `memory.current` |
|---:|---|---:|---:|---:|
| 1 | B0 then V2 | +7,560 KB | +7,628 KB | +7,856,128 B |
| 2 | V2 then B0 | -2,744 KB | -2,708 KB | -2,441,216 B |
| 3 retry | V2 then B0 | +9,712 KB | +9,804 KB | +10,440,704 B |
| 4 | B0 then V2 | -1,700 KB | -1,380 KB | -1,683,456 B |

Summary:

| Metric | Result |
|---|---:|
| Valid pairs | 4/4 |
| Order balance | 2 B0-first / 2 V2-first |
| PSS wins | 2/4 |
| Median PSS delta | +2,930 KB |
| Median Private_Dirty delta | +3,124 KB |
| Median `memory.current` delta | +3,086,336 B |
| Mean PSS delta | +3,207 KB |
| Median loaded-class delta | -119.5 |
| Median anonymous writable PSS outside heap | +3,796 KB |
| Median second-period PSS effect | +522 KB |

The final balanced set does not support a simple order-only explanation. The
second-period median was small relative to the V2 dispersion, and both orders
contained one win and one regression.

## Attribution

V2 loaded fewer classes in all four valid pairs, but lower class count did not
translate into stable process-memory savings.

The dominant adverse movement was anonymous writable memory outside the Java
heap. NMT did not explain the full movement, and heap/object changes were not
stable across pairs. The correct conclusion is therefore not "classes do not
matter"; it is that class-count savings were smaller than current anonymous
residency variance/regression.

## Artifact-Only Diagnostic

A separate one-pair base-CDS diagnostic held the runtime policy identical and
changed only the application JAR. D2-fixed regressed by:

- +6,176 KB PSS;
- +6,448 KB Private_Dirty;
- +6,090,752 B `memory.current`.

This supports the conclusion that the optimized JAR alone is not a current
memory win. It is not a substitute for the product-tuple balanced result.

## Claim Boundary

Allowed:

- the restored B0 target-process memory shape is acceptable for current
  within-campaign comparisons;
- the corrected V2 artifact and exact per-candidate archives run correctly;
- the current balanced screen does not show a stable V2 win;
- the current adverse median is primarily associated with anonymous writable
  memory outside heap.

Not allowed:

- claiming a current Doctor V2 memory win;
- claiming a confirmed V2 regression from four diagnostic pairs;
- invalidating the historical Phase 32K result retroactively;
- comparing old and current absolute cgroup memory as if the hosts were
  identical;
- claiming startup parity.

All runtime commands and responses are retained in private integrity-indexed
scenario ledgers. Private configuration and raw evidence are not committed.
