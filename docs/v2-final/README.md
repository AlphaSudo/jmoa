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
- [Three-service acceptance contract](v2-three-service-acceptance-contract.md)
- [Three-service memory matrix](v2-three-service-memory-matrix.md)
- [Patient final verdict](v2-three-service-patient-verdict.md)
- [Patient bounded root-cause investigation](patient-root-cause-investigation.md)
- [Patient comparator audit](patient-comparator-audit.md)
- [Patient pair attribution](patient-pair-attribution.md)

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
BLOCKED_FINAL_ACCEPTANCE
```

The constrained RC2 public/customer claim remains scoped and valid, but the
frozen three-service final V1-to-V2 launch gate is not passed. PetClinic and
Doctor pass; Patient has 6/6 valid corrected runs but only 1/3 paired wins, a median PSS
delta of +668 KB, and a V2-C mixed-metrics verdict. See the three-service
matrix and Patient verdict above for the exact evidence boundary.
