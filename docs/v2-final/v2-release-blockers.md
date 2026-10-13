# V2 Release Blockers

**Historical final-V2 audit record.** Original measurements, statuses and
release-readiness notes below belong to that publication. For current stable
2.1 and provisional 2.15, see [release boundaries](../releases/current-state.md).

Decision: `READY_FOR_RC1`

There are no unresolved P0 blockers for the constrained RC2 claim. Direct
public B0-to-V2 remains explicitly non-claimable because its fresh five-pair
replication was mixed. V2 distribution is frozen to GitHub Release artifacts
with SHA-256 checksums.

## Qualification Gates

There are no unresolved P0 or P1 blockers.

The public path passed from remote commit `973257e` with an empty isolated
Maven repository. GitHub Actions run `29297537251` independently passed on
Ubuntu with JDK 26. The schema registry covers all six shipped report families.

The complete research/product disposition is recorded in
[V2 unfinished-work disposition](v2-unfinished-work-disposition.md).
