# Patient Comparator Audit

Status: **COMPLETE**

The finalized Patient V1 artifact matches the accepted V1 optimizer policy
after normalizing report timestamps and JSON map order.

The audit found one real comparator defect: the first V2 artifact allowed the
JMOA runtime support JAR into reducer scope. The reducer now supports artifact
filename include/exclude globs, and the corrected Patient build excludes that
runtime JAR and verifies it byte-for-byte against V1.

## Corrected Comparator

```text
application classes: 577 in each artifact
embedded dependencies: 162 in each artifact
application entry content differences: 0
Spring Boot loader content differences: 0
runtime support JAR identical: yes
raw-reduced classes audited: 32,616
failed raw preservation audits: 0
unexpected differences: 0
classification: PACKAGING_ONLY_DIFFERENCES_PRESENT
```

The repacked outer fat JAR has ZIP metadata differences, but its non-library
entry content is unchanged. The final Patient confirmation uses this corrected
comparator, so its failed runtime gate is not inherited from the original
runtime-library defect.
