# V2-Q Application ROI Policy

V2-Q proved that ordinary packaged application classes can be admitted to the
raw LVT/LVTT reducer safely at the artifact and semantic layers. It did not
prove that tiny application-class metadata surfaces are worth runtime
promotion.

## Admission Rule

Application-class raw reduction is artifact/semantic-only unless at least one
of these is true:

```text
application removed bytes >= 32 KB
application reduced classes >= 50
service-specific runtime screen passed
```

Below that threshold, `jmoa:recommend-reducer` should not treat application
classes as equivalent to dependency-jar reducer evidence.

## Visits-Service Assessment

```text
application classes scanned: 7
ordinary classes reduced: 4
application bytes removed: 480
raw audits failed: 0
semantic smoke: passed
diagnostic rerun screen: passed
3-pair confirmation: failed
```

Decision:

```text
LOW_ROI
CLOSED_ARTIFACT_SEMANTIC_ONLY
NO_RUNTIME_CLAIM
```

The reducer mechanism remains useful, but this target's application-class
metadata surface is too small to justify runtime promotion.
