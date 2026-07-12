# V2-V Stage Attribution

`run-generated-lifecycle-attribution.ps1` converts each raw lifecycle stage
into `generated-class-runtime-attribution.json` through the standalone Maven
goal `jmoa:analyze-generated-runtime`.

The wrapper requires `class-load.log` and `class-histogram.txt` for all three
stages. It records a stage-level failure when either input is absent and can
fail the campaign with `-FailOnStageError`.

The resulting attribution remains diagnostic. It is not substituted for V2-C
memory-pair evidence and does not authorize a generated-class transform.
