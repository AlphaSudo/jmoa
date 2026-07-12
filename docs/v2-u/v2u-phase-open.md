# V2-U Matched Generated-Family Evidence Campaign

Status: open as a fresh diagnostic evidence campaign.

V2-U does not reopen the V2-T result. V2-T closed as `PARTIAL_INFRASTRUCTURE`
because the recovered historical captures did not satisfy the matched
artifact/capture contract. V2-U exists to collect fresh startup, warmup, and
workload generated-family diagnostics with a complete identity tuple.

## Scope

V2-U collects and reconciles diagnostic generated-family evidence for:

```text
customers-service
visits-service
Doctor D2R
```

Required identity fields:

```text
artifactSha256
service
launchMode
runtimePolicy
reducerEngine
familyRegistryVersion
scannerVersion
```

Required lifecycle stages:

```text
startup
warmup
workload
```

## Boundaries

- V2-U is diagnostic/report-only.
- V2-U does not mutate generated classes.
- V2-U does not add a runtime memory claim.
- V2-U diagnostic class-load logging is separate from V2-C memory pairs.
- V2-U may admit at most one generated-family prototype, and only if every
  evidence, safety, semantic, rollback, and confirmation gate is satisfied.

## Closure Target

If customers, visits, and Doctor D2R all receive complete matched bundles, V2-U
may close as `CLOSED_CONFIRMED_INFRASTRUCTURE`.

If one or more target bundles remain unavailable, V2-U closes as
`PARTIAL_INFRASTRUCTURE`.
