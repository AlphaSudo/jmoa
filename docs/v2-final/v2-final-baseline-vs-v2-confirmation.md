# Final B0 vs V2 Confirmation

The frozen final V2 product is a substantial public memory win over B0 under
the confirmed customers-service protocol.

| Metric | Median delta | Percent |
|---|---:|---:|
| PSS | -8,956 KB | -2.583% |
| Private_Dirty | -8,620 KB | -2.555% |
| memory.current | -11,247,616 bytes | -2.624% |
| Heap PSS | -6,584 KB | n/a |
| anonymous_rw PSS | -1,612 KB | n/a |
| Loaded classes | -153 | n/a |

All 6 runs were valid, the workload had zero errors, and 2/3 pairs won. Pair 2
regressed and remains in the median. V2-C returned `CONFIRMED_WIN`; V2-D passed.
No startup claim is made.
