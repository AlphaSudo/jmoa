# V2 Capability Matrix

| Capability | Final disposition | Evidence |
| --- | --- | --- |
| final customers B0 -> V2 product | `SHIPPED` | [confirmation](v2-final-baseline-vs-v2-confirmation.md) |
| final customers V1 -> V2 increment | `SHIPPED` | [confirmation](v2-final-v1-vs-v2-confirmation.md) |
| raw dependency LVT/LVTT reducer and byte auditor | `SHIPPED` | V2-I/V2-J |
| public visits raw reducer | `SHIPPED` | V2-L |
| evidence, attribution, recommendation, and workflow engines | `SHIPPED` | V2-C/D/M/N/P |
| generated-family analysis and matched evidence | `VALIDATED_BUT_NOT_SHIPPED` | V2-W |
| private Doctor runtime confirmation | `VALIDATED_BUT_NOT_SHIPPED` | V2-K |
| hardened ASM runtime promotion | `INVESTIGATED_AND_REJECTED` | V2-H |
| application-class reducer promotion | `INVESTIGATED_AND_REJECTED` | V2-Q |
| broad generated/proxy and bytecode surgery | `DEFERRED_TO_V3` | [disposition](v2-unfinished-work-disposition.md) |

These are terminal V2 release dispositions. `VALIDATED_BUT_NOT_SHIPPED` means
analysis/evidence is retained without enabling a public mutation feature.
