# V2 Final Audit

This folder is the release-readiness layer for V2. It freezes scope, records the
passed final performance gates, and preserves the qualification trail that led
to the stable `v2.0.0` release.

## Start Here

- [Release scope](v2-release-scope.md)
- [Final performance acceptance](v2-final-performance-acceptance.md)
- [Final memory-win matrix](v2-final-memory-win-matrix.md)
- [Unfinished-work disposition](v2-unfinished-work-disposition.md)
- [Release blockers](v2-release-blockers.md)
- [Release readiness](v2-release-readiness.md)
- [Three-service acceptance contract](v2-three-service-acceptance-contract.md)
- [Three-service memory matrix](v2-three-service-memory-matrix.md)
- [Patient final policy verdict](patient-final-policy-verdict.md)
- [Patient no-CDS confirmation](patient-nocds-confirmation.md)
- [Patient CDS final verdict](patient-cds-final-verdict.md)
- [Patient historical CDS verdict](v2-three-service-patient-verdict.md)
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
READY_FOR_V2_FINAL
```

The constrained RC2 public/customer claim remains scoped and valid, but the
frozen three-service final V1-to-V2 launch gate now passes under
service-specific confirmed runtime policies: PetClinic no-CDS, Doctor CDS, and
Patient no-CDS. Patient's separate corrected CDS confirmation remains blocked
(1/3 paired wins, median PSS `+668 KB`) and is preserved as a policy-specific
failure. See the three-service matrix, Patient policy verdict, and separate
CDS/no-CDS records above for the exact evidence boundary.

Stable `v2.0.0` was published on July 16, 2026. The later
[Patient Dynamic AppCDS Archive Economics Study](../runtime-policy-studies/patient-dynamic-appcds-study.md)
is a non-blocking post-release policy exception study and does not reopen this
launch decision.
