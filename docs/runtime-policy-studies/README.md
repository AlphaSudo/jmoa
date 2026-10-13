# Runtime policy studies

The studies in this directory are historical, protocol-specific CDS/AppCDS
experiments. Their measured values and rejection decisions remain unchanged.
They are not current global policy recommendations.

Current stable 2.1 PetClinic is a no-CDS, packaging-inclusive deployment result.
Provisional 2.15 Patient uses Java 26/Leyden with separate artifact-specific
caches. It observed 8.38 MiB lower median stopped-process PSS, conditional lower
bound 8.19 MiB; original calibration failed with an approved exception, and
performance bounds remain unqualified or failed. No new Maven binaries ship.

- [Runtime policy model](../architecture/runtime-policy-model.md)
- [Current release boundaries](../releases/current-state.md)
- [Historical Patient AppCDS rejection](../decisions/patient-appcds-rejection.md)

An older rejected dynamic archive does not reject Leyden generally; a later
scoped observation does not retrospectively qualify the older archive.
