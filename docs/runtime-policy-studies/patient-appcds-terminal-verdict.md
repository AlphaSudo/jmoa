# Patient Dynamic AppCDS Terminal Verdict

Status: `SINGLE_REPLICA_ARCHIVE_REGRESSION`

Both predeclared archive profiles regressed in A/B balanced-order fixed-artifact screens. The study stops before V2 screens, profile selection, holdout C, or final V1-to-V2 AppCDS confirmation because neither profile can satisfy the required V1-and-V2 admission rule.

Product decision: reject Patient dynamic application CDS. This post-V2 study does not alter the released Patient no-CDS result, the later stock-base-CDS confirmation, or Doctor's service-specific application-CDS result.

No multi-replica claim was tested. No AOT cache mechanism was used.

The one later, materially different
[extracted-layout common-class study](patient-extracted-common-appcds-study.md)
also failed balanced fixed-artifact admission and issued the permanent
`PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED` stop. No V1-to-V2 APP comparison was
allowed.
