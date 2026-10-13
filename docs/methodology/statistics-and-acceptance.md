# Statistics And Acceptance

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

JMOA separates diagnostic screens from claimable confirmation.

## Pair Calculation

For each pair, the delta is always candidate minus comparator. Negative memory
deltas favor the candidate. Baseline and candidate medians, pair deltas, paired
delta median, mean, minimum, maximum, and paired-win count are retained.

The median is primary. The mean is secondary. Outliers are not deleted unless
the run itself violates a predeclared validity rule.

## Historical generic confirmation shape

- one pair: diagnostic screen only;
- three valid pairs: minimum confirmation;
- five pairs: used only by a predeclared marginal/conflicting protocol, never as
  an after-the-fact rescue for a failed frozen gate.

The historical final three-service gate required, per service:

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


## Stable 2.1 and provisional 2.15

PetClinic uses twelve held-out four-arm blocks, an exact paired sign test
(p=0.00048828125 for the primary contrast) and frozen bootstrap intervals.
All its frozen RAM and product-cost gates passed.

Patient uses twelve direct B0/candidate pairs. Median saving is 8,586 KiB; the
third-smallest saving is the conditional 8,384 KiB lower bound. Its nominal
one-sided coverage is 98.0713% assuming independent, exchangeable, stable
pairs. Eleven of twelve pairs strictly exceed 8 MiB. Those assumptions are
unproved. Original null precision and cost calibration failed; the approved
post-observation exception stays visible. Numerical cost rows passing their
margin tests do not establish qualified performance bounds.

Execution completion, a passed parser and a confirmed RAM direction are
different dispositions. Neither a completed checklist nor a changed label
upgrades scientific acceptance.
