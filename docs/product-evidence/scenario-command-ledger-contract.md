# Scenario Command Ledger Contract

A JMOA scenario command ledger is one Markdown file containing the complete
executed baseline-to-V2 lifecycle. A script inventory, command list, or summary
report is not a command ledger.

## Required Runtime Order

1. Declare every reused and newly generated input with provenance and hash.
2. Build the strict no-JMOA baseline from the frozen source revision.
3. Start required auxiliary services and record their launch responses.
4. Start the baseline service and wait for health.
5. Warm the service, execute the frozen workload, and settle.
6. Capture PSS, Private_Dirty, `memory.current`, full `smaps`, NMT, heap,
   histogram, Metaspace, class-loader, container, and service-log evidence.
7. Tear down the baseline stack.
8. Run the JMOA transformation and reducer, recording every command response.
9. Materialize and hash the V2 deployment artifact.
10. Repeat the same auxiliary-service, launch, warmup, workload, settle,
    capture, and teardown sequence for V2.
11. Calculate baseline-to-V2 deltas without deleting nonzero command records.

## Per-Command Record

Every external command records:

- sequence number and engineering step;
- exact executable and argument vector;
- working directory and environment overrides;
- start/end time and duration;
- exit code;
- whether a nonzero exit was explicitly allowed;
- complete stdout and stderr.

Expected health-probe misses and remove-if-present operations remain visible as
allowed nonzero commands. They are not counted as hard failures. Any unexpected
nonzero exit stops the scenario and leaves the partial ledger intact.

## Input Provenance

Each input is classified as one of:

- `BUILT_IN_SCENARIO`
- `GENERATED_IN_SCENARIO`
- `REUSED_FROZEN_INPUT`
- `REUSED_SUPPORT_IMAGE`

Reused profile, admission, allowlist, support-image, or archive inputs must be
named as reused. A scenario must not describe them as freshly trained.

## PetClinic Runner

[`run-petclinic-audited-baseline-v2-scenario.ps1`](../../scripts/run-petclinic-audited-baseline-v2-scenario.ps1)
implements this contract. Machine-specific source, configuration, Maven, and
JDK paths are mandatory parameters. Raw ledgers are written outside the public
repository under the caller-selected output root because they contain local
paths, full logs, and large evidence.

The files under [`protocol-inventories`](protocol-inventories/) only index
recovered scripts and artifacts. They are deliberately not called command
ledgers.

## Balanced Campaign Ledgers

[`run-petclinic-performance-campaign.ps1`](../../scripts/run-petclinic-performance-campaign.ps1)
uses a two-level ledger without weakening the one-ledger-per-arm requirement:

- each measured B0 or V2 arm emits one chronological `bN-command-ledger.md` or
  `cN-command-ledger.md`;
- that Markdown includes every launch, health, workload, capture, log, and
  teardown command in execution order, with stdout, stderr, or HTTP response
  body inline;
- launch, workload, capture, and teardown also retain stage-specific NDJSON and
  SHA-256-addressed raw files;
- `child-ledger-index.json` verifies every stage ledger, raw file, and
  consolidated arm ledger and is itself hashed by the parent campaign ledger.

The long campaign does not rebuild artifacts. It consumes a signed manifest
that pins source revision, B0/V2 artifact hashes, all four image identities,
materialization manifest, artifact lineage, config revision/content hash, JDK,
Maven, runtime policy, and plugin coordinates. The runtime mounts a
campaign-owned frozen config snapshot, not the mutable checkout.
