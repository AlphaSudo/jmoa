# Patient Extracted-Layout Common-Class AppCDS Study

Status: `PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED`

This was the one authorized post-V2 attempt materially different from the
rejected broad fat-JAR archive study. The accepted Patient V1 and V2 bytecode
artifacts were unchanged. Both were materialized as Spring Boot extracted
layouts and launched on explicit, frozen 164-entry classpaths.

## Method Correction

Java 26 does not use `SharedClassListFile` as a filter for
`ArchiveClassesAtExit`. A direct probe showed the option was ignored. A
small deterministic preloader therefore loaded the single predeclared common
set without class initialization. It was present on both BASE and APP
classpaths, allowing separate V1/V2 dynamic top archives over the same stock
JDK base archive without changing application or dependency bytes.

- Predeclared common profiles: `1`
- Requested common classes: `13410`
- Normalized dynamic archived classes: `14511`
- AOT cache: not used
- Runtime javaagent: absent

## Archive Reproducibility

| Artifact | A/B class Jaccard | Size delta | Passed |
| --- | ---: | ---: | --- |
| V1 | 1 | 0.0038% | True |
| V2 | 1 | 0.0039% | True |

Both variants met the declared `>= 0.999` class-set and `<= 1%` archive-size
requirements. Runtime archive mapped-PSS variance was below `0.1%`.

## Balanced Fixed-Artifact Results

| Artifact | Copy | Order | APP-BASE PSS | APP-BASE Private_Dirty | APP-BASE memory.current | APP archive mapped PSS |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| V1 | A | BASE_TO_APP | 3041 KB | -16520 KB | 90263552 B | 102244 KB |
| V1 | B | APP_TO_BASE | 12492 KB | -7332 KB | 100868096 B | 102152 KB |
| V2 | A | BASE_TO_APP | 6090 KB | -13108 KB | 93233152 B | 99424 KB |
| V2 | B | APP_TO_BASE | -3100 KB | -22576 KB | 82190336 B | 99424 KB |

| Artifact | Median APP-BASE PSS | Median APP-BASE memory.current | Direction stable | Admission |
| --- | ---: | ---: | --- | --- |
| V1 | 7766 KB | 95565824 B | True | `REJECTED` |
| V2 | 1495 KB | 87711744 B | False | `REJECTED` |

All eight JVM runs were valid and all `4,800` requests completed with zero
errors. V1 regressed PSS and `memory.current` in both orders. V2 had an
order-sensitive PSS result, while `memory.current` regressed by more than
82 MB in both orders. Neither artifact satisfies the `<= +1 MiB` fixed-
artifact admission gate.

## Terminal Decision

The V1-to-V2 APP screen and confirmation were not run because the fixed-
artifact gate failed first. Patient application CDS remains blocked. Patient's
later stock-base-CDS and no-CDS confirmations are separate policy results.

No further single-replica Patient AppCDS work is authorized. This does not
alter the accepted Patient V1-to-V2 no-CDS median PSS win, does not contradict
Doctor's artifact-specific CDS result, and does not make a universal claim
about CDS. The later `JDK_BASE_CDS_LOW_DIRTY` confirmation used only the stock
JDK base archive and therefore does not reopen this application-archive study.

References: [Java 26 launcher/CDS options](https://docs.oracle.com/en/java/javase/26/docs/specs/man/java.html),
[Spring Boot efficient deployments](https://docs.spring.io/spring-boot/reference/packaging/efficient.html).
