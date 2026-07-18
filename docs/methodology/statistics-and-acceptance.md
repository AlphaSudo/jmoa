# Statistics And Acceptance

JMOA separates diagnostic screens from claimable confirmation.

## Pair Calculation

For each pair, the delta is always candidate minus comparator. Negative memory
deltas favor the candidate. Baseline and candidate medians, pair deltas, paired
delta median, mean, minimum, maximum, and paired-win count are retained.

The median is primary. The mean is secondary. Outliers are not deleted unless
the run itself violates a predeclared validity rule.

## Confirmation Shape

- one pair: diagnostic screen only;
- three valid pairs: minimum confirmation;
- five pairs: used only by a predeclared marginal/conflicting protocol, never as
  an after-the-fact rescue for a failed frozen gate.

The final three-service gate required, per service:

```text
valid runs: 6/6
paired wins: at least 2/3
median PSS delta: <= -4096 KB
median Private_Dirty delta: <= -1024 KB
median memory.current delta: <= -1048576 bytes
workload and semantic errors: 0
V2-C: CONFIRMED_WIN
V2-D attribution: present
```

Some earlier reducer experiments used narrower incremental gates. Their claims
remain tied to those registered protocols and do not replace the final
V1-to-V2 matrix.

## Verdicts

`CONFIRMED_WIN` means the frozen evidence and metric gates passed.
`CONFIRMED_REGRESSION` means valid evidence consistently favored the comparator.
`MIXED_METRICS_NEEDS_RERUN` is not claimable. `INVALID_EVIDENCE` means no
performance direction may be inferred. A valid negative result is preserved as
evidence rather than relabeled as an environment failure.
