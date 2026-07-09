# V2-K Doctor CDS Policy

The existing corrected D2 CDS archive was trained for the non-reduced D2
artifact. It must not be reused with the D2 + raw reducer artifact.

## Preferred Policy

```text
D2 baseline:
  corrected D2 artifact
  corrected D2 CDS archive

D2R candidate:
  corrected D2 + raw reducer artifact
  freshly trained D2R CDS archive
```

This is the preferred path because it preserves the historical Doctor runtime
mode and avoids comparing unlike policies.

## Allowed Diagnostic Policy

```text
D2 no-CDS
vs
D2R no-CDS
```

This is allowed only as a diagnostic path. It is not comparable to the
historical Doctor corrected fat-JAR/CDS result.

## Forbidden Comparisons

```text
old D2 CDS archive reused with D2R raw-reduced artifact
historical D2 CDS result compared to new D2R no-CDS result
CDS baseline compared to no-CDS candidate
artifact smoke described as runtime evidence
```

## Current Decision

```text
CDS_POLICY_UNDECIDED
```

Recommendation:

```text
Try fresh D2R CDS retraining first.
If retraining is blocked, run an explicitly labeled no-CDS diagnostic screen.
If the private stack cannot be rebuilt, keep Doctor runtime blocked and proceed
with the public visits-service fallback.
```
