# V2-K Doctor Runtime Materialization Proof Result

Status:

```text
PASSED
```

V2-K recovered runnable Doctor D2 and D2R images and verified the runtime
materialization boundary before and during measurement.

Proofs:

```text
D2 image app jar SHA-256:
1818B210028987085C782DF9E5CD8CFBB159B809966D80D0AA0B458C39569B85

D2R image app jar SHA-256:
9D00877C0AF90E02B0C8D812F8BC659297CA67D6A939016CAF96D3D5CED79742

D2 fresh CDS archive SHA-256:
6FE999095F8800D3B820B87D60239EDD2217E730B5E75F9328737670EF6B8E3B

D2R fresh CDS archive SHA-256:
64A4331695D092148A105ADAA47FEEA0CA46CB0CC561C3289F0413D1A67B6ACC

candidate BOOT-INF/lib entries replaced: 184/184
runtime CDS map proof: present for every measured run
class-load logging during memory pairs: disabled
```

Important boundary:

```text
The confirmation uses image app-jar hashes, fresh variant-specific CDS archives,
runtime /proc maps for CDS mapping, and the artifact-level 184/184 BOOT-INF/lib
replacement proof. Class-load logging was intentionally not enabled during memory
pairs because it is perturbing evidence.
```
