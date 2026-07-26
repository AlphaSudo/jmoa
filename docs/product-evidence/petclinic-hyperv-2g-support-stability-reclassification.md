# PetClinic 2 GiB Support-Stability Reclassification

## Superseding Interpretation

The historical scenario ledger remains immutable and records:

```text
STOPPED_INSUFFICIENT_SUPPORT_STACK_HEADROOM
```

That label combined two independent admission dimensions. The evidence shows
that capacity/headroom passed and support stability was not qualified. The
correct superseding interpretation is:

```text
STOPPED_SUPPORT_STACK_STABILITY_GATE
```

This reclassification does not convert the run into admissible product
evidence and does not launch or infer any B0/V2 comparison.

## Independent Gates

| Dimension | Result | Evidence |
| --- | --- | --- |
| Capacity/headroom | Passed | Minimum `MemAvailable` 1,121,853,440 B, above 734,003,200 B |
| Swap | Passed | Zero configured and zero cgroup use |
| Pressure | Passed | Memory PSI `some/full avg10` remained zero |
| OOM | Passed | `oom` and `oom_kill` remained zero |
| Health/restarts | Passed | Both services HTTP 200; zero restarts |
| Stability | Not qualified | Total support `memory.current` range 18,612,224 B exceeded 2,097,152 B |

The old sampler summed `memory.current` read from inside each support
container. It did not preserve the host cgroup paths or `memory.stat`, so the
historical range cannot distinguish anonymous private growth from file-cache
charging or post-health JVM convergence.

## Investigation Status

- Product evidence: not admitted.
- B0/V2 delta: none.
- Historical command-response evidence: preserved.
- Existing drift attribution: `INSUFFICIENT_CAPTURE`.
- Correct next gate: `SUPPORT_CALIBRATION_V2`.

`SUPPORT_CALIBRATION_V2` audits exact host cgroup paths, decomposes
`memory.stat`, observes each fresh support stack for 180 seconds, and evaluates
only the final 60 seconds using private-memory and pressure gates. Three of
three independent support stacks must pass before the non-evidence B0 capacity
arm is allowed.

