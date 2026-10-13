# JMOA 2.15 - scoped Patient RAM benchmark

This provisional **evidence release** reports a direct Patient comparison under Java 26/Leyden: **8.38 MiB lower median stopped-process PSS** after the defined workload across 12 paired blocks (24 fresh units). The conditional lower saving bound is **8.19 MiB**.

**Calibration required a user-approved exception after the original controls failed; performance bounds remain unqualified or failed.** Startup and primary cache warmth, whole-stack/cold/production parity, live ML and parity with the uploaded JMOA 2.1 binary are not established.

- All 12 paired RAM results and all 82 cost metrics are disclosed.
- Both arms used the same native heap trimming and allocator limit.
- Exact tested application, cache, core and JRE hashes are recorded.
- All 96 campaign attempts remain counted, including failures; no historical medians were added.
- Original infrastructure was restored. Saved raw evidence was independently reanalysed, which is not external replication.

## Release contents

Human- and machine-readable benchmark records, this scope statement, a commit-bound evidence manifest and SHA-256 checksums. **No new plugin/runtime JARs or Maven version bump are included.** The version 2.15 identifies this evidence publication; the existing stable 2.1.0 binaries and PetClinic acceptance are unchanged. This release is marked provisional/prerelease and does not replace the stable release.

[Full benchmark and limitations](https://github.com/AlphaSudo/jmoa/blob/v2.15/docs/product-evidence/patient-plan133-scoped-ram-result.md) | [Machine-readable data](https://github.com/AlphaSudo/jmoa/blob/v2.15/docs/product-evidence/patient-plan133-scoped-ram-result.json)

The next investigation is Plan 1.34: seek a fresh >=8 MiB result with passing calibration, qualified costs, explicit cache/pressure scope and proved release-to-test artifact lineage. These remain future work.
