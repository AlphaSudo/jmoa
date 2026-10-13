# Application Reducer Rejection

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

Decision: application-class LVT/LVTT mutation is not promoted as a runtime V2
feature.

The visits-service candidate was artifact-safe and passed semantic smoke, but
removed only 480 bytes from four classes. Three-pair confirmation produced
`1/3` wins and median PSS `+5,732 KB`. The candidate is low ROI and runtime
promotion is blocked. Dependency raw reduction remains the shipped reducer
scope.
