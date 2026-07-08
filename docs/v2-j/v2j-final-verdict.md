# V2-J Final Verdict

V2-J productizes the V2-I raw reducer engine as an auditable artifact reducer.

## Closed As

```text
raw reducer byte-preservation auditor
manifest v2 with per-class raw preservation records
stronger raw reducer verifier tests
Doctor corrected D2 raw artifact smoke
Doctor runtime unblock plan
public second runtime target selection
```

## Result

```text
auditor status: passed
Doctor artifact smoke: passed
Doctor runtime: blocked
new runtime claim: false
new reducer type: false
```

Doctor artifact smoke:

```text
jars processed: 184
classes scanned: 58,924
classes reduced: 31,942
raw byte-preservation audits: 31,942
failed audits: 0
compressed dependency-jar bytes removed: 3,926,870
```

## Claim Boundary

V2-J does not transfer the V2-I PetClinic runtime win to Doctor, fat-JAR mode,
CDS/AppCDS mode, startup performance, or cross-service generalization.

Recommended tag after merge:

```text
v0.8.1-v2j-raw-engine-productized
```

Next recommended phase:

```text
V2-K — public second runtime target screen
```
