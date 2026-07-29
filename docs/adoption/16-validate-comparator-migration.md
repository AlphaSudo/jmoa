# Validate Comparator Migration

An accepted historical V1 result is not automatically comparable with a newly
built B0. Before pairing them, prove that both artifacts belong to the same
experiment universe.

## Required Equivalence

Freeze and verify all of these dimensions:

| Dimension | Required proof |
|---|---|
| Source | Exact commit or source-tree digest |
| Dependencies | Lockfile, resolved dependency graph, and dependency JAR hashes |
| Generated output | Generated-source/class inventory and hashes |
| Application artifact | Full artifact SHA-256 and normalized entry comparison |
| Optimizer | Plugin, runtime library, profile, admission set, and output hashes |
| JVM | Vendor, full version, architecture, and effective flags |
| Runtime policy | CDS mode, exact archive hash when applicable, allocator policy |
| Deployment | Fat JAR, exploded Boot, or expanded classpath, including class origins |
| Support stack | Database/config/image identity and initialized data |
| Workload | Request order, count, authentication, accepted statuses, and error policy |
| Timing | Health, warmup, workload, settle, and capture offsets |
| Capture | Commands, ordering, files, and perturbing diagnostics |

If any required dimension is unknown, the result is a directional historical
reference, not a product comparator.

## Migration Procedure

1. Recover the historical B0 and V1 artifacts and hash them.
2. Compare normalized archive entries before running either artifact.
3. Reject a baseline containing JMOA classes, runtime adapters, or rewritten
   application classes.
4. Reconstruct the effective runtime, not only the intended flags. A rejected
   application CDS archive may silently leave base CDS active.
5. Run the B0 alone first. Apply the historical workload, wait the historical
   settle interval, and compare anonymous/private-dirty and file-backed memory
   separately.
6. Authorize one non-claim B0/V1 directional pair only if the absolute B0 is
   sufficiently reconciled.
7. Authorize a balanced campaign only if the historical direction returns and
   the complete identity tuple is frozen.

## Never Substitute Convenient Artifacts

Do not replace a missing historical B0 with:

- a current clean build,
- a later fixed build,
- a similarly named service,
- an artifact from another JDK or Spring Boot universe,
- or a sanitized Markdown summary.

Such evidence can explain history, but it cannot establish a direct product
effect.

## Required Command Ledger

Every scenario must retain one append-only command ledger containing:

- every command and its exact arguments,
- working directory and allowlisted environment,
- start/end time and exit code,
- complete stdout and stderr files,
- input/output hashes,
- failed attempts and retry reasons,
- HTTP method, redacted headers, response status, and response log,
- and whether the output was accepted as evidence.

The ledger is part of the experiment. A chart without the commands that
produced it is not a reproducible result.

## Current Reconstruction Example

The historical comparator reconstruction demonstrates all three rejection
paths:

- Doctor: exact artifacts recovered, but reconstructed V1 did not reproduce
  the historical direction.
- PetClinic: historical B0 contained JMOA output and semantic application
  drift.
- Patient: historical B0/source/support identity was not recoverable.

See the
[reconstruction closure](../product-evidence/comparator-reconstruction/comparator-reconstruction-closure.md).
