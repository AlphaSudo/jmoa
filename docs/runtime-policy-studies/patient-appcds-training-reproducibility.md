# Patient AppCDS Training Reproducibility

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

All eight A/B archives trained successfully: V1/V2 x STARTUP/REPRESENTATIVE x A/B.

| Artifact/profile | Archived classes A/B | Archive bytes A/B | Class Jaccard | Status |
| --- | ---: | ---: | ---: | --- |
| V1 STARTUP | 16,530 / 16,530 | 118,394,880 / 118,509,568 | 1 | stable |
| V1 REPRESENTATIVE | 16,565 / 16,565 | 118,550,528 / 118,648,832 | 1 | stable |
| V2 STARTUP | 16,530 / 16,530 | 115,707,904 / 115,707,904 | not screened | trained |
| V2 REPRESENTATIVE | 16,564 / 16,564 | 115,896,320 / 115,908,608 | not screened | trained |

The class counts are actual dynamic archive entries. Training load events were retained separately and were not substituted for archive membership.
