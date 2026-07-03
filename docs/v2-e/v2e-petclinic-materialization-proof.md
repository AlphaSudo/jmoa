# V2-E PetClinic Materialization Proof

Status: passed.

This proof checks that the V2-E reduced dependency jars were materialized into
the PetClinic `EXPLODED_BOOT_APP` image used for the runtime screen.

## Summary

```text
service: Spring PetClinic customers-service
variant: full P2 + V2-E reducer
launch mode: EXPLODED_BOOT_APP
original dependency jars: 162
reduced dependency jars: 162
missing jars: 0
extra jars: 0
sample hash checks: passed
```

Materialized dependency jar footprint:

```text
original total bytes: 92,466,274
reduced total bytes: 87,070,377
delta bytes: -5,395,897
```

Four sampled dependency jars were compared between the local reduced output and
the image layer, and every sampled hash matched:

```text
HdrHistogram-2.2.2.jar
spring-boot-4.0.1.jar
jackson-databind-2.20.1.jar
guava-14.0.1.jar
```

## Boundary

This is a materialization proof only. It proves the reduced jars were used by the
screen image and that the dependency-layer jar set stayed structurally stable.
It is not a runtime memory or startup claim.
