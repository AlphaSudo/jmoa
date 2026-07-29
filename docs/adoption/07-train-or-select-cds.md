# 07 Train Or Select CDS

CDS is part of the runtime policy, not a universal JMOA requirement.

Choose one policy and keep its semantics consistent across B0/V1/V2:

## No CDS

Use `-Xshare:off` and prove no JSA mappings. PetClinic used this mode.

## Stock JDK Base CDS

Use the same stock archive path, SHA, and runtime JDK for all artifacts.
Patient used this mode; no application archive was admitted.

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
