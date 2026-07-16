# Patient CDS Transfer Decision

Decision: `V1_CDS_NOT_REPRODUCIBLE` under the current fixed-artifact protocol.

The historical optimized Patient candidate did win while using a matching CDS archive, but that experiment did not isolate CDS. The new fixed-artifact screen does: current V1 regressed by `+20,797 KB PSS` and `+120,639,488 bytes memory.current` under CDS. Current V2 also regressed, more strongly.

The study therefore stops before NMT-detail tuning, archive A/B selection, or a final V1-to-V2 CDS confirmation. Generic tooling for those diagnostics is implemented, but running them after the stop condition would not change the admission decision and risks selecting a favorable archive.

Patient remains confirmed under `NO_CDS_LOW_DIRTY`; Patient CDS remains blocked. Doctor remains confirmed under CDS. The service-specific V2 release matrix is unchanged.

