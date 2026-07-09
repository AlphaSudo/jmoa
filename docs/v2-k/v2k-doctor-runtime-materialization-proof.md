# V2-K Doctor Runtime Materialization Proof

Status:

```text
NOT_ATTEMPTED
```

Runtime materialization proof is blocked because the Doctor runtime images and
private stack inputs are not available.

Before Doctor semantic smoke, prove:

```text
D2 baseline image exists
D2 baseline image contains the corrected D2 artifact
D2 baseline image uses corrected D2 CDS if CDS mode is selected
D2R candidate image exists
D2R candidate image contains the D2 + raw reducer artifact
D2R candidate image BOOT-INF/lib count is 184
sample BOOT-INF/lib hashes match the reducer manifest
D2R candidate image uses fresh D2R CDS if CDS mode is selected
old non-reduced D2 artifact does not shadow the D2R artifact
runtime command points at the expected artifact and archive
```

No materialization proof means no valid semantic smoke.
