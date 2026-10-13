# Three-Artifact Runtime-Policy Audit

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

| Service | Decision | Launch mode | Policy | CDS | JDK | Proofs passed |
|---|---|---|---|---|---|---:|
| doctor-service | RUNTIME_POLICY_IDENTICAL | SPRING_BOOT_FAT_JAR | APPLICATION_CDS | ON | 26.0.1+8 | 18/18 |
| patient-service | RUNTIME_POLICY_IDENTICAL | SPRING_BOOT_FAT_JAR | JDK_BASE_CDS_LOW_DIRTY | ON | 26.0.1 | 18/18 |
| spring-petclinic-customers-service | RUNTIME_POLICY_IDENTICAL | EXPLODED_BOOT_APP | NO_CDS_LOW_DIRTY | OFF | 17.0.19+10 | 18/18 |
