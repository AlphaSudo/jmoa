# patient-service: Historical/Current Runtime Diff

| Dimension | Historical | Current | Classification | Reason |
|---|---|---|---|---|
| B0 artifact SHA | NOT_RECOVERED | 2BE7E1D761ECE8BDC74CE44E7B82FB0B79D7508E45B22E9864577F4D643E0352 | `UNKNOWN` | Raw runs exist but the historical B0 JAR was not recovered. |
| V1 artifact SHA | 3150AB868898359710DE317A38A0156685E30883D8A3585BA7EAE7CC698A331C | 17FDD9B3170E3917A14B3DD2C04551AF13547D1362216AD399E8D5958616E188 | `POTENTIALLY_MEMORY_RELEVANT` | The nearest retained later candidate is not the current V1 or proven Phase 31D artifact. |
| CDS policy | PER_CANDIDATE_APPLICATION_CDS | JDK_BASE_CDS_LOW_DIRTY | `DISQUALIFYING` | CDS policy changes absolute and incremental memory. |
| Workload | 300 operation pairs / 600 requests | frozen health-oriented workload | `DISQUALIFYING` | Mechanism activation and retained state differ. |
| Capture design | three paired runs | six balanced orders | `EXPECTED` | Current design controls order more strongly. |
| Five JDK identities | INCOMPLETE_HISTORICAL_RECORD | CURRENT_RUNTIME_FINGERPRINTED | `UNKNOWN` | The historical five-way split was not frozen. |

**Conclusion:** `DISQUALIFYING_DIFFERENCES_PRESENT`
