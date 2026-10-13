# Patient Capture-Timing Correction Result

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

The one campaign authorized by `PROVEN_CAPTURE_TIMING_DEFECT` completed with
the frozen artifacts, runtime policy, workload, six permutations, and product
thresholds unchanged.

- Qualification: 3/3 valid
- Final observations: 18/18 valid
- Invalid final attempts: 0
- Workload and semantic errors: 0
- Scenario command ledgers: 21
- Final workload-to-capture lag: 13.249 to 14.866 seconds
- Allowed ceiling: 65 seconds

| Leg | Median PSS | Wins | Exact bootstrap 95% CI |
|---|---:|---:|---:|
| B0 to V1 | +910 KB | 3/6 | [-5,326.5, 6,151] KB |
| V1 to V2 | -6,135.5 KB | 5/6 | [-7,925.5, 2,218] KB |
| B0 to V2 | -1,266.5 KB | 4/6 | [-12,330.5, 3,488.5] KB |

The corrected direct result favors V2, and V2-C/V2-D completed, but the frozen
`-4,096 KB` magnitude gate and bootstrap-upper-below-zero gate fail. The final
outcome remains `B0_TO_V1_NOT_REPRODUCED`; no further Patient rerun is
authorized.

The first post-processing attempt inherited a stale JDK 17 `JAVA_HOME`.
Deterministic `Stage Explain` recovery ran under JDK 26 against the same frozen
sessions; no measurement session was rerun. The recovery command and output
remain in the private campaign ledger.
