# JMOA Maven Plugin

The Maven plugin is the build-time optimizer entry point.

Current responsibilities:

- scan class roots and expanded dependency jars for lambda sites
- apply profile and framework admission rules
- generate Tier 1 runtime plans and Tier 2 package/SAM adapters
- rewrite eligible `invokedynamic` call sites
- package optimized dependency jars
- validate adapter references
- write measurement and class-load evidence summaries
- write V2-A generated/synthetic/proxy/AOT inventory reports when explicitly
  enabled

The plugin is intentionally not a runtime javaagent. Optimized artifacts are
produced at build time and then verified in the target deployment shape.

## Important Goals

- `jmoa:optimize`
- `jmoa:coverage-report`
- `jmoa:check-coverage`
- `jmoa:measure-impact`

## V2-A Generated-Class Flags

Generated-class inventory is disabled by default.

```text
jmoa.synthetic.enabled=false
jmoa.synthetic.inventoryOnly=true
jmoa.synthetic.optimizeFamily=none
jmoa.synthetic.failOnUnsafe=true
```

When enabled, the plugin writes inventory, safety taxonomy, prototype selection,
and ROI V2 feature reports under `target/`. Runtime attribution reports are also
written when `jmoa.synthetic.classLoadLog` or `jmoa.synthetic.classHistogram`
points to existing evidence files.

Generated-class bytecode mutation remains disabled. Optimization requests still
fail fast unless the mode is inventory/report-only.

See the repository-level docs for deployment materialization and measurement
boundaries.
