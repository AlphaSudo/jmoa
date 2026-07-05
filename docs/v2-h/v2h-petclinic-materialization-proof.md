# V2-H PetClinic Materialization Proof

The hardened PetClinic runtime image was built from the Phase 33M full-P2 exploded Boot context, replacing only dependency jars under:

```text
dependencies/BOOT-INF/lib
```

## Result

```text
base jars: 162
reduced jars: 162
materialized jars: 162
BOOT-INF/lib entries replaced: 162
same jar count: true
original dependency jar bytes: 92,466,274
hardened dependency jar bytes: 88,610,904
materialized dependency jar delta: -3,855,370
```

All materialized dependency jar hashes matched the hardened reduced jar hashes used to build the runtime image.

## Boundary

This proof validates materialization for the V2-H screen. It does not by itself establish a runtime memory claim.

