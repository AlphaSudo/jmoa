# V2-I PetClinic Raw Materialization Proof

The V2-I raw reducer was materialized into the public PetClinic exploded Boot
deployment shape.

```text
service: Spring PetClinic customers-service
launch mode: EXPLODED_BOOT_APP / JarLauncher
BOOT-INF/lib entries replaced: 162/162
same jar count: true
all materialized hashes match reduced jars: true
```

Artifact result:

```text
original dependency jar bytes: 92,466,274
raw dependency jar bytes: 88,798,165
materialized dependency jar delta: -3,668,109 bytes
```

This is an artifact/materialization proof only. Runtime claims come from the
V2-C-confirmed evidence below.
