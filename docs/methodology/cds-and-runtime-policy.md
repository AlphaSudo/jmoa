# CDS And Runtime Policy

CDS policy is evaluated as part of a concrete service deployment, not as a JVM
feature with a universal expected sign.

## Three Different States

**No-CDS** uses `-Xshare:off` and must show no mapped JSA.

**Stock JDK base CDS** uses the JDK-provided archive. On the accepted Java 26
Patient runtime with compact object headers, the mapped archive is
`classes_coh.jsa`. Both comparison arms must prove the same path and bytes, and
no application archive may be present.

**Application CDS** adds an artifact-specific application archive over the
base archive. Archive training and runtime use must be bound to the same
artifact SHA. Reusing a comparator archive with a transformed artifact is an
invalid comparison.

## Observed Service Decisions

- PetClinic customers won under no-CDS exploded Boot.
- Doctor won under fat-JAR application CDS with separately trained archives.
- Patient won under stock base CDS and independently under no-CDS.
- Patient dynamic application archives regressed or failed admission under the
  tested single-replica policies.

These outcomes show why packaging, class origin, allocator policy, archive
identity, and page-touch behavior must be measured together. "CDS enabled" is
not precise enough for an evidence record.

## Claim Discipline

A policy result transfers neither to another service nor to another policy for
the same service. Diagnostic archive screens do not become V1-to-V2 claims.
Class-load logging used to prove origins is captured separately from official
memory pairs when its file and cgroup effects could perturb measurement.
