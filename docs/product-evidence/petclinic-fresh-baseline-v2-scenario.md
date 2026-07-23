# PetClinic Fresh Baseline-To-V2 Scenario

Classification: **VALID_SINGLE_SCREEN_NOT_CONFIRMATION**

On 2026-07-19, the audited scenario runner executed one complete PetClinic
customers-service baseline-to-V2 lifecycle from source revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967`.

## Execution

- Built a strict no-JMOA B0 before target-service mutation.
- Started config server, discovery server, and customers service.
- Waited for health, warmed for 20 seconds, executed 81 recorded requests,
  settled for 5 seconds, and captured the full memory/JVM evidence set.
- Tore down B0 before applying JMOA.
- Built the current plugin/runtime, ran full-P2, ran the LVT/LVTT reducer,
  materialized exploded Boot V2, and repeated the identical runtime sequence.
- Recorded 444 external command entries with complete responses in one private
  Markdown ledger. There were zero hard failures and 22 allowed nonzero health
  retry or remove-if-present responses.

## Single-Screen Result

| Metric | B0 | V2 | Delta |
| --- | ---: | ---: | ---: |
| PSS | 353,485 KB | 349,870 KB | **-3,615 KB** |
| Private_Dirty | 344,284 KB | 340,988 KB | **-3,296 KB** |
| `memory.current` | 432,865,280 B | 428,793,856 B | **-4,071,424 B** |

This is a positive fresh screen, not a paired confirmation or generalized
performance claim.

## Provenance Boundary

The B0 build, both runtime images, runtime measurements, JMOA transformation,
reducer, and V2 materialization were executed fresh. The runtime profile,
observed-site admission set, safe-SAM allowlist, config-server image, and
discovery-server image were reused frozen inputs and are identified as such in
the raw ledger. The incomplete original profile-generation chain was not
invented or represented as fresh training.

The public [scenario contract](scenario-command-ledger-contract.md) defines the
ledger format. Raw ledgers are not committed because they contain local paths,
large logs, and private environment detail.
