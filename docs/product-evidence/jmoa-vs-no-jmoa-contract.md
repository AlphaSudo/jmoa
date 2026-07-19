# JMOA Versus No-JMOA Product Evidence Contract

Status: frozen before direct product measurements.

## Question

What does the complete final JMOA V2 artifact save relative to the same service
without JMOA when deployment shape, runtime policy, JVM, support stack, workload,
warmup, settle, and capture protocol are held constant?

The primary comparison is `B0 -> V2`. The existing `V1 -> V2` matrix is
engineering-evolution evidence and is not arithmetically combined with older
baseline results.

## Variants

`B0` is built from the frozen service source with no JMOA lambda transformation,
no raw metadata reducer, no `jmoa-runtime-lib`, and no JMOA javaagent.

`V2` is the final admitted V1/full-P2 artifact plus the productized raw dependency
LVT/LVTT reducer, correct runtime support library, materialization proof, and the
service's final confirmed runtime policy.

## Frozen Service Matrix

| Service | Deployment | B0 and V2 policy |
| --- | --- | --- |
| PetClinic customers | Exploded Boot / `JarLauncher` | `NO_CDS_LOW_DIRTY` |
| Doctor | Corrected Spring Boot fat JAR | `APPLICATION_CDS` with separately trained artifact-specific archives |
| Patient | Spring Boot fat JAR | `JDK_BASE_CDS_LOW_DIRTY` using the identical stock JDK base archive |

## Run Contract

- one diagnostic screen per service;
- screen order `B0 -> V2`;
- screen promotion requires all three primary metrics at or below `-1 MiB`;
- one bounded artifact/protocol diagnosis and one corrected screen are allowed;
- promoted services run `B0 -> V2`, `V2 -> B0`, `B0 -> V2`;
- all runs use fresh service containers, controlled page cache, fixed warmup,
  fixed workload, fixed settle, complete capture, and complete teardown;
- valid losing pairs remain in the result.

## Frozen Substantial Product Gate

Every service must satisfy:

```text
valid runs: 6/6
paired PSS wins: at least 2/3
median PSS delta: <= -4096 KB
median Private_Dirty delta: <= -1024 KB
median memory.current delta: <= -1048576 bytes
workload errors: 0
semantic/linkage errors: 0
V2-C verdict: CONFIRMED_WIN
V2-D: passed
```

Thresholds may not change after execution begins. Percentage PSS reduction and
every pair-level delta are reported, but do not replace the absolute gate.

## Evidence And Claim Rules

Raw and private captures stay under `target/`. Only sanitized identities,
results, V2-C/V2-D summaries, and claim boundaries may be committed.

Overall states are exactly:

```text
THREE_SERVICE_PRODUCT_WIN
TWO_SERVICE_PRODUCT_WIN
ONE_SERVICE_PRODUCT_WIN
PRODUCT_EFFECT_NOT_CONFIRMED
```

The previous mixed five-pair PetClinic direct result remains authoritative
history until a new result is reconciled against concrete artifact or protocol
differences. No optimizer or runtime-policy change is permitted during this
campaign merely to obtain a favorable headline.
