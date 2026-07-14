# V2 Release Readiness

Status: `READY_FOR_RC2`

The reproducible incremental performance gate is green. V2-C and V2-D accepted
and explained the fresh V1-to-V2 public comparison. The direct B0-to-V2
five-pair replication is mixed and explicitly excluded from the RC2 claim.

The final engineering gates passed:

1. remote clean-clone public quickstart with an empty isolated Maven repository;
2. compatibility freeze for six shipped schema families;
3. fresh Ubuntu/JDK 26 GitHub Actions build and publication-safety scan.

The strict release script now rejects both P0 and P1 blockers by default. No
override is required for `2.0.0-rc2`.

The distribution route is GitHub Release artifacts plus SHA-256 checksums. A
Maven Central claim is not made.
