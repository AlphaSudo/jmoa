# Current release and claim boundaries

Updated 2026-10-13. Release numbers identify different publication scopes.

| Release | Distribution | Result | Qualification |
| --- | --- | --- | --- |
| **2.1 / v2.1.0** | Stable Maven plugin and runtime artifacts through GitHub Releases | PetClinic: **14.88 MiB lower process PSS**, **16.24 MiB lower target-cgroup memory** | Accepted exact whole-deployment comparison; packaging-inclusive; frozen cost gates passed |
| **2.15 / v2.15** | Provisional benchmark evidence; **no new Maven binaries** | Patient Java 26/Leyden: **8.38 MiB lower median stopped-process PSS**, conditional lower saving bound **8.19 MiB** | Original calibration failed and used an approved post-observation exception; performance bounds unqualified or failed |

Patient startup/cache warmth, production parity and uploaded-binary parity remain
unqualified. Its conditional bound assumes independent, exchangeable, stable
pairs. It is not a clean performance non-regression or whole-stack RAM claim.

## Which version to install

Install plugin/runtime **2.1.0** for the stable distribution. Do not substitute
`2.15` into Maven coordinates: that release publishes evidence, not a new binary
distribution. The Patient study used an admitted compatibility port; equality
with the uploaded 2.1 binaries is not established.

## Reading results

PetClinic compared exact R41F fat-JAR content/deployment with strict no-JMOA B0
exploded Boot under Java 17/no-CDS. Patient compared clean B0 with the composed
candidate under Java 26 and separate artifact-specific Leyden caches. The two
effect sizes describe different services, artifacts and runtime policies.

Older three-service V1/V2 matrices and CDS studies remain historical evidence.
Their original numbers and verdicts are preserved; they do not replace these
release records and must not be added to new paired medians.

## Authoritative records

- [Stable 2.1 release](https://github.com/AlphaSudo/jmoa/releases/tag/v2.1.0)
- [Provisional 2.15 release](https://github.com/AlphaSudo/jmoa/releases/tag/v2.15)
- [PetClinic result](../product-evidence/petclinic-r41f-b0-t7r23-result.md)
- [Patient benchmark and all costs](../product-evidence/patient-scoped-ram-benchmark.md)
- [2.1 technical paper](../paper/jmoa-v2.1-petclinic-memory-engineering.md)
- [2.15 companion paper](../paper/jmoa-v2.15-patient-leyden-memory-evidence.md)
