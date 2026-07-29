# V1 Overhead Product-Engineering Roadmap

The unified three-artifact campaign moved the primary engineering question to
the `B0 -> V1` leg. No new benchmark is authorized by this roadmap.

## Easy

- Keep current and historical claims separated in the claim register.
- Emit accepted logical-site and rewritten-event manifests with artifact hashes.
- Add diagnostic per-site activation counters behind an explicit non-claim mode.
- Show runtime-library, adapter, metadata, and class-loading estimates in the
  optimizer report before admission.

## Medium

- Lazy-initialize V1 runtime support.
- Generate only adapter package/SAM families required by admitted sites.
- Add profile-based admission that rejects sites without representative
  mechanism activation.
- Productize clean-B0 reducer-only mode independently from full optimization.
- Add dynamic origin and activation verification to the public workflow.

## Hard

- Replace shared runtime lookup for statically resolvable sites.
- Fold safe adapters into existing classes without breaking access boundaries.
- Redesign package adapter generation around whole-artifact cost.
- Specialize admission using representative workload activation while keeping
  the measurement campaign independent.

## Promotion Gate

A V1 engineering change is promotable only after semantic safety, direct
`B0 -> V1` cost screening, direct `B0 -> V2` confirmation, V2-C validation, and
V2-D attribution. Incremental reducer savings cannot hide an unexplained V1
regression.
