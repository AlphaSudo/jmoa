# V2-E PetClinic Service Smoke

This is a single semantic smoke for the V2-E reduced PetClinic artifact. It is
not a memory confirmation and does not support a runtime performance claim.

## Input

```text
service: Spring PetClinic customers-service
artifact shape: full P2 plus V2-E reduced dependency jars
launch mode: EXPLODED_BOOT_APP
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden: disabled
runtime javaagent: absent
```

## Result

```text
health: UP
workload requests: 24
workload errors: 0
JVM linkage/class errors detected in sampled logs: false
semantic smoke: passed
```

## Claim Boundary

This smoke says the reduced dependency artifact can start and serve the
representative PetClinic workload once. It does not compare memory, startup, or
PSS against full P2.

The next gate is a P2 versus P2+Reducer single-screen memory run. Promotion to a
performance claim requires V2-C confirmation and V2-D attribution.
