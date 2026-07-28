# PetClinic B0/V1/V2 Artifact Freeze

This freeze defines the three immutable PetClinic customers-service variants used by the balanced direct campaign.

| Variant | Input | Dependency JARs | Dependency bytes | Runtime image |
|---|---|---:|---:|---|
| B0 | strict non-JMOA Spring Boot artifact | 161 | 92,299,443 | `deeca441bda0dac1a64cd9b6f26597ca7c2136de3050179199edeb4472139701` |
| V1 | accepted JMOA V1 artifact | 162 | 92,466,274 | `79b29f0c3f98068cbd09bd77ee25a4f2c2a8507089c02242682bff3763ee620f` |
| V2 | accepted V2 dependency tree | 162 | 88,798,165 | `dabb463d1eced92211f108e8be4a6406a423d02c277f47d611eaf339e6b0b255` |

All variants ran as `EXPLODED_BOOT_APP` with the `NO_CDS_LOW_DIRTY` policy, `MALLOC_ARENA_MAX=1`, CDS disabled, and no runtime javaagent. V1 and V2 used identical application and Spring Boot loader layers; only their dependency layer differed.

The complete command ledgers, local paths, support configuration, and raw runtime evidence remain outside the public repository.
