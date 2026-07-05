# V2-H PetClinic Hardened Reducer Confirmation

3-pair confirmation was not run.

## Reason

The required single-screen promotion gate failed:

```text
PSS delta: +7,804 KB
Private_Dirty delta: +7,824 KB
memory.current delta: -7,364,608 bytes
workload errors: 0
```

The hardened reducer was artifact-smaller, but PSS and Private_Dirty regressed by more than the allowed 1 MB screen threshold.

## Decision

```text
Do not run P2-1 -> P2H-1, P2-2 -> P2H-2, P2-3 -> P2H-3.
Do not claim productized hardened reducer runtime confirmation.
Keep V2-F/G as artifact/productization milestones.
```

