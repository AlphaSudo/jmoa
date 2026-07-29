# 01 Choose Service And Policy

Choose a service with a reproducible build, a deterministic semantic workload,
and a runtime topology you can rebuild without private guesswork.

Freeze these choices before building:

```text
service and source revision
JDK vendor/build
launch mode
GC, heap, stack, and code-cache flags
CDS policy
allocator environment
support-service topology
workload and state-reset method
warmup, settle, and capture order
```

Use the deployment mode the service actually ships. Doctor used a Spring Boot
fat JAR with artifact-specific AppCDS. Patient used a fat JAR with the same
stock JDK base archive for every arm. PetClinic used exploded Boot with CDS
off, SerialGC, and `MALLOC_ARENA_MAX=1`.

Do not copy Doctor's policy into another service because Doctor won. Packaging,
CDS, and page-touch behavior are service-specific.

## Gate

Proceed only when:

- source and runtime dependencies are available;
- the target and required support services can start from a fresh session;
- the workload reports exact request count, status, and errors;
- memory captures can run without JFR, class-load logging, or forced GC;
- every external command can be written to a scenario command ledger.

If any prerequisite is unavailable, classify the service as blocked. Do not
replace missing runtime proof with a portfolio summary.
