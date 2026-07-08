# V2-J Raw Byte Preservation Report

V2-J adds a byte-preservation auditor around the raw reducer engine.

For every raw-reduced class, the auditor independently normalizes both class
files by removing only:

```text
LocalVariableTable
LocalVariableTypeTable
```

The normalized original and normalized reduced class bytes must match exactly.
If they do not match, the reducer hard-fails and does not produce a claimable
reduced artifact.

## Doctor Artifact Smoke Result

The audited raw reducer was run against the Doctor corrected D2 dependency
surface from the V2-G artifact smoke.

```text
engine: raw
dependency jars processed: 184
static classes scanned: 58,924
classes reduced and audited: 31,942
failed audits: 0
BootstrapMethods classes skipped: 0
signed jars skipped: 1
multi-release jars skipped: 23
sealed jars skipped: 0
compressed dependency-jar bytes removed: 3,926,870
```

The public-safe committed result is a summary only. Raw target artifacts and
private dependency jars are not committed.

## Boundary

This report proves raw byte-preservation auditing at artifact level. It does not
prove Doctor semantic behavior, Doctor runtime memory behavior, startup impact,
or CDS/AppCDS behavior.
