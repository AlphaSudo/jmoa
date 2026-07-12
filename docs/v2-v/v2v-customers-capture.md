# V2-V Customers Capture

Customers is the first target because V2-U already exposed a useful but
unmatched lambda signal: eight runtime-only lambda implementations and
813,664 histogram bytes.

That historical signal is a lead, not a matched result. V2-V must reproduce it
with one exact artifact and complete startup, warmup, and workload captures.
The campaign must also record Spring Data load timing and class-loader origin.

Current repository state: no fresh complete customers bundle is committed or
claimed. Run the capture tools with the exact artifact identity and validate the
result through `jmoa:analyze-generated-evidence`.
