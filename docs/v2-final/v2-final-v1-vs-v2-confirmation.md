# Final V1 vs V2 Confirmation

The final V2 product is a substantial incremental memory win over finalized V1
under the same public customers-service protocol.

| Metric | Median delta | Percent |
|---|---:|---:|
| PSS | -4,812 KB | -1.388% |
| Private_Dirty | -4,512 KB | -1.337% |
| memory.current | -6,791,168 bytes | -1.584% |
| Heap PSS | -2,300 KB | n/a |
| anonymous_rw PSS | -1,476 KB | n/a |
| Loaded classes | 0 | n/a |

All 6 runs were valid, all 3 pairs won, and the workload had zero errors. The
absolute deltas exceed the fixed 3 MiB substantial threshold for all primary
metrics. V2-C returned `CONFIRMED_WIN`; V2-D passed. No startup claim is made.
