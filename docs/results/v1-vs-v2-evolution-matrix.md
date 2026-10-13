# Final V1 To Final V2 Engineering-Evolution Matrix

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

This matrix answers how V2 improved the accepted V1 optimizer. It does not
answer the primary adoption question of final JMOA versus no JMOA.

| Service | Policy | Valid runs | Wins | Median PSS | Private_Dirty | memory.current |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| PetClinic customers | `NO_CDS_LOW_DIRTY` | 6/6 | 2/3 | -6,012 KB | -5,708 KB | -8,081,408 B |
| Doctor | `APPLICATION_CDS` | 6/6 | 3/3 | -5,156 KB | -5,212 KB | -6,975,488 B |
| Patient | `JDK_BASE_CDS_LOW_DIRTY` | 6/6 | 3/3 | -8,279 KB | -8,444 KB | -8,523,776 B |

These medians are not added to historical baseline-to-V1 results. The direct
product-effect campaign has its own frozen artifacts, screens, confirmations,
V2-C validation, and V2-D attribution.
