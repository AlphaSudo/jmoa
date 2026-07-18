# V2 Release Readiness

Status: `READY_FOR_V2_FINAL`

The reproducible incremental performance gate is green. V2-C and V2-D accepted
and explained the fresh V1-to-V2 public comparison. The direct B0-to-V2
five-pair replication is mixed and explicitly excluded from the RC2 claim.

The final engineering gates passed:

1. remote clean-clone public quickstart with an empty isolated Maven repository;
2. compatibility freeze for six shipped schema families;
3. fresh Ubuntu/JDK 26 GitHub Actions build and publication-safety scan.

The strict release script now rejects both P0 and P1 blockers by default. No
override is required for `2.0.0-rc2`.

The source tag and GitHub release page are public. The release currently has no
uploaded binary or frozen PetClinic-input assets. Local release-bundle tooling
produces SHA-256 manifests, but public documentation must not imply those files
are downloadable until they are uploaded. Maven Central and GitHub Packages
claims are not made.
