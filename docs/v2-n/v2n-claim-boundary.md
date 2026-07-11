# V2-N Claim Boundary

V2-N is a policy recommendation capability. It creates no new runtime memory
result and does not change the raw reducer, artifact, image, launch command, or
CDS archive.

Allowed claim:

```text
JMOA can classify known raw-reducer runtime policies as confirmed, screen-required,
artifact-only, diagnostic-only, or blocked using explicit evidence gates.
```

Not allowed:

```text
V2-N improves memory.
V2-N automatically applies a runtime policy.
V2-N makes a CDS archive reusable across artifact variants.
V2-N transfers no-CDS evidence to CDS, or CDS evidence to no-CDS.
V2-N transfers a policy recommendation to another service or reducer engine.
```

The existing V2-I, V2-K, and V2-L runtime claims remain the source evidence.
V2-N only encodes their policy boundaries.
