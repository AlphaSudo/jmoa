# Application Reducer Rejection

Decision: application-class LVT/LVTT mutation is not promoted as a runtime V2
feature.

The visits-service candidate was artifact-safe and passed semantic smoke, but
removed only 480 bytes from four classes. Three-pair confirmation produced
`1/3` wins and median PSS `+5,732 KB`. The candidate is low ROI and runtime
promotion is blocked. Dependency raw reduction remains the shipped reducer
scope.
