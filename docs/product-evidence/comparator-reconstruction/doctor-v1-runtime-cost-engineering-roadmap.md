# Doctor V1 Runtime-Cost Engineering Roadmap

The reconstructed two-order diagnostic supports a V1 runtime cost, while the
current six-order complete product still wins because the reducer contribution
is larger. This is an engineering backlog, not a new performance claim.

## Easy

- Add opt-in admitted, observed, transformed, adapter, fallback, and phase counters.
- Lazy-load runtime support on the first transformed branch.
- Skip runtime initialization when an artifact has no admitted sites.
- Gate admission on expected whole-artifact fixed cost.

## Medium

- Generate adapters only for active SAM/package families.
- Reduce class-loader and native lookup/cache structures.
- Specialize the runtime library per artifact and remove unused modules.

## Hard

- Statically resolve sites that currently require runtime lookup.
- Remove shared runtime support for statically provable transformations.
- Fold safe adapters into existing classes.
- Use measured whole-artifact economics in admission.

Any future runtime claim still requires semantic gates, V2-C confirmation,
V2-D attribution, and post-rewrite activation evidence.
