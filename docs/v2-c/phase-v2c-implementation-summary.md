# V2-C Implementation Summary

Status: implemented report-only evidence engine.

Implemented:

- evidence schema,
- smaps_rollup parser,
- full smaps category parser,
- memory.current parser,
- workload JSON parser,
- class histogram parser,
- heap info parser,
- NMT summary parser,
- evidence validator,
- perturbation detector,
- paired confirmation analyzer,
- variance classifier,
- JSON and Markdown reports,
- Maven `jmoa:evidence` goal,
- historical replay mode using a replay-suite JSON contract.

Still future work:

- HTML dashboard,
- JFR event correlation,
- NMT detail parser,
- standalone `jmoa-evidence-engine` module extraction,
- stricter runtime-origin and artifact materialization integration as source
  schemas stabilize.

Replay support:

- `jmoa.evidence.mode=replay`
- `jmoa.evidence.replaySuite=<suite.json>`
- output `jmoa-evidence-replay-report.md/json`

The repo includes `historical-replay-suite.example.json` as the public contract.
Private/raw archived phase folders are intentionally not committed.

V2-A and V2-B mutation remain blocked until V2-C evidence gates can validate
their reducer experiments.
