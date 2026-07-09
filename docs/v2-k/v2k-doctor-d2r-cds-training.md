# V2-K Doctor D2R CDS Training

Preferred policy:

```text
D2 + corrected D2 CDS
vs
D2R + freshly trained D2R CDS
```

Current status:

```text
BLOCKED
D2R_CDS_NOT_TRAINED
```

The existing corrected D2 CDS archive is present, but it belongs to the
non-reduced D2 artifact. It must not be reused with the D2 + raw reducer
candidate.

## Required Proof

Before Doctor semantic smoke under CDS mode, record:

```text
D2R CDS archive exists
D2R CDS archive SHA-256 is recorded
training run used the D2 + raw reducer artifact
runtime command references the D2R CDS archive
old corrected D2 CDS archive is not used by the candidate
```

If D2R CDS retraining remains blocked after image/config/database recovery, the
only allowed fallback is an explicitly labeled no-CDS diagnostic:

```text
D2 no-CDS
vs
D2R no-CDS
```

That diagnostic must not be compared to historical Doctor CDS evidence.
