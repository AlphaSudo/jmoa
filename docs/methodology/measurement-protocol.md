# Measurement Protocol

JMOA measures the deployed service after a semantic workload. It does not infer
runtime memory from artifact size or class count.

## Primary Metrics

**PSS** proportionally divides shared mapped pages among processes. It is the
primary process-footprint metric because RSS charges every shared page in full.

**Private_Dirty** captures process-private pages that cannot be reclaimed by
dropping clean file cache. It is a useful companion to PSS for heap and
anonymous allocator behavior.

**`memory.current`** retains the cgroup-level view, including process memory and
file-backed/container accounting. It is required, but interpreted alongside
smaps because page cache and support-process state can move independently of
the JVM's process PSS.

Secondary evidence includes RSS, full-smaps categories, heap PSS, NMT, heap
occupancy, class histogram bytes, loaded classes, metaspace, and startup time.

## Run Lifecycle

1. Freeze artifact, image, launch mode, runtime policy, and workload identity.
2. Stop stale containers and verify the intended support stack.
3. Apply the declared page-cache policy before each variant.
4. Start one variant and wait for health.
5. Run the exact semantic workload and require zero errors.
6. Wait to the declared capture point.
7. Capture smaps, cgroup, NMT, heap, histogram, and runtime proof.
8. Stop the variant before launching its pair.

Official confirmation uses balanced order: `B -> C`, `C -> B`, `B -> C`.
Single runs are screens only. A valid losing pair remains in the data and the
median; it is never removed because its direction is inconvenient.

## Invalid Runs

A run is invalid when required evidence is missing or truncated, workload
errors are nonzero, health is not UP, artifact or policy proof disagrees with
the contract, PSS arithmetic is impossible, the wrong archive is mapped, or a
forbidden javaagent is present. Invalid runs are rerun; they are not converted
into losses or silently omitted.

Diagnostic instrumentation such as class-load logging, JFR, NMT detail, forced
GC, or large in-container output is captured separately unless the protocol
explicitly measures its overhead.

See [Statistics and Acceptance](statistics-and-acceptance.md) and
[Interpreting Results](../reproduction/interpreting-results.md).
