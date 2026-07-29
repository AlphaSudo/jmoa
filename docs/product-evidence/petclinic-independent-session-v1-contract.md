# PETCLINIC_INDEPENDENT_SESSION_V1

## Status

Pre-registered before any independent-session target result.

Execution is now complete. The B0 qualification failed the frozen repeatability
limits and terminated as
`PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST`. See the
[final result](petclinic-independent-session-v1-result.md).

This is the final authorized PetClinic protocol on the current Hyper-V Debian
VM. It follows the `PETCLINIC_TARGET_ONLY_V1` finding that a second target in
one support lifecycle incurred a systematic same-artifact memory shift.

## Fixed End

The campaign has exactly four steps:

1. read-only attribution of the completed B0 controls;
2. one excluded B0/B0/B0 diagnostic against one support stack;
3. one independent-session qualification for B0 and, only if admitted, V2;
4. one final six-session B0/V2 product block.

No further protocol redesign is authorized on this VM.

## Measurement Unit

Every qualification and product observation consists of:

```text
fresh config/discovery support stack
fixed 180-second support settle
one target JVM
20-second target warmup
81-request corrected workload
5-second target settle
one claim capture
target teardown
support teardown
```

No qualification or product session runs a second target against the same
support stack.

## Frozen Runtime

- `EXPLODED_BOOT_APP`
- `NO_CDS_LOW_DIRTY`
- `MALLOC_ARENA_MAX=1`
- no CDS, AppCDS, Leyden, or runtime javaagent
- same B0 and V2 images, artifacts, config tree, JDK, and JVM flags
- cold page-cache reset before each target
- exact target process and target cgroup metrics

## Diagnostic Sequence

One B0-1, B0-2, B0-3 sequence runs against one support stack. It is excluded
from qualification and product evidence. It classifies the earlier shift as
first-versus-later, alternating, monotonic, within 1 MiB, or mixed.

## Independent Qualification

Run exactly three complete B0 sessions. Compute range, median, median absolute
deviation, maximum pairwise difference, and PSS coefficient of variation.

Frozen admission limits:

- maximum pairwise PSS difference: 1,024 KB;
- maximum pairwise Private_Dirty difference: 1,024 KB;
- maximum target `memory.current` difference: 2,097,152 bytes.

If B0 fails, terminate:

```text
PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST
```

No V2 session and no further PetClinic protocol may run on this VM.

Only if B0 passes, run exactly three V2 qualification sessions under the same
limits. V2 failure terminates as `V2_ARTIFACT_RUNTIME_VARIANCE`.

## Product Block

Only if both artifacts qualify:

```text
Session 1: B0
Session 2: V2
Session 3: V2
Session 4: B0
Session 5: B0
Session 6: V2
```

Adjacent sessions form three V2-C pairs:

```text
B0 -> V2
V2 -> B0
B0 -> V2
```

The adapter links raw captures read-only and derives only pair-specific
manifests. Each raw manifest retains its unique support and target session IDs.

## Final Gate

- six of six sessions valid;
- six distinct support sessions;
- at least two of three paired wins;
- median PSS at most -4,096 KB;
- median Private_Dirty at most -1,024 KB;
- median target `memory.current` at most -1,048,576 bytes;
- zero semantic errors;
- V2-C `CONFIRMED_WIN`;
- V2-D passed.

Terminal product outcomes are `TRUSTED_PRODUCT_WIN` or
`PRODUCT_EFFECT_NOT_CONFIRMED`.

No threshold, duration, order, workload, runtime policy, artifact, or
environmental rule may change after independent-session target evidence begins.
