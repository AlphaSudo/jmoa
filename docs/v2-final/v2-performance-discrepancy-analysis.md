# V2 Performance Discrepancy Analysis

The failed fresh B0-to-V2 run was not caused by different application classes,
a stale V2 artifact, or shadowed dependencies. Byte-level freezing proved that
the application and Boot-loader layers are identical and that V2 is the audited
raw-reduced V1 dependency layer.

The verified defect was uncontrolled container page-cache state. One isolation
screen showed `0 B` block I/O for V1 and about `60 MB` for V2; `memory.current`
therefore charged cold dependency reads to only one variant while process PSS
moved in the opposite direction. The older protocol also always ran the
candidate second and did not capture `memory.stat`.

The final protocol corrects only those measurement defects:

- drop the Podman VM page cache before every variant;
- alternate order `B-C`, `C-B`, `B-C`;
- record cgroup `memory.stat`;
- retain every valid positive and negative pair.

The JVM, artifacts, launch mode, allocator, workload, warmup, settle time, and
success thresholds were not changed. No artifact, protocol, or measurement
mismatch remains unresolved.
