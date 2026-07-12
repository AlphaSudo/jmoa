# V2-V Fresh Matched Generated-Family Capture Campaign

V2-U delivered the strict identity contract and diagnostic lifecycle harness,
but the available services still lacked complete matched bundles. V2-V is the
execution phase: it uses those tools to collect fresh startup, warmup, and
workload generated-family evidence.

## Boundary

- Diagnostic capture only.
- No generated-family mutation.
- No new runtime-memory claim.
- Diagnostic sessions remain separate from V2-C memory pairs.
- Incomplete bundles remain explicit failures, not inferred matches.

The target closure is `CLOSED_CONFIRMED_INFRASTRUCTURE` only when customers,
visits, and Doctor each return `MATCHED_DIAGNOSTIC_EVIDENCE`. Otherwise the
campaign remains `PARTIAL_INFRASTRUCTURE`.

## Execution tools

```powershell
./scripts/capture-generated-lifecycle.ps1
./scripts/run-generated-lifecycle-attribution.ps1
./scripts/validate-generated-capture-bundles.ps1
```

The campaign validator produces bundle validation, lifecycle, cross-service,
ROI, admission, and final-verdict reports. It never admits more than one
candidate and currently defaults to no admission.
