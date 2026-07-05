# V2-G Doctor Artifact Smoke

Target:

```text
Doctor corrected D2
```

Comparison:

```text
corrected D2 dependency libs
vs
corrected D2 dependency libs + V2-E LVT/LVTT reducer
```

Result:

```text
jars processed: 184
classes scanned: 58,924
classes reduced: 25,181
original dependency jar bytes: 100,970,980
reduced dependency jar bytes: 96,831,060
removed dependency jar bytes: 4,156,014
signed jars skipped: 1
multi-release jars skipped: 23
sealed jars skipped: 0
BootstrapMethods classes skipped: 6,761
manifest artifacts: 184
manifest artifacts with hashes: 184
```

Artifact gate:

```text
bytes removed > 0: pass
reducer failure: false
jar count stable: pass
manifest generated: pass
hashes recorded: pass
```

Verdict:

```text
artifact gate passed
runtime claim not made
```

This is second-service artifact generalization, not second-service runtime
confirmation.

