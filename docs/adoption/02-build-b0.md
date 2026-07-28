# 02 Build B0

B0 is a strict no-JMOA build from the same source revision used by V1 and V2.

## Required proof

- JMOA Maven plugin did not execute.
- No JMOA runtime dependency or classes are present.
- No transformed call sites are present.
- No reduced dependency output or reducer manifest is present.
- No runtime Java agent is configured.

Record:

```text
source revision
build command ledger
artifact SHA-256
application class fingerprint
dependency fingerprint
Spring Boot loader fingerprint
container image ID
```

For exploded Boot, hash both the source JAR and the concrete exploded trees. Do not label a logical product identifier as a file or tree SHA unless it was calculated from those bytes.
