# 07 Train Or Select CDS

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

CDS is part of the runtime policy, not a universal JMOA requirement.

Choose one policy and keep its semantics consistent across B0/V1/V2:

## No CDS

Use `-Xshare:off` and prove no JSA mappings. PetClinic used this mode.

## Stock JDK Base CDS

Use the same stock archive path, SHA, and runtime JDK for all artifacts.
The historical Patient stock-base study used this mode. It is separate from
the 2.15 Leyden benchmark.

## Application CDS

Train one archive per artifact with the exact runtime JDK, classpath, launch
mode, and workload. Doctor used distinct B0, V1, and V2 archives. Reusing the
V1 archive with V2 would be an identity defect.

Record:

```text
JDK fingerprint
archive SHA and size
training command/workload
artifact/classpath fingerprint
runtime mapping proof
default archive identity
application archive identity
```

## Gate

Proceed only when every session's runtime-policy proof passes. A different
archive path is acceptable for artifact-specific AppCDS; a mismatched archive
identity is not.


## Leyden in the provisional Patient benchmark

Record the exact JDK, application hash, training/classpath identity and each
artifact's own AOT-cache SHA-256. Rebuild the cache after transforming bytes.
The 2.15 observation retains ambient cache and actual paging; startup and primary
warmth are unqualified. A cache file being present is insufficient proof of its
runtime use or of qualified performance.
