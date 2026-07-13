# V2 Phase Closure Taxonomy

This taxonomy prevents report-only, artifact-only, failed-screen, and
runtime-confirmed milestones from being described as the same kind of closure.

## Closure Types

| Closure type | Meaning | Allowed claim |
| --- | --- | --- |
| `CLOSED_INFRASTRUCTURE` | Report-only or visibility infrastructure is implemented and validated. | Tooling/analysis capability only |
| `PARTIAL_INFRASTRUCTURE` | The report-only implementation is validated, but required real evidence coverage remains incomplete. | Tooling capability and explicit evidence gap only |
| `CLOSED_CONFIRMED` | Runtime behavior is confirmed by V2-C evidence gates and explained by V2-D attribution where relevant. | Narrow runtime claim within the measured protocol |
| `CLOSED_CONFIRMED_INFRASTRUCTURE` | Infrastructure is implemented and proven against replay/historical evidence. | Evidence/attribution capability only |
| `CLOSED_ARTIFACT_ONLY` | Artifact mutation/materialization passed artifact gates but runtime behavior was not confirmed. | Artifact footprint or materialization only |
| `SCREEN_FAILED` | A single-screen runtime gate ran and blocked confirmation. | Negative or blocked screen result only |
| `CONFIRMATION_FAILED` | A screen allowed confirmation, but paired confirmation blocked runtime promotion. | Negative or blocked confirmation result only |
| `BLOCKED` | Work cannot proceed because required artifacts, runtime stack, credentials, or policy decisions are unavailable. | No claim beyond blocked status |
| `OPEN_BACKLOG` | Planned work exists but is not implemented. | No implementation claim |

## Current V2 Closure Map

| Milestone | Closure type | Claim boundary |
| --- | --- | --- |
| V2-A | `CLOSED_INFRASTRUCTURE` | Generated/synthetic/proxy/AOT visibility and safety reports; no generated-class mutation |
| V2-B | `CLOSED_INFRASTRUCTURE` | Bytecode/classfile footprint reporting; no broad bytecode surgery |
| V2-C | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Evidence truth engine replay-proven; not an optimizer |
| V2-D | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Memory attribution replay-proven; not an optimizer |
| V2-E | `CLOSED_CONFIRMED` | PetClinic runtime-confirmed only for the earlier V2-E reducer protocol |
| V2-F | `CLOSED_ARTIFACT_ONLY` | Productized hardened artifact reducer; no runtime win |
| V2-G | `CLOSED_ARTIFACT_ONLY` | Doctor corrected D2 artifact generalization; runtime not claimed in V2-G |
| V2-H | `SCREEN_FAILED` | Hardened PetClinic screen failed promotion; no confirmation |
| V2-I | `CLOSED_CONFIRMED` | PetClinic runtime-confirmed for explicit `jmoa.reducer.engine=raw` protocol |
| V2-J | `CLOSED_ARTIFACT_ONLY` | Raw engine productized and Doctor artifact-smoked; no new runtime claim |
| V2-K | `CLOSED_CONFIRMED` | Private Doctor corrected D2 vs raw-reduced D2R runtime confirmation under fat-JAR/CDS |
| V2-L | `CLOSED_CONFIRMED` | Public visits-service baseline vs raw-reduced baseline confirmation under exploded-Boot/no-CDS |
| V2-M | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Report-only reducer admission/recommendation engine proven by 11/11 historical replay; no mutation or new runtime claim |
| V2-N | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Report-only runtime-policy recommendation engine proven by historical replay; no deployment change, CDS archive reuse, or new runtime claim |
| V2-O | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Runtime preflight and capture automation; no policy selection or new runtime claim |
| V2-P | `CLOSED_CONFIRMED_INFRASTRUCTURE` | End-to-end workflow state machine and claim-register guard; no new reducer or claim |
| V2-Q | `CONFIRMATION_FAILED` | Ordinary packaged application classes admitted safely for raw LVT/LVTT reduction; public visits confirmation blocked runtime promotion |
| V2-R | `CLOSED_INFRASTRUCTURE` | Application/generated ROI discovery and recommendation classifications only; no mutation or runtime claim |
| V2-S | `CLOSED_INFRASTRUCTURE` | Generated-family runtime relevance/safety/ROI discovery only; no family prototype admitted, mutation, or runtime claim |
| V2-T | `PARTIAL_INFRASTRUCTURE` | SHA-gated static/runtime reconciliation and lifecycle evidence model; no complete matched service capture or family admission |
| V2-U | `PARTIAL_INFRASTRUCTURE` | Generated lifecycle capture harness and full identity tuple enforcement; current services still lack complete matched bundles, so no prototype admission or runtime claim |
| V2-V | `PARTIAL_INFRASTRUCTURE` | Fresh matched-capture execution tooling, preflight, automatic stage attribution, bundle validation, lifecycle/ROI/admission reports; no complete fresh bundle or family admission yet |
| V2-W | `CLOSED_INFRASTRUCTURE` | Exact Customers, Visits, and Doctor D2R generated-family matched capture completed as diagnostic discovery only; no mutation, family admission, or runtime claim |

V2-L also uses the descriptive phase label
`CLOSED_CONFIRMED_PUBLIC_SECOND_RUNTIME`. The official closure type remains
`CLOSED_CONFIRMED` so automation consumes the stable taxonomy above.

## Rule

Do not upgrade a closure type by wording alone.

```text
artifact-only does not become runtime-confirmed
screen-passed does not become confirmed
screen-failed does not become regression-confirmed unless V2-C supports it
infrastructure-closed does not mean optimizer-complete
blocked does not become skipped-success
```

Any new runtime performance claim requires fresh V2-C validation and V2-D
attribution within the exact runtime policy and launch mode being claimed.
