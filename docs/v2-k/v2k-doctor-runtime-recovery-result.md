# V2-K Doctor Runtime Recovery Result

The Doctor runtime blocker was revisited with the private phase32 runtime inputs
available locally.

Result:

```text
RECOVERED_AND_SUPERSEDED_BY_CONFIRMATION
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
| Fresh D2 CDS archive | trained successfully for the current runtime |
| Fresh D2R CDS archive | trained successfully for the raw-reduced artifact |

No private paths, generated compose files, secrets, database dumps, raw archives,
or local runtime images are committed.

## Artifact Hashes

| Artifact | SHA-256 |
| --- | --- |
| Corrected D2 jar | `1818B210028987085C782DF9E5CD8CFBB159B809966D80D0AA0B458C39569B85` |
| Raw-reduced D2R jar | `9D00877C0AF90E02B0C8D812F8BC659297CA67D6A939016CAF96D3D5CED79742` |
| Fresh D2 CDS archive | `6FE999095F8800D3B820B87D60239EDD2217E730B5E75F9328737670EF6B8E3B` |
| Fresh D2R CDS archive | `64A4331695D092148A105ADAA47FEEA0CA46CB0CC561C3289F0413D1A67B6ACC` |

Fresh CDS archive sizes:

```text
D2: 128,237,568 bytes
D2R: 126,636,032 bytes
```

## Runtime Smoke And Confirmation

The recovered stack started successfully and was then promoted through screen and
confirmation:

```text
config server: HTTP 200 /actuator/health
discovery server: HTTP 200 /actuator/health
Doctor D2: health UP
Doctor D2R with fresh CDS: health UP
secured Doctor endpoint: HTTP 200
single runtime screen: passed
V2-C confirmation: CONFIRMED_WIN
V2-D attribution: passed
```

## Claim Boundary

Allowed claim:

```text
Doctor corrected D2R is locally runtime-confirmed against corrected D2 with
recovered private runtime inputs, rebuilt support images, and fresh
variant-specific CDS archives.
```

Not allowed:

```text
public reproducibility
all Doctor deployments
all fat-JAR services
all CDS/AppCDS modes
startup win
generated-class mutation
```
