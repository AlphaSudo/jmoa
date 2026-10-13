# V2 Original Plan Coverage

**Historical final-V2 audit record.** Original measurements, statuses and
release-readiness notes below belong to that publication. For current stable
2.1 and provisional 2.15, see [release boundaries](../releases/current-state.md).

The original V2 plan was intentionally broader than the release-critical V2
product. This audit maps each major track to the current repository state.

| Track | Status | Evidence | Release Interpretation |
| --- | --- | --- | --- |
| Synthetic/generated optimizer | `DISCOVERY_ONLY` | `docs/decisions/generated-family-disposition.md` | Inventory, matched capture, ROI, and safety are complete; no mutation admitted. |
| Bytecode / 64KB optimizer | `PARTIAL` | `docs/bytecode-size-profiler.md`, `docs/reference/reducer-configuration.md`, `docs/reference/reducer-configuration.md` | Profiling and raw LVT/LVTT reducer shipped; large-method and constant-pool surgery deferred. |
| Measurement stability | `INFRASTRUCTURE_CONFIRMED` | `docs/evidence-engine.md` | Evidence engine and replay proof are delivered. |
| Memory attribution | `INFRASTRUCTURE_CONFIRMED` | `docs/methodology/memory-attribution.md` | smaps/NMT/heap/class attribution delivered; full JFR pipeline deferred. |
| Runtime policy layer | `INFRASTRUCTURE_CONFIRMED` | `docs/architecture/runtime-policy-model.md`, `docs/architecture/materialization-and-origin-proof.md`, `docs/reference/maven-plugin-goals.md` | Confirmed no-CDS and CDS protocols plus preflight/workflow guards. |
| Packaging/materialization | `INFRASTRUCTURE_CONFIRMED` | `docs/architecture/materialization-and-origin-proof.md`, `docs/results/v2-three-service-matrix.md`, `docs/results/v2-three-service-matrix.md` | Exploded Boot and fat-JAR materialization proof workflows delivered. |
| Candidate ROI / recommendation | `INFRASTRUCTURE_CONFIRMED` | `docs/reference/maven-plugin-goals.md`, `docs/classfile-footprint-roi.md`, `docs/generated-class-optimizer.md` | Reducer/runtime recommendation and discovery classifications delivered. |
| Benchmark suite | `PARTIAL` | `docs/reference/reducer-configuration.md`, `docs/results/v2-three-service-matrix.md`, `docs/results/v2-three-service-matrix.md` | Customers, Doctor, and visits evidence exists; broad benchmark matrix deferred. |
| Safety/proof system | `INFRASTRUCTURE_CONFIRMED` | `docs/reference/reducer-configuration.md`, `docs/reference/reducer-configuration.md`, `docs/architecture/materialization-and-origin-proof.md`, `docs/reference/maven-plugin-goals.md` | Hashes, byte audits, jar safety, materialization, preflight, and claim guards delivered. |
| Public/product polish | `INFRASTRUCTURE_CONFIRMED` | `examples`, `docs/reference/reducer-configuration.mdinal/v2-clean-clone-qualification.md` | Clean-clone public golden path, checksummed release bundle, and Java 17 semantic smoke passed. |

## Bottom Line

The release-critical V2 core is strong, but the broad research plan was not
implemented literally. The final release must claim the delivered product scope,
not every speculative optimizer in the original plan.
