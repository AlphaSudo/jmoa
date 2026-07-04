# V2-F Doctor Artifact Smoke

This smoke ran the V2-F-hardened reducer against the corrected Doctor D2 fat-JAR dependency surface.

```text
runtime claim: false
semantic smoke: not run
V2-C confirmation: not run
V2-D attribution: not run
```

Result:

```text
jars processed: 184
classes scanned: 58,924
classes reduced: 25,181
original jar bytes: 100,970,980
reduced jar bytes: 96,831,060
removed jar bytes: 4,156,014
signed jars detected: 1
multi-release jars detected: 24
sealed jars detected: 0
signed jars skipped: 1
multi-release jars skipped: 23
sealed jars skipped: 0
BootstrapMethods classes skipped: 6,761
manifest artifacts: 184
```

Verdict:

```text
artifact smoke passed
runtime memory claim not made
```

The Doctor raw runtime evidence remains outside this public source repo. This result should be described only as a second-service artifact-level reducer smoke.

