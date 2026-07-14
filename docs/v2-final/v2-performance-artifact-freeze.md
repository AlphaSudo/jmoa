# V2 Performance Artifact Freeze

The final customers-service acceptance uses source revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967` and three byte-frozen products:

| Product | Definition | Artifact SHA-256 | Dependency bytes |
|---|---|---|---:|
| B0 | unoptimized public baseline | `4952EF9306C732846BFAE0FAE6A67BE2F9B8509644B3396B8247215A03E5589D` | 92,299,443 |
| V1 | finalized full-P2 optimization | `314761904021A75EF1BD114B28BBE15FCAFE31C9F21C71577ABF47CECA33A92C` | 92,466,274 |
| V2 | V1 plus productized raw dependency LVT/LVTT reduction | `007F1796B83FCC2217A57A6975EF5CAFD7494A5EF050A7F5261C088B63C6CC2F` | 88,798,165 |

All three products have the same application fingerprint and Boot-loader
fingerprint. V2 differs from V1 only in the audited dependency layer and removes
3,668,109 dependency-JAR bytes.

The protocol is `EXPLODED_BOOT_APP`, `NO_CDS_LOW_DIRTY`, Java 17,
`MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden/javaagent, 20 seconds warmup, the
corrected 81-request workload, and 5 seconds post-workload settle. The final
protocol drops the Podman VM page cache before every variant and alternates pair
order `B-C`, `C-B`, `B-C`.

Status: `B0_ARTIFACT_FROZEN`, `V1_ARTIFACT_FROZEN`, `V2_ARTIFACT_FROZEN`,
`PROTOCOL_FROZEN`.
