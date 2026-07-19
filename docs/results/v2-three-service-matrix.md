# V2 Three-Service Matrix

This is the accepted V1-to-final-V2 engineering-evolution matrix. For the
clean no-JMOA buyer comparison, use the
[direct product matrix](../product-evidence/jmoa-vs-no-jmoa-matrix.md).

The final matrix compares each service's accepted V1 artifact with V2 under a
frozen, service-specific runtime policy.

| Service | Comparator | Deployment | Policy | Valid | Wins | Median PSS | Median Private_Dirty | Median memory.current |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| PetClinic customers | Finalized V1 | Exploded Boot | `NO_CDS_LOW_DIRTY` | `6/6` | `2/3` | `-6,012 KB` | `-5,708 KB` | `-8,081,408 B` |
| Doctor | Corrected D2 | Fat JAR | Application CDS | `6/6` | `3/3` | `-5,156 KB` | `-5,212 KB` | `-6,975,488 B` |
| Patient | Accepted V1 | Fat JAR | `JDK_BASE_CDS_LOW_DIRTY` | `6/6` | `3/3` | `-8,279 KB` | `-8,444 KB` | `-8,523,776 B` |

Every row has zero workload and semantic errors, V2-C `CONFIRMED_WIN`, and
V2-D attribution.

## PetClinic Customers

The public comparator is finalized V1, not the original unoptimized baseline.
The service runs through `JarLauncher` from an exploded Boot layout with CDS,
AppCDS, Leyden, and runtime javaagents disabled and
`MALLOC_ARENA_MAX=1`. The corrected workload and cache policy are fixed across
all six runs. The direct historical baseline-to-V2 claim is provenance only;
its fresh five-pair replication was mixed.

V2-D preserves the observed PSS result without reducing it to class count. See
the [public quickstart](../reproduction/petclinic-quickstart.md) for build and
semantic reproduction.

## Doctor

The private comparison is corrected D2 against raw-reduced D2R under a Spring
Boot fat-JAR deployment. Each artifact has a freshly trained, SHA-bound
application CDS archive; old-archive reuse and CDS/no-CDS comparator mismatch
are blocked. V2-D found anonymous writable and mapped-file PSS reductions, with
NMT Class/Metaspace and class-count movement as supporting evidence. Retained
heap-object reduction was not primary.

Private source, configuration, database initialization, runtime images, CDS
archives, and raw evidence are excluded. The result is not public-reproducible
and does not transfer to all Doctor or fat-JAR/CDS deployments.

## Patient

The private comparison uses accepted V1 and corrected V2 fat JARs. Both arms
map the identical stock Java 26 `classes_coh.jsa` bytes and use no Patient
application archive, AOT cache, archive training, or runtime javaagent. The
600-request workload completes with zero errors in every run.

V2-D attributes the primary movement to heap page-touch reduction, supported by
anonymous writable reduction; heap-used and histogram movement are too small to
support a retained-object claim. Patient no-CDS is independently confirmed at
`-8,903 KB` median PSS. Dynamic Patient application CDS remains rejected.

## Claim Boundary

The matrix proves incremental V1-to-V2 wins for these service/artifact/deployment
contracts. It is not a startup claim, a universal Spring Boot claim, or a claim
that one CDS or allocator policy is generally best. The authoritative data is
in [the frozen JSON matrix](../v2-final/v2-three-service-memory-matrix.json).
