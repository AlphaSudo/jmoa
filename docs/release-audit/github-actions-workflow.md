# GitHub Actions validation

The authoritative workflow is [build.yml](../../.github/workflows/build.yml).
It runs on main pushes and pull requests using Temurin Java 26 on Ubuntu.

The gates run publication safety, documentation quality (local links, JSON and
claim consistency), then Maven tests for `jmoa-runtime-lib` and
`jmoa-maven-plugin`. CI does not start private service stacks or reproduce RAM
campaigns. A passing build cannot qualify the provisional 2.15 cost or lineage gaps.

Equivalent documentation checks:

```powershell
pwsh ./scripts/check-publication-safety.ps1
pwsh ./scripts/check-documentation-quality.ps1 -SkipPublicationSafety
```

Stable binaries remain version 2.1.0. Provisional 2.15 publishes evidence only.
See [current release audit](README.md).
