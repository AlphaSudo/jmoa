# Week 2 Publication Checklist

## Completed By Agent

- [x] Created clean public source repo tree.
- [x] Copied publish-safe plugin/runtime source into the clean tree.
- [x] Excluded private HMS, patient-service, and doctor-service source.
- [x] Excluded phase folders, generated evidence, logs, and binaries.
- [x] Sanitized known private patient-service debug code from the public copy.
- [x] Added repository README.
- [x] Added docs for architecture, materialization, runtime-origin verification,
      no-CDS policy, measurement methodology, and limitations.
- [x] Added PetClinic no-CDS reproduction scaffold.
- [x] Added safety scanner.
- [x] Added GitHub Actions build workflow.
- [x] Safety scan passed.
- [x] Default plugin/runtime Maven build passed.

## User Decisions Still Required

- [ ] Choose license, recommended Apache-2.0 if open-source adoption is desired.
- [ ] Review source inventory and private exclusion list.
- [ ] Decide whether to make the source repo public immediately.
- [ ] Decide whether to rename packages from `com.yourorg.jmoa` later.

## Not Included In v0.1

- private HMS services,
- raw measurements,
- generated optimized jars,
- full standalone deployment materializer CLI,
- full standalone runtime-origin verifier CLI,
- Podman integration tests in CI.
