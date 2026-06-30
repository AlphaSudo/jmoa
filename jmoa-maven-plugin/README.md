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

The plugin is intentionally not a runtime javaagent. Optimized artifacts are
produced at build time and then verified in the target deployment shape.

## Important Goals

- `jmoa:optimize`
- `jmoa:coverage-report`
- `jmoa:check-coverage`
- `jmoa:measure-impact`

See the repository-level docs for deployment materialization and measurement
boundaries.
