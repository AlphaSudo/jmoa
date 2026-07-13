# V2 Schema Freeze

Status: incomplete; P1 before `v2.0.0-rc1`.

The following schemas/report contracts are in scope for V2 freeze:

| Schema / Report | Current Source | Freeze Status |
| --- | --- | --- |
| Reducer report | `jmoa-reducer-report.json` | `NEEDS_VERSION_FREEZE` |
| Reducer manifest v2 | `jmoa-reducer-manifest-v2.json` | `NEEDS_VERSION_FREEZE` |
| Raw byte-preservation audit | `raw-reducer-byte-preservation-report.json` | `NEEDS_VERSION_FREEZE` |
| V2-C run/evidence manifest | `docs/v2-c/jmoa-evidence-schema.json` | `PARTIAL` |
| Paired confirmation | V2-C report | `NEEDS_FIXTURE_FREEZE` |
| V2-D attribution | `jmoa-memory-attribution.json` | `NEEDS_VERSION_FREEZE` |
| Reducer recommendation | `jmoa-reducer-recommendation.json` | `NEEDS_VERSION_FREEZE` |
| Runtime recommendation | `jmoa-runtime-recommendation.json` | `NEEDS_VERSION_FREEZE` |
| Runtime preflight | `jmoa-runtime-preflight.json` | `NEEDS_VERSION_FREEZE` |
| Workflow report | `jmoa-runtime-workflow-report.json` | `NEEDS_VERSION_FREEZE` |
| Generated inventory | `generated-class-inventory.json` | `NEEDS_VERSION_FREEZE` |
| Generated lifecycle manifest | `generated-lifecycle-manifest.json` | `NEEDS_VERSION_FREEZE` |
| Generated evidence reconciliation | V2-U/V2-T reports | `NEEDS_VERSION_FREEZE` |
| Prototype admission | V2-W admission report | `NEEDS_VERSION_FREEZE` |

Exit gate for RC:

```text
schema names and metadataVersion values documented
required/optional fields documented
historical fixture parse tests pass
unsupported future schema behavior documented
```

