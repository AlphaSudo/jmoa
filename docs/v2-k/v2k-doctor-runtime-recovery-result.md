# V2-K Doctor Runtime Recovery Result

The Doctor runtime blocker was revisited with the private phase32 runtime inputs
available locally.

Result:

```text
RECOVERED_SEMANTIC_SMOKE_READY_FOR_SCREEN
```

This supersedes the earlier `v0.8.4` blocked status. The older blocked reports
remain in the repo as historical state, but they are no longer the current
Doctor runtime state.

## Recovered Inputs

The recovery run located or rebuilt the required runtime pieces:

| Requirement | Result |
| --- | --- |
| Private config repository | found locally and mounted into the generated runtime compose |
| Doctor DB init SQL | found locally and mounted into Postgres init |
| Java 26 runtime image | pulled locally |
| Postgres runtime image | pulled locally |
| Config runtime image | rebuilt locally from existing built jar |
| Discovery runtime image | rebuilt locally from existing built jar |
| Doctor D2 runtime image | rebuilt locally from corrected D2 jar |
| Doctor D2R runtime image | rebuilt locally from raw-reduced D2R jar |
| Fresh D2R CDS archive | trained successfully |

No private paths, generated compose files, secrets, database dumps, raw archives,
or local runtime images are committed.

## Artifact Hashes

| Artifact | SHA-256 |
| --- | --- |
| Corrected D2 jar | `1818B210028987085C782DF9E5CD8CFBB159B809966D80D0AA0B458C39569B85` |
| Raw-reduced D2R jar | `9D00877C0AF90E02B0C8D812F8BC659297CA67D6A939016CAF96D3D5CED79742` |
| Fresh D2R CDS archive | `64A4331695D092148A105ADAA47FEEA0CA46CB0CC561C3289F0413D1A67B6ACC` |

Fresh D2R CDS archive size:

```text
126,636,032 bytes
```

## Runtime Smoke

The recovered stack started successfully:

```text
config server: HTTP 200 /actuator/health
discovery server: HTTP 200 /actuator/health
Doctor D2R: HTTP 200 /actuator/health
Doctor D2R with fresh CDS: HTTP 200 /actuator/health
secured Doctor endpoint: HTTP 200 with local admin test token
```

The secured endpoint response exercised the database-backed Doctor API after the
private DB init SQL had populated seed data.

## CDS Policy

The old corrected D2 CDS archive was not reused for D2R.

The recovery run trained a fresh D2R dynamic CDS archive using the raw-reduced
D2R jar, then restarted Doctor D2R with:

```text
-XX:SharedArchiveFile=/opt/leyden/d2r-doctor-management.jsa
```

The archive-backed D2R container reached health `UP` and served the secured
Doctor endpoint.

## Claim Boundary

This is a semantic/runtime recovery result, not a memory result.

Allowed claim:

```text
Doctor corrected D2R is locally runnable with recovered private runtime inputs,
rebuilt support images, a freshly trained D2R CDS archive, and semantic smoke.
```

Not allowed:

```text
Doctor runtime memory win
Doctor startup win
Doctor V2-C confirmation
Doctor V2-D attribution
cross-service runtime generalization
```

## Next Gate

The next executable Doctor gate is:

```text
Doctor corrected D2
vs
Doctor corrected D2 + raw reducer + freshly trained D2R CDS
```

Run a single-screen measurement first. If it passes, proceed to 3-pair V2-C
confirmation and V2-D attribution.
