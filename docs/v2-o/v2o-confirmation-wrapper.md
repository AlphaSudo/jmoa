# V2-O Confirmation Wrapper

`scripts/run-v2-confirmation.ps1` requires passed semantic-smoke and
materialization-proof reports for both variants. It then gathers three or more
pairs with `runtime-screen-pair.ps1`, invokes V2-C, and invokes V2-D only after
V2-C succeeds.

The wrapper writes command logs and a wrapper summary, but it does not promote
a result to a public claim. Read `jmoa-paired-confirmation.json` and
`jmoa-memory-attribution.json` before recording a claim.

The wrapper rejects fewer than three pairs and stops if a pair fails, V2-C
rejects evidence, or V2-D cannot attribute a V2-C-valid result.
