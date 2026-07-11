# V2-N Runtime Policy Recommendation Engine

V2-N is a report-only policy admission layer for the existing raw LVT/LVTT
reducer. It recommends, screens, or blocks runtime protocols using existing
artifact, semantic, V2-C, V2-D, and runtime-materialization evidence.

It does not mutate bytecode, build images, train CDS archives, reuse CDS
archives, or create a runtime performance claim.

The relevant runtime context is:

```text
service
launch mode
runtime policy
reducer engine
artifact fingerprint
CDS archive fingerprint when CDS is used
```

CDS recommendations are intentionally stricter than no-CDS recommendations:
the exact artifact/archive pair and measured archive mapping must be proven.
