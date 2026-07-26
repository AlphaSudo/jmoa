# Historical Support Memory-Drift Attribution

## Verdict

```text
INSUFFICIENT_CAPTURE
```

The ten historical samples are sufficient to prove that the old total
`memory.current` range gate failed. They are not sufficient to determine why.

## Reconstructed Series

| Sample | Aggregate PSS KB | Private Dirty KB | `memory.current` B | `MemAvailable` B |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 386,496 | 365,936 | 464,957,440 | 1,125,425,152 |
| 2 | 372,276 | 351,712 | 450,002,944 | 1,123,430,400 |
| 3 | 372,604 | 352,040 | 450,363,392 | 1,129,623,552 |
| 4 | 372,812 | 352,204 | 450,965,504 | 1,138,499,584 |
| 5 | 375,412 | 354,804 | 468,615,168 | 1,130,168,320 |
| 6 | 374,532 | 353,924 | 452,882,432 | 1,122,484,224 |
| 7 | 374,692 | 354,088 | 453,292,032 | 1,121,886,208 |
| 8 | 374,904 | 354,296 | 453,312,512 | 1,121,853,440 |
| 9 | 374,968 | 354,364 | 453,877,760 | 1,126,092,800 |
| 10 | 375,072 | 354,468 | 452,890,624 | 1,125,912,576 |

The shape is neither a simple monotonic 18.6 MiB growth nor evidence of memory
exhaustion. Sample 1 was high, samples 2-4 fell, sample 5 contained a
`memory.current` spike, and samples 6-10 occupied a narrower band.

## Missing Attribution Inputs

The old capture did not include:

- exact host cgroup paths;
- timestamps or seconds since health;
- `memory.stat` anonymous/file/slab decomposition;
- exact-container `memory.pressure`;
- class, Metaspace, CodeCache, or heap diagnostics after the window.

Therefore it cannot distinguish `ANON_PRIVATE_GROWTH`, `FILE_CACHE_CHARGE`,
`JVM_WARMUP_CONVERGENCE`, or a mixed explanation. The preserved old data is
not discarded; it is classified `INSUFFICIENT_CAPTURE` and superseded for host
admission by the pre-registered `SUPPORT_CALIBRATION_V2` protocol.

