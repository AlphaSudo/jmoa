# Doctor Comparator Command Ledger Index

Raw commands, stdout, stderr, HTTP responses, and failed attempts remain in the
private append-only scenario ledgers. They are not committed.

| Scenario | Status | Recorded evidence |
|---|---|---:|
| B0 first, V1 second | complete | 8 ledger groups, 236 command records |
| V1 first, B0 second, first attempt | failed and retained | not counted |
| V1 first, B0 second, corrected attempt | complete | 8 ledger groups, 235 command records |
| Existing-pair attribution | complete after failures | 5 attempts, 3 failed |
| Mechanism activation study | complete after failure | 3 attempts, 1 failed |
| Security rotation/migration | complete after failure | private |
| Closure and validation | complete after documentation retry | failure retained |

The first reversed attempt failed because the workload protocol still
referenced the pre-rotation environment source. The corrected attempt used a
fresh capture and ledger root. No failed attempt was overwritten or counted as
accepted evidence.

Redacted copies preserve the source evidence and transformation manifest:

- command-ledger tree: 332 files processed, 10 files changed, 18 redactions;
- capture tree: 45 files processed, 2 files changed, 2 redactions.

Public reports contain only sanitized summaries and hashes. The private ledgers
are the command-and-response authority.
