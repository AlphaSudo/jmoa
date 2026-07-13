# V2 Release Readiness

Decision: `READY_AFTER_LISTED_BLOCKERS`.

## Completed

- Full reactor tests can be run locally.
- Publication safety script exists.
- Claim-register consistency guard exists.
- V2-A through V2-W have explicit closure docs.
- Raw reducer, evidence, attribution, recommendation, runtime policy, preflight,
  and workflow layers are implemented.
- Narrow runtime claims are documented with claim boundaries.

## Not Ready For RC

- Fresh final public `B0 -> V2` customers performance gate did not confirm.
- Public PetClinic quickstart is still a scaffold and is not clean-clone
  release qualification.
- Distribution route is not frozen; parent version is still `1.0.0-SNAPSHOT`.
- Schema freeze is incomplete.
- Clean-machine release qualification has not been run.
- Governance/distribution extras (`NOTICE`, `CODE_OF_CONDUCT.md`, `SUPPORT.md`,
  SBOM, signing/checksum policy) are incomplete or undecided.

## Required Next Step

Fix P0 blockers only:

```text
1. Decide whether V2 release headline is performance-first or tooling-first.
2. If performance-first, fix/rerun direct B0 -> V2 until it confirms.
3. Build a clean public quickstart.
4. Freeze distribution route.
```

No new optimizer research should start before these are resolved.

