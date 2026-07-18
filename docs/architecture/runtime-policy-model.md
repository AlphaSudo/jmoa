# Runtime Policy Model

Runtime policy is part of the measured artifact contract. The same bytecode may
behave differently under no-CDS, stock base CDS, or an application archive.

| Policy | Required state | Live proof | Accepted example |
| --- | --- | --- | --- |
| `NO_CDS_LOW_DIRTY` | `-Xshare:off`, `MALLOC_ARENA_MAX=1`, no AppCDS/AOT/javaagent | No JSA mapping | PetClinic customers; Patient secondary result |
| `JDK_BASE_CDS_LOW_DIRTY` | `-Xshare:on` or default sharing, `MALLOC_ARENA_MAX=1`, no application archive/AOT/javaagent | Exactly one stock JDK archive; identical archive identity between arms | Patient primary final result |
| `APPLICATION_CDS` | Sharing enabled and a variant-specific application archive | Registered artifact/archive SHA pair plus base and application mappings | Doctor |
| `DIAGNOSTIC` | Explicit diagnostic flags | Flags and perturbations recorded | JFR, class-load, NMT-detail, forced-GC studies |

## Policy Rules

`NO_CDS_LOW_DIRTY` and `JDK_BASE_CDS_LOW_DIRTY` forbid a runtime javaagent and
require the low-dirty allocator setting used by their accepted protocols.
Application CDS requires a newly trained archive for each artifact; an archive
from the comparator may not be reused for a transformed candidate.

Diagnostic runs can explain behavior but are not claim runs. Class-load logs,
JFR, NMT detail, forced GC, altered tiered compilation, or large in-container
logs are marked as perturbations by the evidence engine.

## Current Service Decisions

- PetClinic customers: no-CDS confirmed.
- Doctor: application CDS confirmed with separate D2 and D2R archives.
- Patient: stock JDK base CDS confirmed; no-CDS independently confirmed;
  dynamic Patient application CDS rejected for the tested single-replica shape.

These decisions do not establish a global preference. They establish that
policy selection, archive identity, and deployment shape must be measured and
proven per service.
