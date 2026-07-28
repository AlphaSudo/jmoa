# 10 Validate Evidence

V2-C validates all final observations and calculates direct paired confirmation.

Hard invalidators include:

- workload or semantic errors;
- missing claim captures;
- wrong artifact, image, launch mode, CDS policy, or Java agent state;
- PSS/RSS arithmetic failure;
- swap, OOM, or frozen pressure violation;
- missing materialization proof;
- changed campaign implementation bytes.

For each comparison report six within-block deltas, median, mean, minimum, maximum, MAD, paired wins, and exact bootstrap confidence interval.

The direct B0-to-V2 substantial gate requires:

```text
18/18 valid final observations
at least 4/6 paired PSS wins
median PSS <= -4096 KB
bootstrap 95% upper bound < 0
median Private_Dirty <= -1024 KB
median memory.current <= -1048576 bytes
zero semantic errors
V2-C CONFIRMED_WIN
V2-D passed
```
