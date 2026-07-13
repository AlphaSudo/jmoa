# V2 Final Audit

This folder is the release-readiness layer for V2. It freezes scope, audits the
original plan, records the final performance gate, and lists the blockers that
must be cleared before `rc1` or `v2.0.0`.

## Start Here

- [Release scope](v2-release-scope.md)
- [Final performance acceptance](v2-final-performance-acceptance.md)
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

## Current Decision

```text
READY_AFTER_LISTED_BLOCKERS
```

The P0 blocker is the direct public `B0 -> V2` gate: the fresh final artifact
run was valid but not confirmed, so V2 cannot be published with a broad
baseline-over-final-product performance headline yet.
