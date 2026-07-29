# PetClinic B0 Period-Effect Attribution

This is a read-only analysis of the completed `PETCLINIC_TARGET_ONLY_V1`
same-artifact controls. No service was rerun for this report.

## Finding

The second target in each shared-support pair was more expensive regardless of
its logical label:

| Pair | PSS | Private_Dirty | `memory.current` | cgroup anon | Java heap PSS | Heap used |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1, second minus first | +5,535 KB | +5,612 KB | +5,763,072 B | +5,750,784 B | +156 KB | +1,310 KB |
| 2, second minus first | +10,169 KB | +10,200 KB | +10,932,224 B | +10,436,608 B | 0 KB | +990 KB |
| Median | +7,852 KB | +7,906 KB | +8,347,648 B | +8,093,696 B | +78 KB | +1,150 KB |

The process-anonymous and cgroup-anonymous deltas explain the shift far better
than Java heap mappings, retained histogram bytes, class metadata, or code
metadata. The classification is:

```text
SECOND_POSITION_NATIVE_ANON
```

## Timing

Capture timing did not converge under the frozen limits:

```text
median second-minus-first JVM age: +15.9215 seconds
allowed JVM-age difference: 2 seconds
median post-workload difference: -0.641 seconds
allowed post-workload difference: 0.5 seconds
```

One second-position arm also took `55.719` seconds to start versus `27.173`
seconds for its first-position peer. The report therefore does not claim a
single allocator, GC, or heap-page cause beyond the observed native-anonymous
residency and timing divergence.

## Boundary

This report attributes the old same-artifact period effect. It does not compare
B0 with V2 and does not change any frozen campaign threshold. Raw captures
remain outside Git.
