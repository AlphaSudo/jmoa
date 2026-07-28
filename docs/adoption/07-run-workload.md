# 07 Run Workload

The workload is part of the frozen protocol.

For PetClinic customers, the public workload runs 27 requests over three rounds, for 81 requests total. It records:

- status and response body;
- canonical JSON hash for comparable responses;
- initial and final data-state hashes;
- expected mutation assertions;
- zero-error summary.

The order for every observation is:

```text
launch full support stack
wait for support health
launch one target
wait for target health
20-second warmup
frozen workload
5-second settle
claim-first capture
diagnostic capture
full teardown
```

An HTTP or semantic failure invalidates the observation. Preserve it and rerun that logical slot in a separately ledgered retry directory.
