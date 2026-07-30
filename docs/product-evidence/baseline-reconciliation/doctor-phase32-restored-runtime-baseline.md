# Doctor Phase 32 Restored Runtime Baseline

## Verdict

**TARGET_MEMORY_BASELINE_REPRODUCED**

Three fresh baseline-only runs reproduced the historical Doctor target JVM's
anonymous/private-dirty memory distribution under the recovered Phase 32K
workload.

This report contains no V1, V2, D2, or optimizer comparison.

## Restored Tuple

The reconstruction uses:

- exact historical Doctor B0 fat JAR;
- application SHA-256
  `0B44E107A4699D090D22CF3C6FAFF52630AA803345DF7775510F22FA3FD10F7B`;
- Temurin OpenJDK `26+35`, released 2026-03-17;
- the historical `jlink` module list and options;
- compact object headers;
- custom-JRE `modules` SHA-256
  `4315B4B87C772D7BE5F07733C40DC0B5B6AB4DF8900E9AF4188AAE5AFAB834C`;
- compact-header base-CDS archive SHA-256
  `F79C398C671A8E248F5FE31A053F4690402578702EE331E42D0D1297C4143CA8`;
- exact 80-request Phase 32K workload;
- zero warmup delay and one capture 20 seconds after workload;
- historical target JVM flags and fat-JAR launch mode.

The preserved 119,812,096-byte Phase 32K application archive, SHA-256
`3EC503C87E232AB32B8638FED8696058F85F9DC3D9FEF63CD09BBD9DC7F8770C`,
maps successfully with this rebuilt custom JRE. That is the compatibility
oracle that identified `26+35` after `26.0.1+8` was rejected.

The runnable measurement image is
`localhost/jmoa-doctor-phase32-restored-b0:runtime-v1`, image ID
`9936B2237B0ACE3155AAEAA54B3276FD22E431A15A53B6651D63B5920954F2CD`.

## Reconstruction Boundary

This is an archive-compatible restoration of the target JVM tuple, not a
byte-for-byte restoration of the old container environment.

Reconstructed/current components:

- final distroless userspace;
- config-server and discovery-server images;
- database image instance;
- container engine, kernel, host, and cgroup accounting;
- a disclosed BusyBox capture helper, not loaded into the JVM.

The historical final image ID was recorded, but its image bytes are no longer
present locally and its distroless base was not pinned by digest.

## Three-Run Results

Every current run used the exact application hash, the same compact-header
base archive, 80 requests, zero workload errors, and health `UP`.

| Metric | Historical values | Historical median | Restored values | Restored median |
|---|---|---:|---|---:|
| PSS | 336,484; 335,792; 340,896 KB | 336,484 KB | 326,612; 329,020; 329,416 KB | 329,020 KB |
| Anonymous PSS | 290,140; 289,172; 294,320 KB | 290,140 KB | 290,052; 292,312; 292,956 KB | 292,312 KB |
| Private_Dirty | 290,148; 289,176; 294,320 KB | 290,148 KB | 290,060; 292,312; 292,968 KB | 292,312 KB |
| File PSS | 46,344; 46,620; 46,576 KB | 46,576 KB | 36,560; 36,708; 36,460 KB | 36,560 KB |
| `memory.current` | 532,750,336; 418,836,480; 424,136,704 B | 424,136,704 B | 300,376,064; 302,432,256; 303,079,424 B | 302,432,256 B |
| Startup | 33,700; 32,100; 38,500 ms | 33,700 ms | 42,926; 41,737; 39,341 ms | 41,737 ms |

All three restored anonymous-PSS values are inside the historical
`289,172-294,320 KB` range. All three restored Private_Dirty values are inside
the historical `289,176-294,320 KB` range.

Current file-backed PSS is about 10 MB lower. Replacing that mapping difference
with the historical file-PSS median gives a normalized current median PSS of
`339,036 KB`, inside the historical `335,792-340,896 KB` PSS range.

## What Did Not Match

Startup distributions do not overlap. The restored median is 8,037 ms slower.

`memory.current` distributions also do not overlap. This metric includes
container/cgroup accounting that changed with the host and container engine.
It is retained as a mismatch, not normalized into a pass.

These mismatches mean this evidence does not prove whole-environment
equivalence. It does prove the target JVM anonymous/private-dirty memory
starting point required for the baseline question.

## Failed Attempts Preserved

The reconstruction did not discard failures:

1. A full-JDK current image produced a three-run anonymous/private-dirty
   distribution above the historical range.
2. Temurin `26.0.1+8` was rejected by the preserved archive and revealed that
   the archive creator was `26+35`.
3. The first restored-runtime screen failed before workload because the
   private signing-key environment variable was not bound. It produced no
   accepted memory evidence.
4. The first aggregate evaluator invocation failed on PowerShell array
   parameter binding. It was post-processing only; the three runtime runs were
   unaffected and the failure remains separately ledgered.

## Command Ledgers

Private ledgers retain every command, response, exit code, duration, and raw
stdout/stderr.

Logical ledger roots:

- `doctor-phase32k-custom-jre-probes-20260730/temurin-26_0_1-8`
- `doctor-phase32k-custom-jre-probes-20260730/temurin-26-35`
- `doctor-phase32k-final-runtime-20260730`
- `doctor-phase32k-restored-b0-screen-20260730` (failed authorization setup)
- `doctor-phase32k-restored-b0-screen-r2-20260730`
- `doctor-phase32k-restored-b0-baseline-2-20260730`
- `doctor-phase32k-restored-b0-baseline-3-20260730`
- `doctor-phase32k-restored-b0-three-run-20260730` (failed evaluator binding)
- `doctor-phase32k-restored-b0-three-run-r2-20260730`

The three successful runtime arms each have one chronological arm ledger
covering launch, health, all 80 requests, runtime capture, logs, and teardown.

## Next Boundary

Baseline reconciliation is complete for Doctor target memory. A candidate
comparison is a separate decision and must reuse this restored tuple. No
candidate result is implied by this baseline-only report.
