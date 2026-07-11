# V2-N Historical Runtime Policy Replay

The replay suite validates the policy engine against the accepted V2 runtime
history. It is a normalized decision regression suite, not a substitute for
the private raw run archives used by V2-C.

```text
cases: 7
passed: 7
failed: 0
```

| Case | Expected policy decision |
| --- | --- |
| V2-I customers raw no-CDS | `RECOMMEND_CONFIRMED_POLICY` / public |
| V2-L visits raw no-CDS | `RECOMMEND_CONFIRMED_POLICY` / public |
| V2-K Doctor D2R raw CDS | `RECOMMEND_CONFIRMED_POLICY` / private |
| D2R with old D2 CDS archive | `BLOCK_CDS_ARCHIVE_MISMATCH` |
| Doctor no-CDS versus CDS baseline | `BLOCK_POLICY_MISMATCH` |
| V2-H hardened ASM screen | `BLOCK_RUNTIME_PROMOTION` |
| no-CDS preflight without fingerprint | `RECOMMEND_SCREEN_REQUIRED` |
