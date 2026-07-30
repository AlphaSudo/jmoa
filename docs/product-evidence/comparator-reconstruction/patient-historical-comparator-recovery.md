# Patient Historical Comparator Recovery

- Decision: **PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE**
- JAR candidates inspected: **7**
- Local Patient/Phase 31 image candidates: **0**
- Unverified deployable Patient candidates: **1**
- Historical B0 artifact recovered: **False**
- Historical B0 source revision recovered: **False**

The search found later/current Patient artifacts, optimizer staging JARs, and an incompatible
Spring Boot 3.1 / JDK 17 service artifact. None establishes the exact historical Phase 31 B0
identity. The historical runtime scripts, workload, capture timing, absolute vectors, and a stock
CDS archive survive, but the B0 artifact/source and support/config identity do not.

No Patient performance run is authorized. The current corrected B0-to-V2 result remains
authoritative: **-1,266.5 KB median PSS, 4/6 wins, confidence interval crossing zero**.
