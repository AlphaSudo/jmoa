# GitHub Actions Workflow

The public source repository now includes `.github/workflows/build.yml`.

The CI gate is intentionally narrow:

- run the publication safety scanner,
- build and test the public Maven modules,
- avoid Podman/container memory measurements in default CI.

```yaml
name: build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '26'
          cache: maven
      - name: Build public modules
        run: mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
```

The equivalent local validation remains:

```powershell
./scripts/build-all.ps1
./scripts/check-publication-safety.ps1
```
