# Publishable Source Inventory

Status: draft generated for Week 2 source-release readiness.

## PUBLIC_SAFE

- `jmoa-maven-plugin/src/main/java`
- `jmoa-maven-plugin/src/test/java` after sanitizing test-only local path usage
- `jmoa-runtime-lib/src/main/java`
- `jmoa-runtime-lib/src/test/java`
- `tools/mode-c-launcher`
- public documentation under `docs`
- PetClinic reproduction scaffold under `examples`

## PUBLIC_SAFE_AFTER_SANITIZATION

- `jmoa-maven-plugin/src/main/java/com/yourorg/jmoa/plugin/LambdaDeduplicationMojo.java`
  - removed a private patient-service-specific debug helper from the public copy
- `jmoa-maven-plugin/src/test/java/com/yourorg/jmoa/plugin/weave/LambdaWeavingCoordinatorTest.java`
  - replaced a fake Windows path with a platform-neutral temp path

## PRIVATE_DO_NOT_PUBLISH

- private HMS service source
- patient-service source
- doctor-service source
- private Docker compose files or profiles
- private generated AOT source rooted in internal packages

## GENERATED_DO_NOT_PUBLISH

- `target/`
- `out/`
- generated optimized jars
- generated class files
- measurement outputs

## LARGE_EVIDENCE_DO_NOT_PUBLISH

- JFR
- HProf
- logs
- raw smaps dumps
- CDS archives

## UNKNOWN_REVIEW_MANUALLY

- older root `src/main/java/com/yourorg/optimizer` agent/profiler code
- old planning docs with local paths
- phase folders under `v3.3`
