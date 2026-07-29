# 13 Read Command Ledgers

Every qualification and final scenario has one top-level ledger:

```text
sessions/<scenario>/scenario-command-ledger.md
```

That ledger is the chronological record of the run. It must contain or link:

```text
every external command and exact argv
working directory
selected environment
start/end timestamps and duration
exit code
stdout and stderr
artifact/image/CDS hashes
support and target launch
health/warmup/workload
claim captures and diagnostics
teardown
child-ledger integrity
```

Raw output may live in child ledgers, but the scenario ledger must name the
source ledger and preserve its hash. A summary without the commands and logs is
not a command ledger.

## Reading Order

1. Confirm the launch artifact, image, JDK, and policy.
2. Confirm support and target health.
3. Verify workload requests/status/errors and state proof.
4. Compare workload completion with the first claim snapshot.
5. Verify claim captures precede perturbing diagnostics.
6. Confirm teardown and environment validity.
7. Check the scenario-ledger hash against the campaign seal.

The campaign seal hashes all scenario ledgers and raw evidence. Raw ledgers,
private paths, credentials, service source, database state, and runtime images
remain outside Git; public reports contain only sanitized aggregates.
