# V2 Release Blockers

Decision: `READY_AFTER_LISTED_BLOCKERS`.

## P0 Blockers

| ID | Blocker | Evidence | Required Fix |
| --- | --- | --- | --- |
| `FINAL_PUBLIC_B0_V2_NOT_CONFIRMED` | Fresh direct public customers `B0 -> V2` run failed the release performance gate. | `docs/v2-final/v2-final-performance-acceptance.md` | Rerun or fix the final public V2 product artifact/protocol until direct `B0 -> V2` confirmation passes, or remove the V2-over-baseline performance headline from the release. |
| `PUBLIC_GOLDEN_PATH_NOT_CLEAN_CLONE_PROVEN` | Current PetClinic examples are scaffolds and still require local phase artifacts for full claim reproduction. | `examples/spring-petclinic-customers-nocds` | Build one clean public quickstart that reaches artifact reduction, byte audit, materialization proof, and semantic smoke from a fresh clone. |
| `DISTRIBUTION_ROUTE_UNDECIDED` | The project still uses `1.0.0-SNAPSHOT`; no Maven Central/GitHub Packages/GitHub Release artifact policy is frozen. | `pom.xml` | Choose distribution route and produce release artifact/checksum process. |

## P1 Blockers

| ID | Blocker | Required Fix |
| --- | --- | --- |
| `SCHEMA_FREEZE_INCOMPLETE` | Freeze schema names/versions and add compatibility fixture inventory before `v2.0.0-rc1`. |
| `GOVERNANCE_FILES_INCOMPLETE` | Add or explicitly defer `NOTICE`, `CODE_OF_CONDUCT.md`, `SUPPORT.md`, SBOM, signing/checksum policy. |
| `CLEAN_MACHINE_QA_PENDING` | Run clean Windows clone qualification; run Linux only if Linux support is claimed. |

Resolved in this final-audit branch:

- `STALE_RELEASE_DOCS`: README, roadmap, claim register, and closure taxonomy now
  point to V2-W plus the V2 final audit and block the failed direct `B0 -> V2`
  product headline.

## P2 / V3 Items

- Generated-family mutation.
- Application-class runtime promotion.
- Large-method splitting.
- Constant-pool and `BootstrapMethods` reducers.
- Full JFR/async-profiler/JOL attribution pipeline.
- Broad AppCDS/Leyden/Kubernetes matrix.
- Dashboard and additional benchmark services.
