# Phase V2-A0 Safety Setup

Status: implemented for inventory-only mode.

## Feature Flags

```text
jmoa.synthetic.enabled=false
jmoa.synthetic.inventoryOnly=true
jmoa.synthetic.optimizeFamily=none
jmoa.synthetic.failOnUnsafe=true
```

## Safety Decisions

- Synthetic/generated-class work is disabled by default.
- Inventory mode is explicit and read-only.
- `optimizeFamily` values other than `none` fail fast in this release.
- `inventoryOnly=false` fails fast in this release.
- The V1 lambda optimizer path remains the default behavior.

## Acceptance

```text
Existing v1 lambda optimization still builds.
Synthetic optimizer is off by default.
Inventory-only mode cannot change bytecode.
Unsafe optimization requests fail before scanning or rewriting.
```

