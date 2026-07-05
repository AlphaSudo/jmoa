# V2-G Doctor Materialization Proof

V2-G materialized a Doctor corrected D2 + V2-E reducer fat JAR in `target/`.
The materialized artifact is not committed to the source repository.

Proof:

```text
BOOT-INF/lib entries in source: 184
BOOT-INF/lib entries replaced: 184
BOOT-INF/lib entries in output: 184
Boot JarLauncher present: true
source SHA-256: 1818b210028987085c782df9e5cd8cfbb159b809966d80d0aa0b458c39569b85
materialized SHA-256: ade17737f9390e18316e2901bae235fda6fa74e51fa64bd34cfa4c14403630f6
```

Boundary:

```text
Do not claim the total fat-JAR byte delta as reducer savings.
ZIP recompression can affect archive size.
The clean reducer claim is the dependency-jar byte delta from the artifact smoke.
```

