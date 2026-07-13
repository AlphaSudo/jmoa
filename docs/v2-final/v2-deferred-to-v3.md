# Deferred To V3

The following items are intentionally outside the V2 release scope:

- Generated-family mutation after V2-W discovery-only closure.
- Spring AOT helper rewriting.
- CGLIB, JDK proxy, ByteBuddy, or Hibernate proxy rewriting.
- Application-class reducer runtime promotion after V2-Q failed confirmation.
- Large-method splitting.
- Constant-pool reduction.
- `BootstrapMethods` rewriting or stripping.
- Additional debug/metadata stripping beyond LVT/LVTT.
- Full JFR/async-profiler/JOL attribution.
- Broader AppCDS, Leyden, Kubernetes, and FAST_STARTUP automation.
- Cross-platform polished dashboard.
- Broad benchmark-service matrix.

V3 work should start only after V2 is released or after a concrete P0 blocker
proves that a V3-scoped item is required for release.

