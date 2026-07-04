# V2-F PetClinic Hardened Artifact Smoke

This smoke reruns the V2-E reducer on the PetClinic full P2 dependency surface after adding V2-F safety handling.

```text
source: Phase 33M full P2 exploded Boot dependency libs
runtime claim: false
```

Result:

```text
jars processed: 162
classes scanned: 54,196
classes reduced: 23,680
original jar bytes: 92,466,274
reduced jar bytes: 88,610,904
removed jar bytes: 3,870,720
signed jars skipped: 1
multi-release jars skipped: 20
sealed jars skipped: 1
BootstrapMethods classes skipped: 6,029
manifest artifacts: 162
```

The reduced byte count is lower than the original V2-E artifact smoke because V2-F skips signed, multi-release, and sealed jars by default.

