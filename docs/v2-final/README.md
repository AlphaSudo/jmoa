# V2 Final Audit

This folder is the release-readiness layer for V2. It freezes scope, records the
passed final performance gates, disposes every research item, and tracks the
remaining release-qualification work before `rc1`, `rc2`, or `v2.0.0`.

## Start Here

- [Release scope](v2-release-scope.md)
- [Final performance acceptance](v2-final-performance-acceptance.md)
- [Final memory-win matrix](v2-final-memory-win-matrix.md)
- [Unfinished-work disposition](v2-unfinished-work-disposition.md)
- [Release blockers](v2-release-blockers.md)
- [Release readiness](v2-release-readiness.md)

## Audit Records

- [Original plan coverage](v2-original-plan-coverage.md)
- [Five questions final answer](v2-five-questions-final-answer.md)
- [Phase closure audit](v2-phase-closure-audit.md)
- [Failure resolution register](v2-failure-resolution-register.md)
- [Capability matrix](v2-capability-matrix.md)
- [Claim matrix](v2-claim-matrix.md)
- [Deferred to V3](v2-deferred-to-v3.md)

## Publication Prep

- [Schema freeze](v2-schema-freeze.md)
- [Public reproduction plan](v2-public-reproduction-plan.md)
- [Public quickstart qualification](v2-public-quickstart-qualification.md)
- [Clean-clone RC qualification](v2-clean-clone-qualification.md)
- [Distribution](v2-distribution.md)
- [RC2 downloaded-asset qualification](v2-rc2-consumer-qualification.md)

## Current Decision

```text
READY_FOR_RC2
```

There are no unresolved P0 or P1 blockers for the constrained RC2 claim. The
remote clean-clone quickstart, schema freeze, and independent GitHub Actions
build all passed. The direct B0-to-V2 result is not an RC2 claim; see the
five-pair replication report for the exact boundary.
