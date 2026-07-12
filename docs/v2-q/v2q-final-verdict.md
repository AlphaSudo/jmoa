# V2-Q Final Verdict

V2-Q closes as a narrow application-admission and artifact/semantic milestone.
It admits only ordinary packaged application classes to raw LVT/LVTT reduction;
generated and proxy-shaped classes remain report-only or blocked.

The public visits artifact and semantic gates passed. The first incremental
screen regressed by `+1,432 KB` PSS, `+1,892 KB` Private_Dirty, and
`+1,851,392` bytes of `memory.current`. A single clean diagnostic rerun then
passed, so V2-Q ran a fresh 3-pair confirmation.

The confirmation failed:

```text
paired wins: 1/3
median PSS delta: +5,732 KB
median Private_Dirty delta: +5,760 KB
median memory.current delta: +5,922,816 bytes
```

V2-Q therefore makes no runtime-memory claim and does not run V2-C/V2-D as a
claim pipeline. The outcome is still useful: application-class admission is
byte-preserving and semantically viable on this target, but the visits-service
application-class surface removed only `480` bytes and remains below the new
application ROI threshold.
