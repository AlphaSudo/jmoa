# V2-L Visits Runtime Screen

Status:

```text
PASSED_AND_SUPERSEDED_BY_CONFIRMATION
```

The single-screen comparison used the same public visits baseline and
raw-reduced candidate later used for confirmation.

Result:

| Metric | Baseline | Candidate | Delta |
| --- | ---: | ---: | ---: |
| PSS | `329,516 KB` | `310,132 KB` | `-19,384 KB` |
| Private_Dirty | `307,016 KB` | `287,836 KB` | `-19,180 KB` |
| memory.current | `316,874,752 bytes` | `297,132,032 bytes` | `-19,742,720 bytes` |
| Workload errors | `0` | `0` | `0` |
| Linkage errors | `0` | `0` | `0` |

The screen passed all promotion gates. Its large delta is not a public result
and is superseded by the three-pair medians.

An earlier preflight attempt encountered a port already owned by the preceding
semantic-smoke container. Container startup failed before capture, so no metrics
from that attempt were admitted. The runner was hardened to fail immediately on
container-start failure, the stale container was removed, and the valid screen
was rerun from fresh containers.

Observed startup timing is supporting context only. V2-L makes no startup claim.
