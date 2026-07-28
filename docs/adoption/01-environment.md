# 01 Environment

## Required tools

- Windows PowerShell 7 for the supplied campaign scripts.
- Maven 3.9.x.
- The service's accepted JDK build.
- Podman with a running Linux machine.
- At least 1 GiB free in the Podman VM before every target arm.

## Required runtime controls

Each service config freezes:

- launch mode;
- CDS policy;
- JVM flags;
- `MALLOC_ARENA_MAX`;
- warmup and settle durations;
- workload identity;
- support images and configuration tree;
- page-cache policy and Podman machine name.

Run the fixture suite before beginning a service:

```powershell
./scripts/run-campaign-fixtures.ps1
```

Do not begin if any fixture fails. Once qualification or a final block begins, do not edit any frozen implementation script. The runner hashes the implementation and fails closed if bytes change.

## Private boundary

Keep service secrets and machine paths in a private JSON config. Supply it with `-ConfigPath` or one of:

```text
JMOA_DOCTOR_CAMPAIGN_CONFIG
JMOA_PATIENT_CAMPAIGN_CONFIG
JMOA_PETCLINIC_CUSTOMERS_CAMPAIGN_CONFIG
```
