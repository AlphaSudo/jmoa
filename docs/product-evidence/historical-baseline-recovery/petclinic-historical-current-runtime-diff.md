# spring-petclinic-customers-service: Historical/Current Runtime Diff

| Dimension | Historical | Current | Classification | Reason |
|---|---|---|---|---|
| B0 artifact SHA | 4952EF9306C732846BFAE0FAE6A67BE2F9B8509644B3396B8247215A03E5589D | 2F4A63A803B2A05050ACF020AD382ABE589C52EE50253A1F5DC85F6D62584ACF | `DISQUALIFYING` | Normalized JAR content differs. |
| V1 artifact SHA | 314761904021A75EF1BD114B28BBE15FCAFE31C9F21C71577ABF47CECA33A92C | 314761904021A75EF1BD114B28BBE15FCAFE31C9F21C71577ABF47CECA33A92C | `EXPECTED` | Exact artifact recovered. |
| Launch mode | EXPLODED_BOOT_APP | EXPLODED_BOOT_APP | `EXPECTED` | Same deployment shape. |
| CDS policy | NO_CDS_LOW_DIRTY | NO_CDS_LOW_DIRTY | `EXPECTED` | Same high-level policy. |
| Capture design | three paired runs | six balanced orders | `EXPECTED` | Current design controls order more strongly. |
| Five JDK identities | INCOMPLETE_HISTORICAL_RECORD | CURRENT_RUNTIME_FINGERPRINTED | `UNKNOWN` | The historical five-way split was not frozen. |

**Conclusion:** `DISQUALIFYING_DIFFERENCES_PRESENT`
