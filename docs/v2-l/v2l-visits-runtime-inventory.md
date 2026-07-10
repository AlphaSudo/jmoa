# V2-L Visits Runtime Inventory

Status:

```text
READY_AND_EXECUTED
```

The selected target is the public Spring PetClinic visits-service at revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967`.

Inventory result:

| Item | Result |
| --- | --- |
| Source | Public Spring PetClinic microservices repository |
| Module | `spring-petclinic-visits-service` |
| Application version | `4.0.1` |
| Build | Maven wrapper package succeeded |
| Database | Embedded HSQLDB under the default profile |
| Support services | Not required for this isolated protocol |
| Health | `/actuator/health` |
| Launch mode | `EXPLODED_BOOT_APP` via `JarLauncher` |
| Container Java | Java 17 |
| Runtime policy | `NO_CDS_LOW_DIRTY` |

Cloud configuration, discovery, Eureka registration, and tracing exporters were
disabled explicitly so the public service could run as a self-contained target.
Both variants used the same settings.

Representative workload, repeated for three rounds:

```text
GET  /actuator/health
GET  /owners/1/pets/1/visits
GET  /owners/2/pets/2/visits
GET  /pets/visits?petId=1&petId=2&petId=3
POST /owners/1/pets/1/visits
GET  /owners/1/pets/1/visits
```

This produces 18 requests per run. `/actuator/info` is not exposed in this
standalone profile and was deliberately excluded rather than counted as a
service failure.

No visits-specific full-P2 artifact was found. The honest V2-L comparison is
therefore baseline vs baseline plus raw reducer.
