# V2 Release Blockers

Decision: `READY_FOR_RELEASE_QUALIFICATION`

There are no unresolved P0 blockers. The corrected direct public B0-to-V2 gate
passed, and V2 distribution is frozen to GitHub Release artifacts with SHA-256
checksums.

## P1 Qualification Gates

- `PUBLIC_GOLDEN_PATH_NOT_CLEAN_CLONE_PROVEN`
- `SCHEMA_FREEZE_INCOMPLETE`
- `CLEAN_MACHINE_QA_PENDING`

These are release-engineering gates, not reasons to rerun the accepted
performance experiment. RC tagging remains blocked until they pass.

The complete research/product disposition is recorded in
[V2 unfinished-work disposition](v2-unfinished-work-disposition.md).
