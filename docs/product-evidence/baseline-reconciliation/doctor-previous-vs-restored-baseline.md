# Doctor Previous vs Restored Baseline

## Purpose

This report explains why the first current Doctor baseline was rejected and
what changed when the Phase 32 target runtime was reconstructed.

Both observations are useful. The first baseline exposed a real comparator
problem; the restored baseline corrected it.

## What The Previous Baseline Got Right

The previous reconstruction preserved:

- the exact historical B0 application JAR;
- the Phase 32 80-request workload;
- zero workload errors;
- fat-JAR launch mode;
- the target heap, stack, Serial GC, compact-header, code-cache, compiler-count,
  and NMT flags;
- base-CDS as the effective runtime policy category;
- fresh target stacks and audited command/response ledgers.

It correctly demonstrated that protocol labels and an application hash are not
enough to establish runtime comparability.

## What The Previous Baseline Got Wrong

The previous target image used a full current JDK rather than the historical
custom `jlink` runtime.

Consequences:

- different Java runtime build;
- different module image;
- different compact-header base-CDS archive;
- different file-backed mapping shape;
- different anonymous/private residency;
- no proof that the preserved Phase 32 archive accepted the runtime;
- startup and cgroup values treated as more comparable than the environment
  justified.

The full-JDK baseline therefore measured a valid current runtime, but not the
historical Doctor target runtime.

## Correction

The historical Doctor Dockerfile was recovered. It builds a custom JRE with
the Phase 32 module list and creates compact-header base CDS before copying the
runtime into a distroless final image.

The preserved Phase 32K application archive identified its creator as
OpenJDK `26+35`. It rejected Temurin `26.0.1+8` and mapped successfully with the
reconstructed Temurin `26+35` custom JRE.

## Results

| Metric | Previous full-JDK median | Restored custom-JRE median | Change |
|---|---:|---:|---:|
| PSS | 312,088 KB | 329,020 KB | +16,932 KB |
| Anonymous PSS | 302,864 KB | 292,312 KB | -10,552 KB |
| Private_Dirty | 302,872 KB | 292,312 KB | -10,560 KB |
| File PSS | 9,224 KB | 36,560 KB | +27,336 KB |
| `memory.current` | 314,286,080 B | 302,432,256 B | -11,853,824 B |
| Startup | 45,493 ms | 41,737 ms | -3,756 ms |

The higher restored total PSS is not a regression. It restores much of the
historical file-backed mapping while lowering anonymous/private-dirty memory.

Every restored anonymous-PSS and Private_Dirty observation falls inside the
historical Phase 32 ranges. File-normalized restored median PSS is 339,036 KB,
inside the historical 335,792-340,896 KB range.

## Superseded Conclusion

The earlier V1 runtime-cost estimate was produced under the wrong target
runtime tuple. It remains forensic history but is not admissible evidence for
V1 or V2 behavior under the restored Phase 32-compatible runtime.

No old current-runtime delta should be transferred into the new campaign.

## Accepted Boundary

Accepted:

- target JVM anonymous/private-dirty baseline;
- file-normalized process PSS;
- exact B0 JAR, workload, flags, capture timing, and compatible custom JRE.

Not claimed:

- byte-identical historical container image;
- historical support-image identity;
- historical host, Docker engine, cgroup accounting, startup distribution, or
  `memory.current` distribution.

The restored baseline is accepted for a new within-campaign B0-to-V2
comparison. Historical and current absolute `memory.current` values must not be
compared.

## Subsequent V2 Result

The restored tuple was frozen and used for the new Doctor B0/V2 campaign.
The balanced per-candidate-CDS screen did not reproduce a stable V2 memory
win: median V2-minus-B0 PSS was +2,930 KB across four valid balanced-order
pairs. See `doctor-restored-b0-v2-balanced-screen.md`.
