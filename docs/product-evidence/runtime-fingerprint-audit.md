# Runtime Fingerprint Audit

The reconciliation workflow captured the current host before further product
measurement. The fingerprint is intentionally sanitized; the private audit
record retains exact paths and command output under `target/`.

| Component | Observed state |
|---|---|
| Source revision at audit start | `adc6a2318227e597c16a82b9d995d18a1b90dbdc` |
| `JAVA_HOME` toolchain | JDK 17 |
| `PATH` `java` | OpenJDK 26 |
| `PATH` `javac` | OpenJDK 26 |
| Bare Maven command | Not available in the current shell |
| Podman | 5.7.1 |

This is a real environment split: scripts that honor `JAVA_HOME` can use JDK
17 while direct `java`/`javac` invocations use JDK 26. The audited command
wrapper records both and hashes its inputs and outputs. Future confirmation
runs must select one explicit toolchain instead of inheriting this ambiguity.
