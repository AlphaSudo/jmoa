# V2-K Doctor Raw Materialization Proof

V2-K locally materialized a Doctor corrected D2 + raw reducer fat JAR.

Input:

```text
corrected D2 fat JAR
V2-J raw-reduced dependency libs
```

Output:

```text
Doctor corrected D2 + raw reducer fat JAR under target/
```

Summary:

```text
source fat-JAR bytes: 101,906,981
materialized fat-JAR bytes: 97,989,324
BOOT-INF/lib entries: 184
BOOT-INF/lib entries replaced: 184
missing replacement libs: 0
source SHA-256: 1818b210028987085c782df9e5cd8cfbb159b809966d80d0aa0b458c39569b85
materialized SHA-256: 9d00877c0af90e02b0c8d812f8bc659297ca67d6a939016caf96d3d5ced79742
```

## Boundary

This proves local artifact materialization only.

It does not prove:

```text
runtime image contains this artifact
CDS archive is valid for this artifact
service starts
semantic smoke passes
runtime memory improves
```
