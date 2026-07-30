# petclinic historical Protocol Inventory

This is an inventory of recovered scripts and artifacts. It is not an executed command ledger and does not contain command responses.

Classification: **HISTORICAL_COMMANDS_COMPLETE, HISTORICAL_ARTIFACT_COMPLETE, HISTORICAL_RUNTIME_NOT_REPRODUCIBLE**

Phase 33M preserves the historical exploded-Boot, no-CDS commands and
artifacts, but its artifact named `baseline.jar` is not a valid no-JMOA B0. It
contains JMOA output and semantic application drift. The comparator is
therefore `HISTORICAL_PETCLINIC_B0_INVALID`; the artifact name is retained only
as historical provenance.

## Scripts

| Logical path | Present | SHA-256 |
|---|---:|---|
| historical/petclinic/run-33l7-exploded-boot-materialization.ps1 | True | BD80B5DE195B2BC2E754721F9625C06B6C3496F764AD0A6A344A233AEC6099D8 |
| historical/petclinic/run-33m-integrated-nocds-confirmation.ps1 | True | 3F7193F09E80B1D65ECDF36778298AFBD6D111C85EB51C8649715CE1194E9BBD |

## Artifacts

| Logical path | Present | SHA-256 |
|---|---:|---|
| artifact/petclinic/baseline.jar | True | 4952EF9306C732846BFAE0FAE6A67BE2F9B8509644B3396B8247215A03E5589D |
| artifact/petclinic/optimized.jar | True | 314761904021A75EF1BD114B28BBE15FCAFE31C9F21C71577ABF47CECA33A92C |
