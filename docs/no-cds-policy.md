# No-CDS Runtime Policy

The public PetClinic case intentionally disables CDS, AppCDS, Leyden, and the
runtime javaagent.

The accepted PetClinic policy was:

```text
NO_CDS_LOW_DIRTY
```

with:

```text
MALLOC_ARENA_MAX=1
```

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
