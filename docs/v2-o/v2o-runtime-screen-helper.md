# V2-O Runtime Screen Helper

`scripts/runtime-screen-pair.ps1` runs one fresh baseline/candidate pair under
one declared runtime policy. It captures the raw Linux/JVM artifacts V2-C
parses: `smaps_rollup`, full `smaps`, `memory.current`, NMT summary, heap info,
class histogram, workload JSON, and a run manifest.

Use it only after both variants have passed materialization proof and semantic
smoke. The script does not enable class-load logging, JFR, forced GC, or other
diagnostic perturbations during a clean memory pair.

One pair is a screen. It cannot establish a runtime claim. Use
`run-v2-confirmation.ps1` to collect three or more clean pairs and delegate the
verdict to V2-C.
