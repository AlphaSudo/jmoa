# V2 Original Plan Coverage

The original V2 plan was intentionally broader than the release-critical V2
product. This audit maps each major track to the current repository state.

| Track | Status | Evidence | Release Interpretation |
| --- | --- | --- | --- |
| Synthetic/generated optimizer | `DISCOVERY_ONLY` | `docs/v2-w/v2w-final-verdict.md` | Inventory, matched capture, ROI, and safety are complete; no mutation admitted. |
| Bytecode / 64KB optimizer | `PARTIAL` | `docs/v2-b`, `docs/v2-i`, `docs/v2-j` | Profiling and raw LVT/LVTT reducer shipped; large-method and constant-pool surgery deferred. |
| Measurement stability | `INFRASTRUCTURE_CONFIRMED` | `docs/v2-c/v2c-closure-report.md` | Evidence engine and replay proof are delivered. |
| Memory attribution | `INFRASTRUCTURE_CONFIRMED` | `docs/v2-d/v2d-closure-report.md` | smaps/NMT/heap/class attribution delivered; full JFR pipeline deferred. |
| Runtime policy layer | `INFRASTRUCTURE_CONFIRMED` | `docs/v2-n`, `docs/v2-o`, `docs/v2-p` | Confirmed no-CDS and CDS protocols plus preflight/workflow guards. |
| Packaging/materialization | `INFRASTRUCTURE_CONFIRMED` | `docs/v2-o`, `docs/v2-k`, `docs/v2-l` | Exploded Boot and fat-JAR materialization proof workflows delivered. |
| Candidate ROI / recommendation | `INFRASTRUCTURE_CONFIRMED` | `docs/v2-m`, `docs/v2-r`, `docs/v2-s` | Reducer/runtime recommendation and discovery classifications delivered. |
| Benchmark suite | `PARTIAL` | `docs/v2-i`, `docs/v2-k`, `docs/v2-l` | Customers, Doctor, and visits evidence exists; broad benchmark matrix deferred. |
| Safety/proof system | `INFRASTRUCTURE_CONFIRMED` | `docs/v2-f`, `docs/v2-j`, `docs/v2-o`, `docs/v2-p` | Hashes, byte audits, jar safety, materialization, preflight, and claim guards delivered. |
| Public/product polish | `INFRASTRUCTURE_CONFIRMED` | `examples`, `docs/v2-final/v2-clean-clone-qualification.md` | Clean-clone public golden path, checksummed release bundle, and Java 17 semantic smoke passed. |

## Bottom Line

The release-critical V2 core is strong, but the broad research plan was not
implemented literally. The final release must claim the delivered product scope,
not every speculative optimizer in the original plan.
