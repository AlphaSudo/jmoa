# No-CDS Runtime Policy

This policy applies to the stable 2.1 PetClinic measurement. Its final
accepted contrast is R41F fat JAR versus strict B0 exploded Boot, not the
earlier same-layout comparison. Patient 2.15 retains Java 26/Leyden caches
and is outside this no-CDS policy; both its arms share native trimming and
allocator settings.

See [current release boundaries](releases/current-state.md).

The public PetClinic case intentionally disables CDS, AppCDS, Leyden, and the
runtime javaagent.

The accepted PetClinic policy was:

```text
NO_CDS_LOW_DIRTY
```

with:

```text
MALLOC_ARENA_MAX=1
-XX:TrimNativeHeapInterval=5000
```

The accepted 2.1 runtime record freezes both settings for the compared arms.
They are deployment policy, not flags implicitly injected by the optimizer.

## Why This Exists

Earlier measurements showed that default allocator behavior could turn class
count reductions into private dirty memory growth. `MALLOC_ARENA_MAX=1` reduced
that allocator noise for the PetClinic no-CDS experiments.

This does not mean the setting is universally required for every service. It
means no-CDS JMOA claims need an explicit runtime policy and must measure the
policy against the selected deployment shape.

## Claim Guardrails

Do not claim:

- JMOA wins in every no-CDS deployment,
- allocator policy alone is the optimization,
- fat-JAR and exploded Boot behavior are interchangeable,
- CDS and no-CDS have the same economics.
